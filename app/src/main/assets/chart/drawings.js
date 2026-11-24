/* ChartDesk drawing layer: canvas overlay on top of lightweight-charts. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ChartDeskDrawings = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var api = null;
  var canvas = null;
  var ctx = null;
  var dpr = 1;
  var raf = 0;

  var state = {
    drawings: [],
    selectedId: null,
    mode: 'none',
    tool: null,
    draft: [],
    draftPoint: null,
    history: [],
    redoStack: [],
    drag: null,
    measure: null,
    pendingTextId: null,
  };

  var TOOLS = ['trendline', 'ray', 'hline', 'vline', 'rect', 'fib', 'fibchannel', 'text', 'long', 'short', 'measure'];
  var POINTS_FOR = {
    trendline: 2, ray: 2, hline: 1, vline: 1, rect: 2, fib: 2, fibchannel: 3,
    text: 1, long: 2, short: 2, measure: 2,
  };

  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

  function distanceToSegment(px, py, x1, y1, x2, y2) {
    var dx = x2 - x1;
    var dy = y2 - y1;
    if (dx === 0 && dy === 0) return Math.hypot(px - x1, py - y1);
    var t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
    t = clamp(t, 0, 1);
    return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
  }

  function fibLevels() {
    return [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1];
  }

  function newId() {
    return 'd' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  }

  function post(type, payload) {
    if (api && api.post) api.post(type, payload);
  }

  function colors() {
    var p = api.palette();
    return {
      line: p.accent,
      up: p.up,
      down: p.down,
      text: p.text,
      dim: p.textSecondary,
      surface: p.surface,
      border: p.border,
    };
  }

  function attach() {
    if (canvas) return;
    canvas = document.createElement('canvas');
    canvas.id = 'drawings-canvas';
    canvas.style.position = 'absolute';
    canvas.style.inset = '0';
    canvas.style.zIndex = '2';
    canvas.style.pointerEvents = 'none';
    canvas.style.touchAction = 'none';
    api.container.appendChild(canvas);
    ctx = canvas.getContext('2d');
    resize();
    if (window.ResizeObserver) {
      new ResizeObserver(resize).observe(api.container);
    } else {
      window.addEventListener('resize', resize);
    }
    canvas.addEventListener('pointerdown', onDown);
    canvas.addEventListener('pointermove', onMove);
    canvas.addEventListener('pointerup', onUp);
    canvas.addEventListener('pointercancel', onUp);
    canvas.addEventListener('pointerleave', function () { state.draftPoint = null; });
    api.chart.timeScale().subscribeVisibleLogicalRangeChange(schedule);
    api.chart.subscribeCrosshairMove(schedule);
    api.container.addEventListener('wheel', schedule, { passive: true });
    setInterval(schedule, 250);
  }

  function resize() {
    if (!canvas) return;
    var rect = api.container.getBoundingClientRect();
    dpr = window.devicePixelRatio || 1;
    canvas.width = Math.max(1, Math.floor(rect.width * dpr));
    canvas.height = Math.max(1, Math.floor(rect.height * dpr));
    canvas.style.width = rect.width + 'px';
    canvas.style.height = rect.height + 'px';
    schedule();
  }

  function schedule() {
    if (raf) return;
    raf = requestAnimationFrame(function () {
      raf = 0;
      draw();
    });
  }

  function xOf(time) {
    if (time === null || time === undefined) return null;
    var x = api.chart.timeScale().timeToCoordinate(time);
    return x === null ? null : x;
  }

  function yOf(price) {
    if (price === null || price === undefined) return null;
    var y = api.mainSeries.priceToCoordinate(price);
    return y === null ? null : y;
  }

  function pointPx(pt) {
    var x = xOf(pt.time);
    var y = yOf(pt.price);
    if (x === null || y === null) return null;
    return { x: x, y: y };
  }

  function pointerPoint(event) {
    var rect = canvas.getBoundingClientRect();
    var x = event.clientX - rect.left;
    var y = event.clientY - rect.top;
    var time = api.chart.timeScale().coordinateToTime(x);
    var price = api.mainSeries.coordinateToPrice(y);
    if (price === null) return null;
    return { x: x, y: y, time: time === null ? null : time, price: price };
  }

  function colorFor(drawing) {
    if (drawing.color) return drawing.color;
    if (drawing.type === 'long') return colors().up;
    if (drawing.type === 'short') return colors().down;
    return colors().line;
  }

  function draw() {
    if (!ctx) return;
    var w = canvas.width / dpr;
    var h = canvas.height / dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';

    state.drawings.forEach(function (drawing) {
      drawOne(drawing, drawing.id === state.selectedId, w, h);
    });

    if (state.draft.length && state.draftPoint) {
      drawOne({ type: state.tool, points: state.draft.concat([state.draftPoint]), draft: true }, false, w, h);
    }
    if (state.measure) {
      drawMeasure(state.measure, w, h);
    }
  }

  function drawOne(drawing, selected, w, h) {
    var pts = drawing.points.map(pointPx);
    if (pts.some(function (p) { return p === null; })) return;
    var c = colorFor(drawing);
    var width = selected ? 2 : 1.4;
    ctx.strokeStyle = c;
    ctx.fillStyle = c;
    ctx.lineWidth = width;
    ctx.setLineDash(drawing.locked ? [4, 3] : []);

    switch (drawing.type) {
      case 'trendline':
        line(pts[0], pts[1]);
        break;
      case 'ray':
        ray(pts[0], pts[1], w, h);
        break;
      case 'hline':
        horizontal(pts[0].y, w);
        break;
      case 'vline':
        vertical(pts[0].x, h);
        break;
      case 'rect':
        rectangle(pts[0], pts[1], c);
        break;
      case 'fib':
        fibonacci(pts[0], pts[1], w);
        break;
      case 'fibchannel':
        fibChannel(pts, w);
        break;
      case 'text':
        text(drawing, pts[0]);
        break;
      case 'long':
      case 'short':
        position(drawing, pts[0], pts[1], w);
        break;
      default:
        line(pts[0], pts[1]);
    }

    if (selected) {
      ctx.setLineDash([]);
      pts.forEach(function (p) { handle(p.x, p.y); });
      if (drawing.locked) {
        ctx.fillStyle = colors().dim;
        ctx.font = '9px monospace';
        ctx.fillText('locked', pts[0].x + 6, pts[0].y - 8);
      }
    }
  }

  function line(a, b) {
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
  }

  function ray(a, b, w, h) {
    var dx = b.x - a.x;
    var dy = b.y - a.y;
    if (dx === 0 && dy === 0) return;
    var scale = Math.max(w, h) * 2 / Math.hypot(dx, dy);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(a.x + dx * scale, a.y + dy * scale);
    ctx.stroke();
  }

  function horizontal(y, w) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(w, y);
    ctx.stroke();
  }

  function vertical(x, h) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, h);
    ctx.stroke();
  }

  function rectangle(a, b, color) {
    ctx.save();
    ctx.globalAlpha = 0.12;
    ctx.fillStyle = color;
    ctx.fillRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y));
    ctx.restore();
    ctx.strokeRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y));
  }

  function fibonacci(a, b, w) {
    line(a, b);
    ctx.font = '9px monospace';
    ctx.textAlign = 'left';
    fibLevels().forEach(function (level) {
      var y = b.y + (a.y - b.y) * level;
      ctx.save();
      ctx.setLineDash([2, 3]);
      ctx.beginPath();
      ctx.moveTo(Math.min(a.x, b.x), y);
      ctx.lineTo(w, y);
      ctx.stroke();
      ctx.restore();
      ctx.fillStyle = colors().dim;
      ctx.fillText((level * 100).toFixed(1) + '%', Math.min(a.x, b.x) + 4, y - 2);
    });
  }

  function fibChannel(pts, w) {
    if (pts.length < 3) return;
    var a = pts[0];
    var b = pts[1];
    var offset = pts[2].y - a.y;
    [0, 0.5, 1].forEach(function (f) {
      var dy = offset * f;
      ctx.save();
      ctx.setLineDash(f === 0 ? [] : [3, 3]);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y + dy);
      ctx.lineTo(b.x, b.y + dy);
      ctx.stroke();
      ctx.restore();
    });
    ctx.beginPath();
    ctx.moveTo(pts[2].x, pts[2].y);
    ctx.lineTo(w, pts[2].y);
    ctx.setLineDash([2, 3]);
    ctx.stroke();
  }

  function text(drawing, p) {
    ctx.font = '11px -apple-system, Roboto, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillStyle = colors().text;
    var label = drawing.text || 'Text';
    ctx.fillText(label, p.x + 4, p.y - 4);
    ctx.strokeStyle = colorFor(drawing);
    ctx.beginPath();
    ctx.arc(p.x, p.y, 2.5, 0, Math.PI * 2);
    ctx.stroke();
  }

  function position(drawing, entry, target, w) {
    var isLong = drawing.type === 'long';
    var risk = Math.abs(target.y - entry.y) * 0.5;
    var stop = isLong ? entry.y + risk : entry.y - risk;
    var green = colors().up;
    var red = colors().down;

    ctx.save();
    ctx.globalAlpha = 0.16;
    ctx.fillStyle = green;
    ctx.fillRect(Math.min(entry.x, target.x), Math.min(entry.y, target.y), Math.abs(target.x - entry.x), Math.abs(target.y - entry.y));
    ctx.fillStyle = red;
    ctx.fillRect(Math.min(entry.x, target.x), Math.min(entry.y, stop), Math.abs(target.x - entry.x), Math.abs(stop - entry.y));
    ctx.restore();

    ctx.setLineDash([]);
    ctx.strokeStyle = colors().text;
    ctx.beginPath();
    ctx.moveTo(Math.min(entry.x, target.x), entry.y);
    ctx.lineTo(Math.max(entry.x, target.x), entry.y);
    ctx.stroke();

    ctx.font = '9px monospace';
    ctx.fillStyle = colors().text;
    var rr = Math.abs(target.y - entry.y) / Math.max(1, Math.abs(stop - entry.y));
    ctx.fillText((isLong ? 'Long' : 'Short') + '  R:R ' + rr.toFixed(2), Math.min(entry.x, target.x) + 4, Math.min(entry.y, target.y) - 6);
    ctx.fillStyle = colors().dim;
    ctx.fillText('target', Math.max(entry.x, target.x) + 4, target.y + 3);
    ctx.fillText('stop', Math.max(entry.x, target.x) + 4, stop + 3);
  }

  function drawMeasure(m, w, h) {
    var a = pointPx(m.a);
    var b = pointPx(m.b);
    if (!a || !b) return;
    ctx.save();
    ctx.strokeStyle = colors().dim;
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
    ctx.setLineDash([]);
    var delta = m.b.price - m.a.price;
    var pct = m.a.price ? (delta / m.a.price) * 100 : 0;
    ctx.fillStyle = delta >= 0 ? colors().up : colors().down;
    ctx.font = '10px monospace';
    ctx.fillText((delta >= 0 ? '+' : '') + delta.toFixed(4) + '  (' + pct.toFixed(2) + '%)', b.x + 6, b.y - 6);
    ctx.restore();
  }

  function handle(x, y) {
    ctx.fillStyle = colors().surface;
    ctx.strokeStyle = colors().line;
    ctx.lineWidth = 1;
    ctx.fillRect(x - 4, y - 4, 8, 8);
    ctx.strokeRect(x - 4, y - 4, 8, 8);
  }

  // ---- hit testing --------------------------------------------------------
  function hitTest(x, y) {
    for (var i = state.drawings.length - 1; i >= 0; i--) {
      var d = state.drawings[i];
      var pts = d.points.map(pointPx);
      if (pts.some(function (p) { return p === null; })) continue;
      if (hit(d, pts, x, y)) return d;
    }
    return null;
  }

  function hit(d, pts, x, y) {
    switch (d.type) {
      case 'hline':
        return Math.abs(y - pts[0].y) < 10;
      case 'vline':
        return Math.abs(x - pts[0].x) < 10;
      case 'rect':
        return x >= Math.min(pts[0].x, pts[1].x) - 4 && x <= Math.max(pts[0].x, pts[1].x) + 4 &&
          y >= Math.min(pts[0].y, pts[1].y) - 4 && y <= Math.max(pts[0].y, pts[1].y) + 4;
      case 'text':
        return Math.abs(x - pts[0].x) < 60 && Math.abs(y - pts[0].y) < 18;
      case 'long':
      case 'short': {
        var top = Math.min(pts[0].y, pts[1].y);
        var bottom = Math.max(pts[0].y, pts[1].y);
        return x >= Math.min(pts[0].x, pts[1].x) && x <= Math.max(pts[0].x, pts[1].x) &&
          y >= top - 10 && y <= bottom + 10;
      }
      case 'fib':
        return fibLevels().some(function (level) {
          var ly = pts[1].y + (pts[0].y - pts[1].y) * level;
          return Math.abs(y - ly) < 8;
        }) || distanceToSegment(x, y, pts[0].x, pts[0].y, pts[1].x, pts[1].y) < 10;
      default:
        return distanceToSegment(x, y, pts[0].x, pts[0].y, pts[1].x, pts[1].y) < 10;
    }
  }

  function handleHit(drawing, x, y) {
    var pts = drawing.points.map(pointPx);
    for (var i = 0; i < pts.length; i++) {
      if (!pts[i]) continue;
      if (Math.abs(x - pts[i].x) < 12 && Math.abs(y - pts[i].y) < 12) return i;
    }
    return -1;
  }

  // ---- interaction --------------------------------------------------------
  function interactive() {
    return state.mode !== 'none';
  }

  function syncPointer() {
    if (!canvas) return;
    canvas.style.pointerEvents = interactive() ? 'auto' : 'none';
  }

  function onDown(event) {
    if (!interactive()) return;
    event.preventDefault();
    var p = pointerPoint(event);
    if (!p) return;

    if (state.tool) {
      if (state.tool === 'measure') {
        state.measure = { a: { time: p.time, price: p.price }, b: { time: p.time, price: p.price } };
        schedule();
        return;
      }
      if (state.tool === 'hline' || state.tool === 'vline' || state.tool === 'text') {
        var drawing = {
          id: newId(),
          type: state.tool,
          points: [{ time: p.time, price: p.price }],
          locked: false,
          text: state.tool === 'text' ? 'Text' : undefined,
        };
        addDrawing(drawing);
        if (state.tool === 'text') post('textRequest', { id: drawing.id });
        return;
      }
      state.draft.push({ time: p.time, price: p.price });
      state.draftPoint = { time: p.time, price: p.price };
      if (state.draft.length >= POINTS_FOR[state.tool]) {
        var pts = state.draft.slice(0, POINTS_FOR[state.tool]);
        addDrawing({ id: newId(), type: state.tool, points: pts, locked: false });
      }
      schedule();
      return;
    }

    // select / move mode
    if (state.selectedId) {
      var selected = find(state.selectedId);
      if (selected && !selected.locked) {
        var hIdx = handleHit(selected, p.x, p.y);
        if (hIdx >= 0) {
          state.drag = { id: selected.id, pointIndex: hIdx, origin: clone(selected.points), start: p, before: snapshot() };
          return;
        }
      }
    }
    var hitDrawing = hitTest(p.x, p.y);
    if (hitDrawing) {
      state.selectedId = hitDrawing.id;
      post('drawingSelected', { id: hitDrawing.id, locked: !!hitDrawing.locked });
      if (!hitDrawing.locked) {
        state.drag = { id: hitDrawing.id, pointIndex: -1, origin: clone(hitDrawing.points), start: p, before: snapshot() };
      }
    } else {
      state.selectedId = null;
      post('drawingSelected', { id: null });
    }
    schedule();
  }

  function onMove(event) {
    if (!interactive()) return;
    var p = pointerPoint(event);
    if (!p) return;
    if (state.tool === 'measure' && state.measure) {
      state.measure.b = { time: p.time, price: p.price };
      schedule();
      return;
    }
    if (state.tool && state.draft.length) {
      state.draftPoint = { time: p.time, price: p.price };
      schedule();
      return;
    }
    if (state.drag) {
      var d = find(state.drag.id);
      if (!d) return;
      if (state.drag.pointIndex >= 0) {
        d.points[state.drag.pointIndex] = { time: p.time, price: p.price };
      } else {
        var dt = (p.time || 0) - (state.drag.start.time || 0);
        var dp = p.price - state.drag.start.price;
        d.points = state.drag.origin.map(function (pt) {
          return { time: pt.time === null ? null : pt.time + dt, price: pt.price + dp };
        });
      }
      schedule();
    }
  }

  function onUp() {
    if (state.measure) {
      if (state.measure.a && state.measure.b) {
        post('measure', {
          from: state.measure.a.price,
          to: state.measure.b.price,
          pct: state.measure.a.price ? ((state.measure.b.price - state.measure.a.price) / state.measure.a.price) * 100 : 0,
        });
      }
      state.measure = null;
      schedule();
      return;
    }
    if (state.drag) {
      if (state.drag.before) pushHistory(state.drag.before);
      state.drag = null;
      persist();
      schedule();
    }
  }

  function addDrawing(drawing) {
    pushHistory();
    state.drawings.push(drawing);
    state.selectedId = drawing.id;
    state.draft = [];
    state.draftPoint = null;
    state.tool = null;
    state.mode = 'select';
    syncPointer();
    post('modeChanged', { mode: 'select' });
    post('drawingSelected', { id: drawing.id, locked: false });
    persist();
    schedule();
  }

  function find(id) {
    return state.drawings.filter(function (d) { return d.id === id; })[0] || null;
  }

  function clone(points) {
    return points.map(function (p) { return { time: p.time, price: p.price }; });
  }

  function snapshot() {
    return JSON.stringify(state.drawings);
  }

  function pushHistory(before) {
    state.history.push(before || snapshot());
    if (state.history.length > 60) state.history.shift();
    state.redoStack = [];
  }

  function persist() {
    post('drawingsChanged', { list: state.drawings });
  }

  // ---- public API ---------------------------------------------------------
  function init(options) {
    api = options;
    attach();
  }

  function setDrawings(list) {
    state.drawings = (list || []).map(function (d) {
      return {
        id: d.id || newId(),
        type: d.type,
        points: (d.points || []).map(function (p) { return { time: p.time, price: p.price }; }),
        locked: !!d.locked,
        text: d.text,
        color: d.color,
      };
    });
    state.selectedId = null;
    schedule();
  }

  function setMode(mode) {
    state.mode = mode === 'none' ? 'none' : 'select';
    if (state.mode === 'none') {
      state.selectedId = null;
      state.tool = null;
      state.draft = [];
      state.draftPoint = null;
    }
    syncPointer();
    schedule();
  }

  function setTool(tool) {
    if (TOOLS.indexOf(tool) === -1) return;
    state.tool = tool;
    state.mode = 'select';
    state.draft = [];
    state.draftPoint = null;
    state.selectedId = null;
    syncPointer();
    schedule();
  }

  function undo() {
    if (!state.history.length) return;
    state.redoStack.push(snapshot());
    state.drawings = JSON.parse(state.history.pop());
    state.selectedId = null;
    persist();
    schedule();
  }

  function redo() {
    if (!state.redoStack.length) return;
    state.history.push(snapshot());
    state.drawings = JSON.parse(state.redoStack.pop());
    state.selectedId = null;
    persist();
    schedule();
  }

  function deleteSelected() {
    if (!state.selectedId) return;
    pushHistory();
    state.drawings = state.drawings.filter(function (d) { return d.id !== state.selectedId; });
    state.selectedId = null;
    post('drawingSelected', { id: null });
    persist();
    schedule();
  }

  function setLocked(locked) {
    var d = state.selectedId ? find(state.selectedId) : null;
    if (!d) return;
    pushHistory();
    d.locked = !!locked;
    persist();
    schedule();
  }

  function clear() {
    if (!state.drawings.length) return;
    pushHistory();
    state.drawings = [];
    state.selectedId = null;
    persist();
    schedule();
  }

  function updateText(id, text) {
    var d = find(id);
    if (!d) return;
    d.text = text;
    persist();
    schedule();
  }

  function getDrawings() {
    return state.drawings;
  }

  function setSeries(series) {
    if (!api) return;
    api.mainSeries = series;
    schedule();
  }

  return {
    init: init,
    setDrawings: setDrawings,
    setMode: setMode,
    setTool: setTool,
    undo: undo,
    redo: redo,
    deleteSelected: deleteSelected,
    setLocked: setLocked,
    clear: clear,
    updateText: updateText,
    getDrawings: getDrawings,
    setSeries: setSeries,
    __test: { distanceToSegment: distanceToSegment, fibLevels: fibLevels },
    __state: state,
  };
});
