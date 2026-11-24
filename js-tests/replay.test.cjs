const test = require('node:test');
const assert = require('node:assert');
const replay = require('../app/src/main/assets/chart/replay.js');

test('clampIndex keeps the cursor inside the series', () => {
  assert.strictEqual(replay.clampIndex(-5, 100), 0);
  assert.strictEqual(replay.clampIndex(500, 100), 99);
  assert.strictEqual(replay.clampIndex(42.7, 100), 42);
  assert.strictEqual(replay.clampIndex(5, 0), 0);
});

test('step walks forward and backward without escaping', () => {
  assert.strictEqual(replay.step(10, 100, 1), 11);
  assert.strictEqual(replay.step(0, 100, -1), 0);
  assert.strictEqual(replay.step(99, 100, 1), 99);
});

test('speed maps to bar interval', () => {
  assert.strictEqual(replay.intervalMs(1), 1000);
  assert.strictEqual(replay.intervalMs(2), 500);
  assert.strictEqual(replay.intervalMs(0.5), 2000);
  assert.strictEqual(replay.intervalMs(99), 1000);
});

test('speed cycles through the supported set', () => {
  assert.strictEqual(replay.nextSpeed(0.5), 1);
  assert.strictEqual(replay.nextSpeed(1), 2);
  assert.strictEqual(replay.nextSpeed(5), 0.5);
});

test('pnl is signed by side', () => {
  assert.strictEqual(replay.pnl('long', 100, 110), 10);
  assert.strictEqual(replay.pnl('short', 100, 110), -10);
  assert.strictEqual(replay.pnl(null, 100, 110), 0);
  assert.ok(Math.abs(replay.pnlPercent('long', 100, 110) - 10) < 1e-9);
});

test('buy opens long and sell closes it', () => {
  const long = replay.trade(null, 'buy', 100);
  assert.strictEqual(long.side, 'long');
  assert.strictEqual(replay.trade(long, 'sell', 105), null);
  assert.strictEqual(replay.trade(long, 'buy', 105).side, 'long');
});

test('sell opens short and buy closes it', () => {
  const short = replay.trade(null, 'sell', 100);
  assert.strictEqual(short.side, 'short');
  assert.strictEqual(replay.trade(short, 'buy', 95), null);
});

test('summary reports live pnl', () => {
  const position = { side: 'long', entry: 100, qty: 1 };
  const s = replay.summary(position, 103);
  assert.strictEqual(s.side, 'long');
  assert.strictEqual(s.pnl, 3);
  assert.ok(Math.abs(s.pnlPercent - 3) < 1e-9);
  assert.deepStrictEqual(replay.summary(null, 100), { side: null, entry: 0, pnl: 0, pnlPercent: 0 });
});
