/* ChartDesk indicator math + registry. Pure functions; no DOM. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ChartDeskIndicators = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function sma(values, period) {
    var out = [];
    var sum = 0;
    for (var i = 0; i < values.length; i++) {
      sum += values[i];
      if (i >= period) sum -= values[i - period];
      out.push(i >= period - 1 ? sum / period : null);
    }
    return out;
  }

  function ema(values, period) {
    var out = [];
    var k = 2 / (period + 1);
    var prev = null;
    for (var i = 0; i < values.length; i++) {
      if (i === period - 1) {
        var sum = 0;
        for (var j = 0; j < period; j++) sum += values[j];
        prev = sum / period;
        out.push(prev);
      } else if (i < period - 1) {
        out.push(null);
      } else {
        prev = values[i] * k + prev * (1 - k);
        out.push(prev);
      }
    }
    return out;
  }

  function wma(values, period) {
    var out = [];
    var denom = (period * (period + 1)) / 2;
    for (var i = 0; i < values.length; i++) {
      if (i < period - 1) {
        out.push(null);
        continue;
      }
      var sum = 0;
      for (var j = 0; j < period; j++) sum += values[i - period + 1 + j] * (j + 1);
      out.push(sum / denom);
    }
    return out;
  }

  function rsi(values, period) {
    var out = new Array(values.length).fill(null);
    if (values.length <= period) return out;
    var gain = 0;
    var loss = 0;
    for (var i = 1; i <= period; i++) {
      var diff = values[i] - values[i - 1];
      if (diff >= 0) gain += diff;
      else loss -= diff;
    }
    var avgGain = gain / period;
    var avgLoss = loss / period;
    out[period] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
    for (var k = period + 1; k < values.length; k++) {
      var d = values[k] - values[k - 1];
      var g = d > 0 ? d : 0;
      var l = d < 0 ? -d : 0;
      avgGain = (avgGain * (period - 1) + g) / period;
      avgLoss = (avgLoss * (period - 1) + l) / period;
      out[k] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
    }
    return out;
  }

  function macd(values, fast, slow, signalPeriod) {
    var fastEma = ema(values, fast);
    var slowEma = ema(values, slow);
    var macdLine = values.map(function (_, i) {
      return fastEma[i] === null || slowEma[i] === null ? null : fastEma[i] - slowEma[i];
    });
    var valid = macdLine.filter(function (v) { return v !== null; });
    var signalValid = ema(valid, signalPeriod);
    var signal = new Array(values.length).fill(null);
    var offset = values.length - signalValid.length;
    for (var i = 0; i < signalValid.length; i++) signal[offset + i] = signalValid[i];
    var hist = values.map(function (_, i) {
      return macdLine[i] === null || signal[i] === null ? null : macdLine[i] - signal[i];
    });
    return { macd: macdLine, signal: signal, hist: hist };
  }

  function bollinger(values, period, mult) {
    var mid = sma(values, period);
    var upper = [];
    var lower = [];
    for (var i = 0; i < values.length; i++) {
      if (mid[i] === null) {
        upper.push(null);
        lower.push(null);
        continue;
      }
      var variance = 0;
      for (var j = i - period + 1; j <= i; j++) {
        var d = values[j] - mid[i];
        variance += d * d;
      }
      var sd = Math.sqrt(variance / period);
      upper.push(mid[i] + sd * mult);
      lower.push(mid[i] - sd * mult);
    }
    return { mid: mid, upper: upper, lower: lower };
  }

  function trueRanges(candles) {
    return candles.map(function (c, i) {
      if (i === 0) return c.high - c.low;
      var prev = candles[i - 1].close;
      return Math.max(c.high - c.low, Math.abs(c.high - prev), Math.abs(c.low - prev));
    });
  }

  function atr(candles, period) {
    var tr = trueRanges(candles);
    var out = new Array(candles.length).fill(null);
    if (candles.length < period) return out;
    var sum = 0;
    for (var i = 0; i < period; i++) sum += tr[i];
    var prev = sum / period;
    out[period - 1] = prev;
    for (var k = period; k < candles.length; k++) {
      prev = (prev * (period - 1) + tr[k]) / period;
      out[k] = prev;
    }
    return out;
  }

  function stochastic(candles, kPeriod, dPeriod) {
    var kLine = new Array(candles.length).fill(null);
    for (var i = kPeriod - 1; i < candles.length; i++) {
      var high = -Infinity;
      var low = Infinity;
      for (var j = i - kPeriod + 1; j <= i; j++) {
        high = Math.max(high, candles[j].high);
        low = Math.min(low, candles[j].low);
      }
      var range = high - low;
      kLine[i] = range === 0 ? 50 : ((candles[i].close - low) / range) * 100;
    }
    var valid = kLine.filter(function (v) { return v !== null; });
    var dValid = sma(valid, dPeriod);
    var dLine = new Array(candles.length).fill(null);
    var offset = candles.length - dValid.length;
    for (var n = 0; n < dValid.length; n++) dLine[offset + n] = dValid[n];
    return { k: kLine, d: dLine };
  }

  function vwap(candles) {
    var out = [];
    var cumPv = 0;
    var cumVol = 0;
    var day = null;
    for (var i = 0; i < candles.length; i++) {
      var c = candles[i];
      var currentDay = Math.floor(c.time / 86400);
      if (currentDay !== day) {
        day = currentDay;
        cumPv = 0;
        cumVol = 0;
      }
      var typical = (c.high + c.low + c.close) / 3;
      cumPv += typical * (c.volume || 0);
      cumVol += c.volume || 0;
      out.push(cumVol === 0 ? null : cumPv / cumVol);
    }
    return out;
  }

  function ichimoku(candles, tenkanP, kijunP, senkouP) {
    function mid(period) {
      var out = new Array(candles.length).fill(null);
      for (var i = period - 1; i < candles.length; i++) {
        var high = -Infinity;
        var low = Infinity;
        for (var j = i - period + 1; j <= i; j++) {
          high = Math.max(high, candles[j].high);
          low = Math.min(low, candles[j].low);
        }
        out[i] = (high + low) / 2;
      }
      return out;
    }
    var tenkan = mid(tenkanP);
    var kijun = mid(kijunP);
    var senkouA = new Array(candles.length).fill(null);
    var senkouB = new Array(candles.length).fill(null);
    var chikou = new Array(candles.length).fill(null);
    for (var i = 0; i < candles.length; i++) {
      if (tenkan[i] !== null && kijun[i] !== null) senkouA[i] = (tenkan[i] + kijun[i]) / 2;
    }
    var rawB = mid(senkouP);
    for (var k = 0; k < candles.length; k++) {
      if (rawB[k] !== null) senkouB[k] = rawB[k];
      if (k + kijunP < candles.length) chikou[k] = candles[k + kijunP].close;
    }
    return { tenkan: tenkan, kijun: kijun, senkouA: senkouA, senkouB: senkouB, chikou: chikou };
  }

  function superTrend(candles, period, multiplier) {
    var atrValues = atr(candles, period);
    var line = new Array(candles.length).fill(null);
    var direction = new Array(candles.length).fill(null);
    var upper = null;
    var lower = null;
    var dir = 1;
    for (var i = 0; i < candles.length; i++) {
      if (atrValues[i] === null) continue;
      var mid = (candles[i].high + candles[i].low) / 2;
      var basicUpper = mid + multiplier * atrValues[i];
      var basicLower = mid - multiplier * atrValues[i];
      var prevClose = i > 0 ? candles[i - 1].close : candles[i].close;
      upper = upper === null ? basicUpper : (basicUpper < upper || prevClose > upper ? basicUpper : upper);
      lower = lower === null ? basicLower : (basicLower > lower || prevClose < lower ? basicLower : lower);
      if (line[i - 1] === null || line[i - 1] === undefined) {
        dir = candles[i].close >= mid ? 1 : -1;
      } else if (line[i - 1] === upper) {
        dir = candles[i].close > upper ? 1 : -1;
      } else {
        dir = candles[i].close < lower ? -1 : 1;
      }
      line[i] = dir === 1 ? lower : upper;
      direction[i] = dir;
    }
    return { line: line, direction: direction };
  }

  function closes(candles) { return candles.map(function (c) { return c.close; }); }

  function series(candles, values) {
    var out = [];
    for (var i = 0; i < candles.length; i++) {
      if (values[i] !== null && values[i] !== undefined && isFinite(values[i])) {
        out.push({ time: candles[i].time, value: values[i] });
      }
    }
    return out;
  }

  var registry = {
    ma: {
      name: 'MA', pane: 'overlay', defaults: { period: 20 },
      compute: function (candles, p) {
        return { plots: [{ key: 'ma', label: 'MA ' + p.period, color: '#4C9BFF', data: series(candles, sma(closes(candles), p.period)) }] };
      },
    },
    ema: {
      name: 'EMA', pane: 'overlay', defaults: { period: 20 },
      compute: function (candles, p) {
        return { plots: [{ key: 'ema', label: 'EMA ' + p.period, color: '#F5B942', data: series(candles, ema(closes(candles), p.period)) }] };
      },
    },
    wma: {
      name: 'WMA', pane: 'overlay', defaults: { period: 20 },
      compute: function (candles, p) {
        return { plots: [{ key: 'wma', label: 'WMA ' + p.period, color: '#C084FC', data: series(candles, wma(closes(candles), p.period)) }] };
      },
    },
    bollinger: {
      name: 'Bollinger', pane: 'overlay', defaults: { period: 20, mult: 2 },
      compute: function (candles, p) {
        var b = bollinger(closes(candles), p.period, p.mult);
        return {
          plots: [
            { key: 'upper', label: 'BB upper', color: '#8A99AD', data: series(candles, b.upper) },
            { key: 'mid', label: 'BB basis', color: '#4C9BFF', data: series(candles, b.mid) },
            { key: 'lower', label: 'BB lower', color: '#8A99AD', data: series(candles, b.lower) },
          ],
        };
      },
    },
    vwap: {
      name: 'VWAP', pane: 'overlay', defaults: {},
      compute: function (candles) {
        return { plots: [{ key: 'vwap', label: 'VWAP', color: '#14C7A5', data: series(candles, vwap(candles)) }] };
      },
    },
    ichimoku: {
      name: 'Ichimoku', pane: 'overlay', defaults: { tenkan: 9, kijun: 26, senkou: 52 },
      compute: function (candles, p) {
        var i = ichimoku(candles, p.tenkan, p.kijun, p.senkou);
        return {
          plots: [
            { key: 'tenkan', label: 'Tenkan', color: '#FF9F1C', data: series(candles, i.tenkan) },
            { key: 'kijun', label: 'Kijun', color: '#4C9BFF', data: series(candles, i.kijun) },
            { key: 'senkouA', label: 'Senkou A', color: '#2ED47A', data: series(candles, i.senkouA) },
            { key: 'senkouB', label: 'Senkou B', color: '#FF5C7A', data: series(candles, i.senkouB) },
          ],
        };
      },
    },
    supertrend: {
      name: 'SuperTrend', pane: 'overlay', defaults: { period: 10, mult: 3 },
      compute: function (candles, p) {
        var st = superTrend(candles, p.period, p.mult);
        return { plots: [{ key: 'st', label: 'SuperTrend', color: '#2ED47A', data: series(candles, st.line) }], meta: { direction: st.direction } };
      },
    },
    volume: {
      name: 'Volume', pane: 'volume', defaults: {},
      compute: function (candles) {
        return { plots: [{ key: 'vol', label: 'Volume', color: '#8A99AD', data: candles.map(function (c) { return { time: c.time, value: c.volume || 0 }; }) }] };
      },
    },
    rsi: {
      name: 'RSI', pane: 'separate', defaults: { period: 14 },
      compute: function (candles, p) {
        return {
          plots: [{ key: 'rsi', label: 'RSI ' + p.period, color: '#C084FC', data: series(candles, rsi(closes(candles), p.period)) }],
          hlines: [{ price: 70, color: '#FF5C7A' }, { price: 30, color: '#2ED47A' }],
        };
      },
    },
    macd: {
      name: 'MACD', pane: 'separate', defaults: { fast: 12, slow: 26, signal: 9 },
      compute: function (candles, p) {
        var m = macd(closes(candles), p.fast, p.slow, p.signal);
        return {
          plots: [
            { key: 'macd', label: 'MACD', color: '#4C9BFF', data: series(candles, m.macd) },
            { key: 'signal', label: 'Signal', color: '#F5B942', data: series(candles, m.signal) },
          ],
          histogram: series(candles, m.hist),
        };
      },
    },
    atr: {
      name: 'ATR', pane: 'separate', defaults: { period: 14 },
      compute: function (candles, p) {
        return { plots: [{ key: 'atr', label: 'ATR ' + p.period, color: '#FF9F1C', data: series(candles, atr(candles, p.period)) }] };
      },
    },
    stochastic: {
      name: 'Stochastic', pane: 'separate', defaults: { k: 14, d: 3 },
      compute: function (candles, p) {
        var s = stochastic(candles, p.k, p.d);
        return {
          plots: [
            { key: 'k', label: '%K', color: '#4C9BFF', data: series(candles, s.k) },
            { key: 'd', label: '%D', color: '#F5B942', data: series(candles, s.d) },
          ],
          hlines: [{ price: 80, color: '#FF5C7A' }, { price: 20, color: '#2ED47A' }],
        };
      },
    },
  };

  function compute(id, candles, params) {
    var def = registry[id];
    if (!def) throw new Error('Unknown indicator: ' + id);
    var merged = Object.assign({}, def.defaults, params || {});
    return def.compute(candles, merged);
  }

  return {
    registry: registry,
    compute: compute,
    math: {
      sma: sma, ema: ema, wma: wma, rsi: rsi, macd: macd, bollinger: bollinger,
      atr: atr, stochastic: stochastic, vwap: vwap, ichimoku: ichimoku, superTrend: superTrend,
    },
  };
});
