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
      post('crosshair', bar ? {
        time: bar.time,
        open: bar.open, high: bar.high, low: bar.low, close: bar.close,
        volume: bar.volume || 0,
      } : {});
    });

    chart.timeScale().subscribeVisibleLogicalRangeChange(function (range) {
      if (!range) return;
      post('visibleRange', { from: range.from, to: range.to });
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

  function displayedCandles() {
    if (state.type === 'heikin') return lib.heikinAshi(state.raw);
    return state.raw;
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
    if (state.volumeSeries) state.volumeSeries.setData(volumeData(state.raw));
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
    var last = state.raw.length ? state.raw[state.raw.length - 1] : null;
    var shown = state.type === 'heikin' && bar ? bar : (bar || (state.type === 'heikin' && state.raw.length ? lib.heikinAshi(state.raw).pop() : last));
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
  }

  function updateCandle(payload) {
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
