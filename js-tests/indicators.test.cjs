const test = require('node:test');
const assert = require('node:assert');
const ind = require('../app/src/main/assets/chart/indicators.js');
const m = ind.math;

function candlesFromCloses(closes, volume = 10) {
  return closes.map((close, i) => ({
    time: 1_700_000_000 + i * 60,
    open: close - 0.5,
    high: close + 1,
    low: close - 1,
    close,
    volume,
  }));
}

test('sma matches the hand calculation', () => {
  assert.deepStrictEqual(m.sma([1, 2, 3, 4, 5], 3), [null, null, 2, 3, 4]);
});

test('ema seeds with sma and then smooths', () => {
  const out = m.ema([1, 2, 3, 4, 5], 3);
  assert.strictEqual(out[0], null);
  assert.strictEqual(out[2], 2);
  assert.ok(Math.abs(out[3] - 3) < 1e-9);
  assert.ok(Math.abs(out[4] - 4) < 1e-9);
});

test('wma weights the newest value most', () => {
  const out = m.wma([1, 2, 3, 4, 5], 3);
  assert.ok(Math.abs(out[2] - 14 / 6) < 1e-9);
  assert.ok(Math.abs(out[4] - 26 / 6) < 1e-9);
});

test('rsi is 100 on a monotonic rise', () => {
  const out = m.rsi([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], 14);
  assert.strictEqual(out[14], 100);
});

test('rsi stays inside 0..100 on a mixed series', () => {
  const values = [10, 11, 10.5, 12, 11, 13, 12.5, 14, 13, 15, 14.5, 16, 15, 17, 16, 18];
  const out = m.rsi(values, 14);
  out.filter((v) => v !== null).forEach((v) => assert.ok(v >= 0 && v <= 100, 'rsi out of range: ' + v));
});

test('macd is positive while the fast ema leads', () => {
  const closes = Array.from({ length: 60 }, (_, i) => 100 + i);
  const out = m.macd(closes, 12, 26, 9);
  const last = out.macd[out.macd.length - 1];
  assert.ok(last > 0);
  assert.ok(Math.abs(out.hist[out.hist.length - 1] - (last - out.signal[out.signal.length - 1])) < 1e-9);
});

test('bollinger collapses to the mean on a flat series', () => {
  const out = m.bollinger(new Array(30).fill(10), 20, 2);
  assert.strictEqual(out.mid[25], 10);
  assert.strictEqual(out.upper[25], 10);
  assert.strictEqual(out.lower[25], 10);
});

test('atr equals the constant true range', () => {
  const candles = candlesFromCloses(new Array(20).fill(10));
  const out = m.atr(candles, 14);
  assert.ok(Math.abs(out[19] - 2) < 1e-9);
});

test('stochastic hits 100 when closes sit at the highs', () => {
  const candles = candlesFromCloses(Array.from({ length: 20 }, (_, i) => 10 + i));
  candles.forEach((c) => { c.high = c.close; c.low = c.close - 2; });
  const out = m.stochastic(candles, 14, 3);
  assert.strictEqual(out.k[19], 100);
});

test('vwap uses typical price and volume', () => {
  const candles = [
    { time: 0, open: 10, high: 12, low: 8, close: 10, volume: 10 },
    { time: 60, open: 10, high: 13, low: 9, close: 11, volume: 30 },
  ];
  const out = m.vwap(candles);
  const first = (12 + 8 + 10) / 3;
  const second = (13 + 9 + 11) / 3;
  assert.ok(Math.abs(out[0] - first) < 1e-9);
  assert.ok(Math.abs(out[1] - (first * 10 + second * 30) / 40) < 1e-9);
});

test('vwap resets at the utc day boundary', () => {
  const candles = [
    { time: 0, open: 10, high: 10, low: 10, close: 10, volume: 10 },
    { time: 86400, open: 20, high: 20, low: 20, close: 20, volume: 10 },
  ];
  const out = m.vwap(candles);
  assert.strictEqual(out[1], 20);
});

test('ichimoku lines all equal the flat price', () => {
  const candles = candlesFromCloses(new Array(80).fill(10));
  const out = m.ichimoku(candles, 9, 26, 52);
  assert.strictEqual(out.tenkan[79], 10);
  assert.strictEqual(out.kijun[79], 10);
  assert.strictEqual(out.senkouA[79], 10);
});

test('supertrend sits below price in an uptrend', () => {
  const candles = candlesFromCloses(Array.from({ length: 60 }, (_, i) => 100 + i * 2));
  const out = m.superTrend(candles, 10, 3);
  const last = out.line[59];
  assert.ok(last < candles[59].close);
  assert.strictEqual(out.direction[59], 1);
});

test('registry computes plots with timestamps for every indicator', () => {
  const candles = candlesFromCloses(Array.from({ length: 120 }, (_, i) => 100 + Math.sin(i / 5) * 3));
  Object.keys(ind.registry).forEach((id) => {
    const result = ind.compute(id, candles, {});
    assert.ok(Array.isArray(result.plots) && result.plots.length > 0, id + ' returned no plots');
    result.plots.forEach((plot) => {
      assert.ok(Array.isArray(plot.data), id + ' plot data missing');
      if (plot.data.length > 0) {
        assert.ok(typeof plot.data[0].time === 'number');
        assert.ok(isFinite(plot.data[0].value));
      }
    });
  });
});

test('unknown indicator ids fail loudly', () => {
  assert.throws(() => ind.compute('nope', [], {}), /Unknown indicator/);
});
