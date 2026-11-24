const test = require('node:test');
const assert = require('node:assert');
const drawings = require('../app/src/main/assets/chart/drawings.js');
const t = drawings.__test;

test('distance to a segment is zero on the line', () => {
  assert.strictEqual(t.distanceToSegment(5, 5, 0, 0, 10, 10), 0);
});

test('distance to a segment clamps to the nearest endpoint', () => {
  const d = t.distanceToSegment(-3, 4, 0, 0, 10, 0);
  assert.strictEqual(d, 5);
});

test('distance to a degenerate segment is point distance', () => {
  assert.strictEqual(t.distanceToSegment(3, 4, 0, 0, 0, 0), 5);
});

test('fib levels cover the canonical retracement set', () => {
  assert.deepStrictEqual(t.fibLevels(), [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1]);
});
