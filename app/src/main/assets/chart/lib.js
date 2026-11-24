/* ChartDesk pure chart helpers. No DOM access: safe to unit test under Node. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ChartDeskLib = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function heikinAshi(candles) {
    var out = [];
    var prevOpen = null;
    var prevClose = null;
    for (var i = 0; i < candles.length; i++) {
      var c = candles[i];
      var close = (c.open + c.high + c.low + c.close) / 4;
      var open = prevOpen === null ? (c.open + c.close) / 2 : (prevOpen + prevClose) / 2;
      var high = Math.max(c.high, open, close);
      var low = Math.min(c.low, open, close);
      out.push({ time: c.time, open: open, high: high, low: low, close: close, volume: c.volume || 0 });
      prevOpen = open;
      prevClose = close;
    }
    return out;
  }

  function decimalsForPrice(price) {
    if (!isFinite(price) || price <= 0) return 2;
    if (price >= 10000) return 1;
    if (price >= 100) return 2;
    if (price >= 1) return 3;
    if (price >= 0.01) return 5;
    return 8;
  }

  function formatPrice(value, decimals) {
    if (value === null || value === undefined || !isFinite(value)) return '—';
    var d = typeof decimals === 'number' ? decimals : decimalsForPrice(value);
    return Number(value).toFixed(d);
  }

  function formatCompact(value) {
    if (!isFinite(value)) return '—';
    var abs = Math.abs(value);
    if (abs >= 1e9) return (value / 1e9).toFixed(2) + 'B';
    if (abs >= 1e6) return (value / 1e6).toFixed(2) + 'M';
    if (abs >= 1e3) return (value / 1e3).toFixed(2) + 'K';
    return value.toFixed(2);
  }

  function changePercent(open, close) {
    if (!open) return 0;
    return ((close - open) / open) * 100;
  }

  /** Merge a partial live candle into a sorted series. Returns a new array. */
  function upsertCandle(candles, candle) {
    if (!candles.length || candle.time > candles[candles.length - 1].time) {
      return candles.concat([candle]);
    }
    var last = candles[candles.length - 1];
    if (candle.time === last.time) {
      var copy = candles.slice(0, -1);
      copy.push(candle);
      return copy;
    }
    // Out-of-order update (late trade): replace matching bucket.
    for (var i = candles.length - 1; i >= 0; i--) {
      if (candles[i].time === candle.time) {
        var next = candles.slice();
        next[i] = candle;
        return next;
      }
      if (candles[i].time < candle.time) break;
    }
    return candles;
  }

  /** Aggregate 1m candles into a higher timeframe bucket (seconds). */
  function aggregate(candles, bucketSeconds) {
    if (!candles.length) return [];
    var out = [];
    var bucketStart = null;
    var current = null;
    for (var i = 0; i < candles.length; i++) {
      var c = candles[i];
      var start = Math.floor(c.time / bucketSeconds) * bucketSeconds;
      if (start !== bucketStart) {
        if (current) out.push(current);
        bucketStart = start;
        current = { time: start, open: c.open, high: c.high, low: c.low, close: c.close, volume: c.volume || 0 };
      } else {
        current.high = Math.max(current.high, c.high);
        current.low = Math.min(current.low, c.low);
        current.close = c.close;
        current.volume += c.volume || 0;
      }
    }
    if (current) out.push(current);
    return out;
  }

  function timeframeSeconds(tf) {
    var map = { '1m': 60, '5m': 300, '15m': 900, '30m': 1800, '1h': 3600, '4h': 14400, '1d': 86400, '1w': 604800, '1M': 2592000 };
    return map[tf] || 60;
  }

  return {
    clamp: clamp,
    heikinAshi: heikinAshi,
    decimalsForPrice: decimalsForPrice,
    formatPrice: formatPrice,
    formatCompact: formatCompact,
    changePercent: changePercent,
    upsertCandle: upsertCandle,
    aggregate: aggregate,
    timeframeSeconds: timeframeSeconds,
  };
});
