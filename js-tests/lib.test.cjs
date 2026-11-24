const test = require('node:test');
const assert = require('node:assert');
const lib = require('../app/src/main/assets/chart/lib.js');

test('heikin ashi keeps the first candle average of open and close', () => {
  const src = [{ time: 60, open: 10, high: 12, low: 9, close: 11, volume: 3 }];
  const out = lib.heikinAshi(src);
  assert.strictEqual(out.length, 1);
  assert.strictEqual(out[0].open, 10.5);
  assert.strictEqual(out[0].close, 10.5);
  assert.strictEqual(out[0].high, 12);
  assert.strictEqual(out[0].low, 9);
});

test('heikin ashi smooths a second candle from the previous HA bar', () => {
  const src = [
    { time: 60, open: 10, high: 12, low: 9, close: 11, volume: 1 },
    { time: 120, open: 11, high: 14, low: 10, close: 13, volume: 1 },
  ];
  const out = lib.heikinAshi(src);
  assert.strictEqual(out[1].close, 12);
  assert.strictEqual(out[1].open, 10.5);
  assert.strictEqual(out[1].high, 14);
  assert.strictEqual(out[1].low, 10);
});

test('upsert replaces the current bucket and appends new ones', () => {
  const base = [{ time: 60, open: 1, high: 2, low: 0.5, close: 1.5, volume: 1 }];
  const replaced = lib.upsertCandle(base, { time: 60, open: 1, high: 3, low: 0.5, close: 2.5, volume: 2 });
  assert.strictEqual(replaced.length, 1);
  assert.strictEqual(replaced[0].close, 2.5);
  const appended = lib.upsertCandle(replaced, { time: 120, open: 2.5, high: 4, low: 2, close: 3.5, volume: 1 });
  assert.strictEqual(appended.length, 2);
  assert.strictEqual(appended[1].time, 120);
});

test('upsert ignores late bars older than the window', () => {
  const base = [{ time: 120, open: 1, high: 2, low: 0.5, close: 1.5, volume: 1 }];
  const out = lib.upsertCandle(base, { time: 60, open: 1, high: 2, low: 0.5, close: 1.2, volume: 1 });
  assert.strictEqual(out.length, 1);
  assert.strictEqual(out[0].time, 120);
});

test('aggregate buckets by epoch-aligned seconds', () => {
  const rows = [
    { time: 0, open: 10, high: 11, low: 9, close: 10.5, volume: 1 },
    { time: 60, open: 10.5, high: 12, low: 10, close: 11.5, volume: 2 },
    { time: 300, open: 11.5, high: 13, low: 11, close: 12.5, volume: 4 },
  ];
  const out = lib.aggregate(rows, 300);
  assert.strictEqual(out.length, 2);
  assert.strictEqual(out[0].time, 0);
  assert.strictEqual(out[0].open, 10);
  assert.strictEqual(out[0].close, 11.5);
  assert.strictEqual(out[0].volume, 3);
  assert.strictEqual(out[1].time, 300);
});

test('price formatting matches the native rules', () => {
  assert.strictEqual(lib.decimalsForPrice(67400), 1);
  assert.strictEqual(lib.decimalsForPrice(172.5), 2);
  assert.strictEqual(lib.decimalsForPrice(1.234), 3);
  assert.strictEqual(lib.decimalsForPrice(0.0623), 5);
  assert.strictEqual(lib.formatPrice(0.6231, 5), '0.62310');
  assert.strictEqual(lib.formatPrice(NaN), '—');
});

test('compact volume and percent helpers', () => {
  assert.strictEqual(lib.formatCompact(1500), '1.50K');
  assert.strictEqual(lib.formatCompact(2_400_000), '2.40M');
  assert.strictEqual(lib.changePercent(100, 110), 10);
  assert.strictEqual(lib.changePercent(0, 10), 0);
});

test('timeframe map covers the UI labels', () => {
  assert.strictEqual(lib.timeframeSeconds('1m'), 60);
  assert.strictEqual(lib.timeframeSeconds('4h'), 14400);
  assert.strictEqual(lib.timeframeSeconds('1w'), 604800);
  assert.strictEqual(lib.timeframeSeconds('bogus'), 60);
});
