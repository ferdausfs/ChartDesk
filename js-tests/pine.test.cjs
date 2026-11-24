const test = require('node:test');
const assert = require('node:assert');
const pine = require('../app/src/main/assets/chart/pine.js');

function candles(n = 30) {
  return Array.from({ length: n }, (_, i) => ({
    time: 1_700_000_000 + i * 60,
    open: 100 + i,
    high: 101 + i,
    low: 99 + i,
    close: 100 + i,
    volume: 10 + i,
  }));
}

test('plots the close series', () => {
  const result = pine.run('indicator("x")\nplot(close, title="c")', candles());
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots.length, 1);
  assert.strictEqual(result.plots[0].data.length, 30);
  assert.strictEqual(result.plots[0].data[0].value, 100);
});

test('ta.sma needs a full window before producing values', () => {
  const result = pine.run('plot(ta.sma(close, 5), title="ma")', candles());
  assert.strictEqual(result.ok, true);
  const data = result.plots[0].data;
  assert.strictEqual(data.length, 26);
  assert.ok(Math.abs(data[0].value - 102) < 1e-9);
});

test('var initialises once and := updates per bar', () => {
  const result = pine.run('var counter = 0\ncounter := counter + 1\nplot(counter, title="n")', candles(5));
  assert.strictEqual(result.ok, true);
  assert.deepStrictEqual(result.plots[0].data.map((p) => p.value), [1, 2, 3, 4, 5]);
});

test('if/else statements branch per bar', () => {
  const code = [
    'x = 0',
    'if close > 105',
    '    x := 1',
    'else',
    '    x := 2',
    'plot(x, title="x")',
  ].join('\n');
  const result = pine.run(code, candles(10));
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots[0].data[0].value, 2);
  assert.strictEqual(result.plots[0].data[9].value, 1);
});

test('for loops accumulate', () => {
  const code = [
    'total = 0',
    'for i = 1 to 4',
    '    total := total + i',
    'plot(total, title="t")',
  ].join('\n');
  const result = pine.run(code, candles(3));
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots[0].data[0].value, 10);
});

test('inputs fall back to their default values', () => {
  const result = pine.run('len = input.int(7, title="Length")\nplot(ta.sma(close, len), title="s")', candles());
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots[0].data.length, 24);
});

test('plotshape records conditions', () => {
  const result = pine.run('plotshape(close > 120, title="hi", color=color.green)', candles());
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.shapes.length, 1);
  assert.strictEqual(result.shapes[0].data.length, 9);
});

test('hline records static levels', () => {
  const result = pine.run('hline(100, color=color.gray)', candles());
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.hlines[0].price, 100);
});

test('math and na helpers work', () => {
  const result = pine.run('plot(math.max(math.abs(-5), math.sqrt(16)), title="m")', candles(2));
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots[0].data[0].value, 5);
  // Booleans are plotted as 0/1 by the line renderer.
  assert.strictEqual(pine.run('plot(na(close), title="n")', candles(2)).plots[0].data[0].value, 0);
});

test('arrays support new/push/get/size', () => {
  const code = [
    'a = array.new_float(0)',
    'array.push(a, 3)',
    'array.push(a, 4)',
    'plot(array.get(a, 0) + array.size(a), title="arr")',
  ].join('\n');
  const result = pine.run(code, candles(2));
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots[0].data[0].value, 5);
});

test('request.security evaluates on the chart series and warns', () => {
  const result = pine.run('plot(request.security("BTCUSDT", "1h", close), title="sec")', candles(3));
  assert.strictEqual(result.ok, true);
  assert.ok(result.warnings.length > 0);
});

test('unknown identifiers fail with a readable error', () => {
  const result = pine.run('plot(doesNotExist)', candles(2));
  assert.strictEqual(result.ok, false);
  assert.match(result.errors[0].message, /Unknown identifier/);
});

test('unsupported functions fail, not crash', () => {
  const result = pine.run('strategy.entry("long", strategy.long)', candles(2));
  assert.strictEqual(result.ok, false);
  assert.match(result.errors[0].message, /Unsupported function/);
});

test('syntax errors carry a line number', () => {
  const result = pine.run('plot(close', candles(2));
  assert.strictEqual(result.ok, false);
  assert.ok(result.errors[0].line >= 1);
});

test('empty script runs without output', () => {
  const result = pine.run('', candles(2));
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.plots.length, 0);
});
