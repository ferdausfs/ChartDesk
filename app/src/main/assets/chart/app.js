/* ChartDesk chart core. Runs inside the app WebView; no network access by design. */
(function () {
  'use strict';

  var LWC = window.LightweightCharts;
  var lib = window.ChartDeskLib;

  if (!LWC) {
    document.getElementById('error-toast').textContent = 'Chart engine failed to load.';
    document.getElementById('error-toast').classList.add('show');
    return;
  }

  var els = {
    chart: document.getElementById('chart'),
    panes: document.getElementById('panes'),
    indicatorLegend: document.getElementById('indicator-legend'),
    skeleton: document.getElementById('skeleton'),
    toast: document.getElementById('error-toast'),
    sym: document.getElementById('legend-symbol'),
    tf: document.getElementById('legend-tf'),
    o: document.getElementById('legend-o'),
    h: document.getElementById('legend-h'),
    l: document.getElementById('legend-l'),
    c: document.getElementById('legend-c'),
    chg: document.getElementById('legend-chg'),
    vol: document.getElementById('legend-vol'),
  };

  var programmaticCrosshair = false;

  var state = {
    chart: null,
    mainSeries: null,
    volumeSeries: null,
    lastPriceLine: null,
    type: 'candle',
    symbol: '—',
    timeframe: '1m',
    precision: 2,
    raw: [],
    ready: false,
    theme: 'dark',
    cb: false,
    scaleMode: 'normal',
    watermark: 'ChartDesk',
    indicators: [],
    overlays: {},
    panes: {},
    syncing: false,
    options: { grid: true, crosshair: true, watermark: true },
    replay: { active: false, playing: false, index: 0, speed: 1, timer: null, position: null },
    pine: { code: null, series: [], lines: [] },
  };

  var hooks = {
    afterData: [],
    afterSeries: [],
    onCrosshair: [],
  };
  window.ChartDeskHooks = hooks;

  function post(type, payload) {
    try {
      var bridge = window.AndroidBridge;
      if (bridge && typeof bridge.postMessage === 'function') {
        bridge.postMessage(JSON.stringify({ type: type, payload: payload || {} }));
      }
    } catch (e) {
      // Never let a bridge failure break the render loop.
      console.warn('bridge post failed', e);
    }
  }

  function toast(message) {
    els.toast.textContent = String(message).slice(0, 400);
    els.toast.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { els.toast.classList.remove('show'); }, 4000);
  }

  function css(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  function rgba(hex, alpha) {
    var h = hex.replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var n = parseInt(h, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + alpha + ')';
  }

  function palette() {
    return {
      bg: css('--cd-bg'),
      surface: css('--cd-surface'),
      surfaceAlt: css('--cd-surface-alt'),
      border: css('--cd-border'),
      text: css('--cd-text'),
      dim: css('--cd-text-dim'),
      accent: css('--cd-accent'),
      up: css('--cd-up'),
      down: css('--cd-down'),
      grid: css('--cd-grid'),
      crosshair: css('--cd-crosshair'),
      mono: css('--cd-font-mono') || 'monospace',
    };
  }

  function createChart() {
    var p = palette();
    var chart = LWC.createChart(els.chart, {
      autoSize: true,
      layout: {
        background: { type: 'solid', color: p.bg },
        textColor: p.dim,
        fontSize: 11,
        fontFamily: p.mono,
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: p.grid },
        horzLines: { color: p.grid },
      },
      crosshair: {
        mode: LWC.CrosshairMode.Normal,
        vertLine: { color: p.crosshair, width: 1, style: LWC.LineStyle.Dashed, labelBackgroundColor: p.surfaceAlt },
        horzLine: { color: p.crosshair, width: 1, style: LWC.LineStyle.Dashed, labelBackgroundColor: p.surfaceAlt },
      },
      rightPriceScale: {
        borderColor: p.border,
        scaleMargins: { top: 0.08, bottom: 0.24 },
        entireTextOnly: true,
      },
      timeScale: {
        borderColor: p.border,
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 4,
        barSpacing: 6,
        minBarSpacing: 0.5,
        lockVisibleTimeRangeOnResize: true,
      },
      handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
      handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
      kineticScroll: { touch: true, mouse: false },
      localization: {
        priceFormatter: function (value) { return lib.formatPrice(value, state.precision); },
      },
      watermark: {
        visible: true,
        text: state.watermark,
        color: rgba(p.text, 0.045),
        fontSize: 44,
        horzAlign: 'center',
        vertAlign: 'center',
      },
    });

    chart.subscribeCrosshairMove(function (param) {
      if (!state.ready) return;
      var bar = null;
      if (param && param.time && param.seriesData && state.mainSeries) {
        bar = param.seriesData.get(state.mainSeries) || null;
      }
      renderLegend(bar);
      for (var i = 0; i < hooks.onCrosshair.length; i++) {
        try { hooks.onCrosshair[i](param, bar); } catch (e) { console.warn(e); }
      }
      if (programmaticCrosshair) return;
      post('crosshair', bar ? {
        time: bar.time,
        open: bar.open, high: bar.high, low: bar.low, close: bar.close,
        volume: bar.volume || 0,
      } : {});
    });

    chart.timeScale().subscribeVisibleLogicalRangeChange(function (range) {
      if (!range) return;
      post('visibleRange', { from: range.from, to: range.to });
      if (state.syncing) return;
      state.syncing = true;
      Object.keys(state.panes).forEach(function (key) {
        state.panes[key].chart.timeScale().setVisibleLogicalRange(range);
      });
      state.syncing = false;
    });

    state.chart = chart;
    return chart;
  }

  function mainSeriesOptions(p, type) {
    var base = {
      priceFormat: { type: 'price', precision: state.precision, minMove: Math.pow(10, -state.precision) },
      lastValueVisible: false,
      priceLineVisible: false,
    };
    if (type === 'line') {
      return Object.assign(base, { color: p.accent, lineWidth: 2, crosshairMarkerVisible: true });
    }
    if (type === 'area') {
      return Object.assign(base, {
        lineColor: p.accent,
        topColor: rgba(p.accent, 0.28),
        bottomColor: rgba(p.accent, 0.02),
        lineWidth: 2,
      });
    }
    if (type === 'bar') {
      return Object.assign(base, { upColor: p.up, downColor: p.down, thinBars: false });
    }
    return Object.assign(base, {
      upColor: p.up,
      downColor: p.down,
      borderVisible: false,
      wickUpColor: p.up,
      wickDownColor: p.down,
    });
  }

  function addMainSeries(type) {
    var p = palette();
    var opts = mainSeriesOptions(p, type);
    var series;
    if (type === 'line') series = state.chart.addLineSeries(opts);
    else if (type === 'area') series = state.chart.addAreaSeries(opts);
    else if (type === 'bar') series = state.chart.addBarSeries(opts);
    else series = state.chart.addCandlestickSeries(opts);
    state.mainSeries = series;
    state.type = type;
    return series;
  }

  function addVolumeSeries() {
    var p = palette();
    state.volumeSeries = state.chart.addHistogramSeries({
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
      lastValueVisible: false,
      priceLineVisible: false,
    });
    state.chart.priceScale('volume').applyOptions({
      scaleMargins: { top: 0.82, bottom: 0 },
      visible: false,
    });
  }

  function visibleRaw() {
    if (state.replay.active) return state.raw.slice(0, state.replay.index + 1);
    return state.raw;
  }

  function displayedCandles() {
    var base = state.type === 'heikin' ? lib.heikinAshi(state.raw) : state.raw;
    if (state.replay.active) return base.slice(0, state.replay.index + 1);
    return base;
  }

  function toSeriesData(type, candles) {
    if (type === 'line' || type === 'area') {
      return candles.map(function (c) { return { time: c.time, value: c.close }; });
    }
    return candles.map(function (c) {
      return { time: c.time, open: c.open, high: c.high, low: c.low, close: c.close };
    });
  }

  function volumeData(candles) {
    var p = palette();
    return candles.map(function (c) {
      return { time: c.time, value: c.volume || 0, color: rgba(c.close >= c.open ? p.up : p.down, 0.35) };
    });
  }

  function applyData(fit) {
    if (!state.mainSeries) return;
    var shown = displayedCandles();
    state.mainSeries.setData(toSeriesData(state.type, shown));
    if (state.volumeSeries) state.volumeSeries.setData(volumeData(visibleRaw()));
    if (state.raw.length) {
      var last = shown[shown.length - 1];
      setLastPriceLine(last.close);
      renderLegend(null);
    }
    if (fit) {
      state.chart.timeScale().fitContent();
    }
    for (var i = 0; i < hooks.afterData.length; i++) {
      try { hooks.afterData[i](shown); } catch (e) { console.warn(e); }
    }
  }

  function setLastPriceLine(price) {
    if (!state.mainSeries) return;
    var p = palette();
    if (state.lastPriceLine) {
      state.mainSeries.removePriceLine(state.lastPriceLine);
      state.lastPriceLine = null;
    }
    state.lastPriceLine = state.mainSeries.createPriceLine({
      price: price,
      color: p.accent,
      lineWidth: 1,
      lineStyle: LWC.LineStyle.Dotted,
      axisLabelVisible: true,
      title: 'LAST',
    });
  }

  function renderLegend(bar) {
    var p = palette();
    els.sym.textContent = state.symbol;
    els.tf.textContent = state.timeframe;
    var visible = visibleRaw();
    var last = visible.length ? visible[visible.length - 1] : null;
    var shown = state.type === 'heikin' && bar ? bar : (bar || (state.type === 'heikin' && visible.length ? lib.heikinAshi(visible).pop() : last));
    if (!shown) {
      ['o', 'h', 'l', 'c'].forEach(function (k) { els[k].textContent = '—'; });
      els.chg.textContent = '—';
      els.vol.textContent = '—';
      return;
    }
    els.o.textContent = lib.formatPrice(shown.open, state.precision);
    els.h.textContent = lib.formatPrice(shown.high, state.precision);
    els.l.textContent = lib.formatPrice(shown.low, state.precision);
    els.c.textContent = lib.formatPrice(shown.close, state.precision);
    var pct = lib.changePercent(shown.open, shown.close);
    els.chg.textContent = (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%';
    els.chg.className = 'val ' + (pct >= 0 ? 'up' : 'down');
    els.vol.textContent = lib.formatCompact(shown.volume || 0);
    els.c.style.color = pct >= 0 ? p.up : p.down;
  }

  function recreateSeries(type) {
    var data = state.raw;
    if (state.mainSeries) state.chart.removeSeries(state.mainSeries);
    state.mainSeries = null;
    if (state.lastPriceLine) state.lastPriceLine = null;
    addMainSeries(type);
    state.raw = data;
    if (window.ChartDeskDrawings) window.ChartDeskDrawings.setSeries(state.mainSeries);
    applyData(false);
    for (var i = 0; i < hooks.afterSeries.length; i++) {
      try { hooks.afterSeries[i](); } catch (e) { console.warn(e); }
    }
  }

  function setType(type) {
    var valid = { candle: 1, heikin: 1, line: 1, area: 1, bar: 1 };
    if (!valid[type] || type === state.type) return;
    recreateSeries(type);
    post('chartTypeChanged', { type: type });
  }

  function setTheme(dark, colorBlind) {
    state.theme = dark ? 'dark' : 'light';
    state.cb = !!colorBlind;
    document.documentElement.setAttribute('data-theme', state.theme);
    document.documentElement.setAttribute('data-cb', colorBlind ? 'true' : 'false');
    var p = palette();
    state.chart.applyOptions({
      layout: { background: { type: 'solid', color: p.bg }, textColor: p.dim },
      grid: { vertLines: { color: p.grid }, horzLines: { color: p.grid } },
      crosshair: {
        vertLine: { color: p.crosshair, labelBackgroundColor: p.surfaceAlt },
        horzLine: { color: p.crosshair, labelBackgroundColor: p.surfaceAlt },
      },
      rightPriceScale: { borderColor: p.border },
      timeScale: { borderColor: p.border },
    });
    if (state.volumeSeries) state.volumeSeries.setData(volumeData(state.raw));
    recreateSeries(state.type === 'heikin' ? 'heikin' : state.type);
    applyIndicatorTheme();
    post('themeApplied', { dark: !!dark, colorBlind: !!colorBlind });
  }

  function setScaleMode(mode) {
    var map = { normal: LWC.PriceScaleMode.Normal, log: LWC.PriceScaleMode.Logarithmic, percent: LWC.PriceScaleMode.Percentage };
    if (!map[mode]) return;
    state.scaleMode = mode;
    state.chart.priceScale('right').applyOptions({ mode: map[mode], autoScale: true });
    post('scaleModeChanged', { mode: mode });
  }

  function resetView() {
    if (!state.chart) return;
    state.chart.timeScale().fitContent();
    state.chart.priceScale('right').applyOptions({ autoScale: true });
    post('viewReset', {});
  }

  function setCandles(payload) {
    if (state.replay.active) replayStop(true);
    var rows = payload.candles || [];
    var precision = payload.precision;
    state.raw = rows.map(function (r) {
      if (Array.isArray(r)) {
        return { time: r[0], open: r[1], high: r[2], low: r[3], close: r[4], volume: r[5] || 0 };
      }
      return { time: r.time, open: r.open, high: r.high, low: r.low, close: r.close, volume: r.volume || 0 };
    });
    state.symbol = payload.symbol || state.symbol;
    state.timeframe = payload.timeframe || state.timeframe;
    state.precision = typeof precision === 'number' ? precision : lib.decimalsForPrice(state.raw.length ? state.raw[state.raw.length - 1].close : 0);
    if (state.mainSeries) {
      state.mainSeries.applyOptions({
        priceFormat: { type: 'price', precision: state.precision, minMove: Math.pow(10, -state.precision) },
      });
    }
    applyData(payload.fit !== false);
    els.skeleton.classList.add('hidden');
    state.ready = true;
    post('dataApplied', { count: state.raw.length, symbol: state.symbol, timeframe: state.timeframe });
    if (state.pine.code) runPine(state.pine.code);
  }

  function updateCandle(payload) {
    if (state.replay.active) return;
    var row = payload.candle;
    if (!row) return;
    var candle = Array.isArray(row)
      ? { time: row[0], open: row[1], high: row[2], low: row[3], close: row[4], volume: row[5] || 0 }
      : row;
    state.raw = lib.upsertCandle(state.raw, candle);
    if (!state.mainSeries) return;
    var shown = state.type === 'heikin' ? lib.heikinAshi(state.raw).pop() : candle;
    var data = state.type === 'line' || state.type === 'area'
      ? { time: shown.time, value: shown.close }
      : { time: shown.time, open: shown.open, high: shown.high, low: shown.low, close: shown.close };
    state.mainSeries.update(data);
    if (state.volumeSeries) {
      var p = palette();
      state.volumeSeries.update({ time: candle.time, value: candle.volume || 0, color: rgba(candle.close >= candle.open ? p.up : p.down, 0.35) });
    }
    setLastPriceLine(shown.close);
    renderLegend(null);
    for (var i = 0; i < hooks.afterData.length; i++) {
      try { hooks.afterData[i](displayedCandles()); } catch (e) { console.warn(e); }
    }
  }

  // ---- crosshair sync (multi-chart grid) ----------------------------------
  function setCrosshair(payload) {
    if (!state.mainSeries || !payload || payload.time === undefined || payload.time === null) return;
    programmaticCrosshair = true;
    try {
      state.chart.setCrosshairPosition(payload.price, payload.time, state.mainSeries);
    } catch (e) {
      console.warn(e);
    }
    clearTimeout(setCrosshair._t);
    setCrosshair._t = setTimeout(function () { programmaticCrosshair = false; }, 80);
  }

  function clearCrosshair() {
    programmaticCrosshair = true;
    try { state.chart.clearCrosshairPosition(); } catch (e) { console.warn(e); }
    clearTimeout(clearCrosshair._t);
    clearCrosshair._t = setTimeout(function () { programmaticCrosshair = false; }, 40);
  }

  // ---- Pine subset --------------------------------------------------------
  function pineColor(name) {
    var p = palette();
    switch (name) {
      case 'up': return p.up;
      case 'down': return p.down;
      case 'warning': return p.warning;
      case 'blue': return '#4C9BFF';
      case 'purple': return '#C084FC';
      case 'dim': return p.textSecondary;
      case 'text': return p.text;
      default: return p.accent;
    }
  }

  function clearPine() {
    state.pine.series.forEach(function (s) {
      try { state.chart.removeSeries(s); } catch (e) { console.warn(e); }
    });
    state.pine.series = [];
    state.pine.lines.forEach(function (line) {
      try { state.mainSeries.removePriceLine(line); } catch (e) { console.warn(e); }
    });
    state.pine.lines = [];
    if (state.mainSeries && state.mainSeries.setMarkers) state.mainSeries.setMarkers([]);
    state.pine.code = null;
  }

  function runPine(code) {
    var engine = window.ChartDeskPine;
    if (!engine) {
      post('pineResult', { ok: false, errors: [{ message: 'Pine engine not loaded', line: 1 }], warnings: [] });
      return;
    }
    clearPine();
    var result;
    try {
      result = engine.run(code, state.raw);
    } catch (e) {
      post('pineResult', { ok: false, errors: [{ message: String(e.message || e), line: 1 }], warnings: [] });
      return;
    }
    if (!result.ok) {
      post('pineResult', { ok: false, errors: result.errors, warnings: result.warnings || [] });
      return;
    }
    state.pine.code = code;
    result.plots.forEach(function (plot) {
      var series = state.chart.addLineSeries({
        color: pineColor(plot.color),
        lineWidth: 1,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
        priceFormat: { type: 'price', precision: state.precision, minMove: Math.pow(10, -state.precision) },
      });
      series.setData(plot.data);
      state.pine.series.push(series);
    });
    result.hlines.forEach(function (line) {
      var priceLine = state.mainSeries.createPriceLine({
        price: line.price,
        color: pineColor(line.color),
        lineWidth: 1,
        lineStyle: LWC.LineStyle.Dashed,
        axisLabelVisible: true,
        title: 'pine',
      });
      state.pine.lines.push(priceLine);
    });
    if (result.shapes.length && state.mainSeries.setMarkers) {
      var markers = [];
      result.shapes.forEach(function (shape) {
        shape.data.forEach(function (point) {
          markers.push({
            time: point.time,
            position: 'belowBar',
            color: pineColor(shape.color),
            shape: 'arrowUp',
            text: shape.title,
          });
        });
      });
      state.mainSeries.setMarkers(markers);
    }
    post('pineResult', {
      ok: true,
      errors: [],
      warnings: result.warnings || [],
      plots: result.plots.length,
      shapes: result.shapes.length,
    });
  }

  // ---- bar replay ---------------------------------------------------------
  function replayEngine() {
    return window.ChartDeskReplay;
  }

  function postReplay() {
    var engine = replayEngine();
    var visible = visibleRaw();
    var price = visible.length ? visible[visible.length - 1].close : 0;
    var summary = engine ? engine.summary(state.replay.position, price) : { side: null, entry: 0, pnl: 0, pnlPercent: 0 };
    post('replayState', {
      active: state.replay.active,
      playing: state.replay.playing,
      index: state.replay.index,
      total: state.raw.length,
      speed: state.replay.speed,
      price: price,
      side: summary.side,
      entry: summary.entry,
      pnl: summary.pnl,
      pnlPercent: summary.pnlPercent,
    });
  }

  function replayApply(fit) {
    applyData(!!fit);
    postReplay();
  }

  function replayStart(index) {
    var engine = replayEngine();
    if (!engine || !state.raw.length) return;
    clearInterval(state.replay.timer);
    state.replay.active = true;
    state.replay.playing = false;
    state.replay.index = engine.clampIndex(index, state.raw.length);
    state.replay.position = null;
    replayApply(true);
  }

  function replayPlay(speed) {
    var engine = replayEngine();
    if (!engine || !state.replay.active) return;
    if (speed) state.replay.speed = speed;
    state.replay.playing = true;
    clearInterval(state.replay.timer);
    state.replay.timer = setInterval(function () { replayStep(1); }, engine.intervalMs(state.replay.speed));
    postReplay();
  }

  function replayPause() {
    state.replay.playing = false;
    clearInterval(state.replay.timer);
    state.replay.timer = null;
    postReplay();
  }

  function replayStep(delta) {
    var engine = replayEngine();
    if (!engine || !state.replay.active) return;
    var next = engine.step(state.replay.index, state.raw.length, delta || 1);
    if (next === state.replay.index && (delta || 1) > 0) {
      replayPause();
      return;
    }
    state.replay.index = next;
    replayApply(false);
    if (next >= state.raw.length - 1 && state.replay.playing) replayPause();
  }

  function replaySeek(index) {
    var engine = replayEngine();
    if (!engine || !state.replay.active) return;
    state.replay.index = engine.clampIndex(index, state.raw.length);
    replayApply(false);
  }

  function replayStop(silent) {
    clearInterval(state.replay.timer);
    state.replay.timer = null;
    state.replay.active = false;
    state.replay.playing = false;
    state.replay.position = null;
    applyData(false);
    if (!silent) postReplay();
  }

  function replayTrade(action) {
    var engine = replayEngine();
    if (!engine || !state.replay.active) return;
    var visible = visibleRaw();
    if (!visible.length) return;
    state.replay.position = engine.trade(state.replay.position, action, visible[visible.length - 1].close);
    postReplay();
  }

  function replaySetSpeed(speed) {
    if (!state.replay.active) return;
    state.replay.speed = speed;
    if (state.replay.playing) replayPlay(speed);
    else postReplay();
  }

  // ---- indicators ---------------------------------------------------------
  function indicatorLib() {
    return window.ChartDeskIndicators;
  }

  function indicatorLabel(cfg) {
    var lib = indicatorLib();
    var spec = lib && lib.registry[cfg.type];
    var name = spec ? spec.name : cfg.type.toUpperCase();
    var params = cfg.params || {};
    var keys = Object.keys(params);
    if (!keys.length) return name;
    return name + ' ' + keys.map(function (k) { return params[k]; }).join('/');
  }

  function removeOverlay(uid) {
    var store = state.overlays[uid];
    if (!store) return;
    Object.keys(store).forEach(function (key) {
      try { state.chart.removeSeries(store[key]); } catch (e) { console.warn(e); }
    });
    delete state.overlays[uid];
  }

  function removePane(uid) {
    var pane = state.panes[uid];
    if (!pane) return;
    try { pane.chart.remove(); } catch (e) { console.warn(e); }
    if (pane.div && pane.div.parentNode) pane.div.parentNode.removeChild(pane.div);
    delete state.panes[uid];
    updateTimeAxis();
  }

  function updateTimeAxis() {
    var keys = Object.keys(state.panes);
    state.chart.applyOptions({ timeScale: { visible: keys.length === 0 } });
    keys.forEach(function (key, index) {
      state.panes[key].chart.applyOptions({ timeScale: { visible: index === keys.length - 1 } });
    });
  }

  function paneOptions() {
    var p = palette();
    return {
      autoSize: true,
      layout: { background: { type: 'solid', color: p.bg }, textColor: p.dim, fontSize: 9, fontFamily: p.mono, attributionLogo: false },
      grid: { vertLines: { color: p.grid }, horzLines: { color: p.grid } },
      crosshair: {
        mode: state.options.crosshair ? LWC.CrosshairMode.Normal : LWC.CrosshairMode.Hidden,
        vertLine: { color: p.crosshair, width: 1, style: LWC.LineStyle.Dashed, labelBackgroundColor: p.surfaceAlt },
        horzLine: { color: p.crosshair, width: 1, style: LWC.LineStyle.Dashed, labelBackgroundColor: p.surfaceAlt },
      },
      rightPriceScale: { borderColor: p.border, scaleMargins: { top: 0.18, bottom: 0.08 }, entireTextOnly: true },
      timeScale: { visible: false, borderColor: p.border, rightOffset: 4 },
      handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
      handleScale: { axisPressedMouseMove: false, mouseWheel: true, pinch: true },
      kineticScroll: { touch: true, mouse: false },
      localization: { priceFormatter: function (value) { return lib.formatPrice(value, Math.min(state.precision + 1, 6)); } },
    };
  }

  function createPane(cfg) {
    var div = document.createElement('div');
    div.className = 'pane';
    var title = document.createElement('div');
    title.className = 'pane-title';
    div.appendChild(title);
    els.panes.appendChild(div);
    var chart = LWC.createChart(div, paneOptions());
    var pane = { div: div, title: title, chart: chart, series: {}, hlineCount: 0 };
    state.panes[cfg.uid] = pane;
    chart.timeScale().subscribeVisibleLogicalRangeChange(function (range) {
      if (!range || state.syncing) return;
      state.syncing = true;
      state.chart.timeScale().setVisibleLogicalRange(range);
      Object.keys(state.panes).forEach(function (key) {
        if (key !== cfg.uid) state.panes[key].chart.timeScale().setVisibleLogicalRange(range);
      });
      state.syncing = false;
    });
    var mainRange = state.chart.timeScale().getVisibleLogicalRange();
    if (mainRange) chart.timeScale().setVisibleLogicalRange(mainRange);
    updateTimeAxis();
    return pane;
  }

  function seriesForPane(pane, plot, histogram) {
    var key = histogram ? '__hist' : plot.key;
    var existing = pane.series[key];
    if (existing) {
      if (plot) existing.applyOptions({ color: plot.color });
      return existing;
    }
    var options = {
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
      priceFormat: { type: 'price', precision: Math.min(state.precision + 1, 6), minMove: Math.pow(10, -Math.min(state.precision + 1, 6)) },
    };
    if (histogram) {
      options.color = plot.color;
      options.base = 0;
    } else {
      options.color = plot.color;
      options.lineWidth = 1;
    }
    var series = histogram ? pane.chart.addHistogramSeries(options) : pane.chart.addLineSeries(options);
    pane.series[key] = series;
    return series;
  }

  function updateOverlay(cfg, result) {
    var store = state.overlays[cfg.uid] || (state.overlays[cfg.uid] = {});
    result.plots.forEach(function (plot) {
      var series = store[plot.key];
      if (!series) {
        series = state.chart.addLineSeries({
          color: plot.color,
          lineWidth: 1,
          priceLineVisible: false,
          lastValueVisible: false,
          crosshairMarkerVisible: false,
          priceFormat: { type: 'price', precision: state.precision, minMove: Math.pow(10, -state.precision) },
        });
        store[plot.key] = series;
      } else {
        series.applyOptions({ color: plot.color });
      }
      series.setData(plot.data);
    });
  }

  function updatePane(cfg, result) {
    var pane = state.panes[cfg.uid] || createPane(cfg);
    pane.title.textContent = indicatorLabel(cfg);
    result.plots.forEach(function (plot) {
      seriesForPane(pane, plot, false).setData(plot.data);
    });
    if (result.histogram) {
      var hist = seriesForPane(pane, { key: 'hist', color: palette().dim }, true);
      hist.setData(result.histogram);
    }
    if (result.hlines && result.hlineCount !== pane.hlineCount) {
      // Recreate reference lines only when the indicator set changes.
      pane.hlineCount = result.hlineCount;
    }
    if (result.hlines) {
      if (!pane.hlinesDrawn) {
        var first = pane.series[result.plots[0].key];
        if (first) {
          result.hlines.forEach(function (line) {
            first.createPriceLine({ price: line.price, color: line.color, lineWidth: 1, lineStyle: LWC.LineStyle.Dashed, axisLabelVisible: false, title: '' });
          });
          pane.hlinesDrawn = true;
        }
      }
    }
  }

  function computeAll(candles) {
    var libInd = indicatorLib();
    if (!libInd || !state.chart) return;
    var data = candles || state.raw;
    var active = {};
    state.indicators.forEach(function (cfg) {
      if (!cfg.visible) return;
      active[cfg.type] = true;
      try {
        if (cfg.type === 'volume') return;
        var result = libInd.compute(cfg.type, data, cfg.params || {});
        var spec = libInd.registry[cfg.type];
        if (!spec) return;
        if (spec.pane === 'overlay') updateOverlay(cfg, result);
        else updatePane(cfg, result);
      } catch (e) {
        console.warn('indicator failed: ' + cfg.type, e);
        post('error', { message: 'Indicator ' + cfg.type + ' failed: ' + e.message });
      }
    });
    if (state.volumeSeries) {
      state.volumeSeries.applyOptions({ visible: !!active.volume });
    }
  }

  function setIndicators(list) {
    var incoming = (list || []).filter(function (item) { return item && item.type; }).map(function (item) {
      return {
        uid: item.uid || item.type,
        type: item.type,
        params: item.params || {},
        visible: item.visible !== false,
      };
    });
    state.indicators = incoming;
    var keep = {};
    incoming.forEach(function (i) { keep[i.uid] = true; });
    Object.keys(state.overlays).forEach(function (uid) { if (!keep[uid]) removeOverlay(uid); });
    Object.keys(state.panes).forEach(function (uid) { if (!keep[uid]) removePane(uid); });
    computeAll(state.raw);
    renderIndicatorLegend();
    updateTimeAxis();
  }

  function renderIndicatorLegend() {
    if (!els.indicatorLegend) return;
    els.indicatorLegend.innerHTML = '';
    var lib = indicatorLib();
    state.indicators.forEach(function (cfg) {
      if (!cfg.visible) return;
      var spec = lib && lib.registry[cfg.type];
      var color = cfg.type === 'volume' ? palette().dim : (spec && spec.pane === 'separate' ? palette().accent : '#4C9BFF');
      var item = document.createElement('span');
      item.className = 'item';
      var dot = document.createElement('span');
      dot.className = 'dot';
      dot.style.background = color;
      item.appendChild(dot);
      item.appendChild(document.createTextNode(indicatorLabel(cfg)));
      els.indicatorLegend.appendChild(item);
    });
  }

  function applyIndicatorTheme() {
    Object.keys(state.panes).forEach(function (key) {
      var pane = state.panes[key];
      var p = palette();
      pane.chart.applyOptions({
        layout: { background: { type: 'solid', color: p.bg }, textColor: p.dim },
        grid: { vertLines: { color: p.grid }, horzLines: { color: p.grid } },
        rightPriceScale: { borderColor: p.border },
        timeScale: { borderColor: p.border },
      });
    });
  }

  function setOptions(payload) {
    var showGrid = payload.grid !== false;
    var showCrosshair = payload.crosshair !== false;
    var showWatermark = payload.watermark !== false;
    state.chart.applyOptions({
      grid: {
        vertLines: { visible: showGrid },
        horzLines: { visible: showGrid },
      },
      crosshair: {
        mode: showCrosshair ? LWC.CrosshairMode.Normal : LWC.CrosshairMode.Hidden,
      },
      watermark: { visible: showWatermark },
    });
    state.options = { grid: showGrid, crosshair: showCrosshair, watermark: showWatermark };
  }

  function setWatermark(text) {
    state.watermark = text || 'ChartDesk';
    state.chart.applyOptions({ watermark: { visible: true, text: state.watermark } });
  }

  function handle(raw) {
    var msg;
    try {
      msg = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (e) {
      toast('Bad command payload');
      return;
    }
    if (!msg || !msg.type) return;
    var payload = msg.payload || {};
    try {
      switch (msg.type) {
        case 'init':
          if (payload.theme) setTheme(payload.theme.dark !== false, !!payload.theme.colorBlind);
          if (payload.type) setType(payload.type);
          if (payload.scale) setScaleMode(payload.scale);
          if (payload.symbol) state.symbol = payload.symbol;
          if (payload.timeframe) state.timeframe = payload.timeframe;
          renderLegend(null);
          break;
        case 'setCandles': setCandles(payload); break;
        case 'updateCandle': updateCandle(payload); break;
        case 'setChartType': setType(payload.type); break;
        case 'setTheme': setTheme(payload.dark !== false, !!payload.colorBlind); break;
        case 'setPriceScale': setScaleMode(payload.mode); break;
        case 'setTimeframe': state.timeframe = payload.timeframe || state.timeframe; renderLegend(null); break;
        case 'setSymbol': state.symbol = payload.symbol || state.symbol; renderLegend(null); break;
        case 'resetView': resetView(); break;
        case 'setWatermark': setWatermark(payload.text); break;
        case 'setPrecision': state.precision = payload.precision; applyData(false); break;
        case 'setOptions': setOptions(payload); break;
        case 'setIndicators': setIndicators(payload.list); break;
        case 'setDrawings':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.setDrawings(payload.list || []);
          break;
        case 'setDrawTool':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.setTool(payload.tool);
          break;
        case 'setDrawMode':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.setMode(payload.mode);
          break;
        case 'undoDrawing':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.undo();
          break;
        case 'redoDrawing':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.redo();
          break;
        case 'deleteDrawing':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.deleteSelected();
          break;
        case 'lockDrawing':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.setLocked(!!payload.locked);
          break;
        case 'clearDrawings':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.clear();
          break;
        case 'updateDrawingText':
          if (window.ChartDeskDrawings) window.ChartDeskDrawings.updateText(payload.id, payload.text);
          break;
        case 'replayStart': replayStart(payload.index || 0); break;
        case 'replayPlay': replayPlay(payload.speed); break;
        case 'replayPause': replayPause(); break;
        case 'replayStep': replayStep(payload.delta || 1); break;
        case 'replaySeek': replaySeek(payload.index || 0); break;
        case 'replayStop': replayStop(false); break;
        case 'replaySpeed': replaySetSpeed(payload.speed || 1); break;
        case 'replayTrade': replayTrade(payload.action); break;
        case 'runPine': runPine(payload.code || ''); break;
        case 'clearPine': clearPine(); break;
        case 'setCrosshair': setCrosshair(payload); break;
        case 'clearCrosshair': clearCrosshair(); break;
        case 'showError': toast(payload.message || 'Chart error'); break;
        default:
          if (window.ChartDeskExt && typeof window.ChartDeskExt.handle === 'function') {
            var handled = window.ChartDeskExt.handle(msg.type, payload);
            if (!handled) console.warn('Unknown command', msg.type);
          } else {
            console.warn('Unknown command', msg.type);
          }
      }
      if (msg.id) post('ack', { id: msg.id });
    } catch (e) {
      console.error(e);
      toast(e && e.message ? e.message : 'Command failed');
      post('error', { command: msg.type, message: String(e && e.message ? e.message : e) });
    }
  }

  // ---- touch niceties -----------------------------------------------------
  var pressTimer = null;
  var inspecting = false;
  var downAt = null;
  var lastTap = 0;
  var lastTapX = 0;
  var lastTapY = 0;

  function localPoint(e) {
    var rect = els.chart.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function inspectAt(pt) {
    if (!state.mainSeries) return;
    var price = state.mainSeries.coordinateToPrice(pt.y);
    var time = state.chart.timeScale().coordinateToTime(pt.x);
    if (price === null || time === null) return;
    state.chart.setCrosshairPosition(price, time, state.mainSeries);
  }

  els.chart.addEventListener('pointerdown', function (e) {
    downAt = localPoint(e);
    var now = Date.now();
    if (now - lastTap < 280 && Math.abs(downAt.x - lastTapX) < 32 && Math.abs(downAt.y - lastTapY) < 32) {
      resetView();
      post('haptic', { kind: 'reset' });
      lastTap = 0;
      return;
    }
    lastTap = now;
    lastTapX = downAt.x;
    lastTapY = downAt.y;
    clearTimeout(pressTimer);
    pressTimer = setTimeout(function () {
      inspecting = true;
      inspectAt(downAt);
      post('haptic', { kind: 'inspect' });
    }, 420);
  });

  els.chart.addEventListener('pointermove', function (e) {
    if (!downAt) return;
    var pt = localPoint(e);
    if (!inspecting && (Math.abs(pt.x - downAt.x) > 10 || Math.abs(pt.y - downAt.y) > 10)) {
      clearTimeout(pressTimer);
    }
    if (inspecting) inspectAt(pt);
  });

  function endPress() {
    clearTimeout(pressTimer);
    downAt = null;
    if (inspecting) {
      setTimeout(function () {
        inspecting = false;
        if (state.chart) state.chart.clearCrosshairPosition();
      }, 650);
    }
  }

  els.chart.addEventListener('pointerup', endPress);
  els.chart.addEventListener('pointercancel', endPress);
  els.chart.addEventListener('pointerleave', endPress);

  window.addEventListener('error', function (e) {
    post('error', { message: String(e.message || 'JS error'), source: 'window' });
  });

  window.addEventListener('unhandledrejection', function (e) {
    post('error', { message: String((e.reason && e.reason.message) || e.reason || 'Promise rejection'), source: 'promise' });
  });

  // ---- boot ---------------------------------------------------------------
  try {
    createChart();
    addMainSeries('candle');
    addVolumeSeries();
    renderLegend(null);
    hooks.afterData.push(function (candles) { computeAll(candles); });
    hooks.afterSeries.push(function () { computeAll(state.raw); });
    if (window.ChartDeskDrawings) {
      window.ChartDeskDrawings.init({
        chart: state.chart,
        mainSeries: state.mainSeries,
        container: els.chart,
        post: post,
        palette: palette,
      });
    }
    window.ChartDesk = {
      handle: handle,
      state: state,
      hooks: hooks,
      setType: setType,
      setTheme: setTheme,
      resetView: resetView,
      post: post,
    };
    post('ready', { version: 1, engine: 'lightweight-charts', types: ['candle', 'heikin', 'line', 'area', 'bar'] });
    setTimeout(function () { els.skeleton.classList.add('hidden'); }, 600);
  } catch (e) {
    console.error(e);
    els.skeleton.classList.add('hidden');
    toast('Chart failed to start: ' + (e.message || e));
    post('error', { message: String(e.message || e), source: 'boot' });
  }
})();
