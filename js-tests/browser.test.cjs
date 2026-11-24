const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const puppeteer = require('puppeteer-core');

const PAGE = path.resolve(__dirname, '..', 'app', 'src', 'main', 'assets', 'chart', 'index.html');

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    '/snap/bin/chromium',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium-browser',
    '/usr/bin/chromium',
  ].filter(Boolean);
  const found = candidates.find((p) => fs.existsSync(p));
  if (!found) throw new Error('No Chrome/Chromium binary found; set CHROME_PATH');
  return found;
}

function candleSeries(count) {
  const start = 1_700_000_000;
  let price = 100;
  const rows = [];
  for (let i = 0; i < count; i++) {
    const open = price;
    const close = open + Math.sin(i / 3) * 0.8 + 0.15;
    const high = Math.max(open, close) + 0.4;
    const low = Math.min(open, close) - 0.4;
    rows.push([start + i * 60, open, high, low, close, 10 + i]);
    price = close;
  }
  return rows;
}

test('chart page boots and applies bridge commands without errors', async (t) => {
  const browser = await puppeteer.launch({
    executablePath: chromePath(),
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--allow-file-access-from-files', '--force-device-scale-factor=1'],
    defaultViewport: { width: 420, height: 720, deviceScaleFactor: 1 },
  });
  t.after(() => browser.close());

  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push('console: ' + msg.text());
  });

  await page.goto('file://' + PAGE, { waitUntil: 'load' });
  await page.waitForFunction('window.ChartDesk && window.ChartDesk.state && window.ChartDesk.state.chart');

  assert.strictEqual(await page.$eval('#attribution', (el) => el.textContent.includes('TradingView')), true);

  const rows = candleSeries(80);
  await page.evaluate((candles) => {
    window.ChartDesk.handle(JSON.stringify({
      type: 'setCandles',
      payload: { symbol: 'BTCUSDT', timeframe: '1m', precision: 2, candles },
    }));
  }, rows);

  await page.waitForFunction('window.ChartDesk.state.raw.length === 80');
  assert.strictEqual(await page.$eval('#legend-symbol', (el) => el.textContent), 'BTCUSDT');
  assert.strictEqual(await page.$eval('#skeleton', (el) => el.classList.contains('hidden')), true);
  assert.ok((await page.$$('canvas')).length > 0, 'lightweight-charts canvas should exist');

  for (const type of ['line', 'area', 'bar', 'heikin', 'candle']) {
    await page.evaluate((value) => {
      window.ChartDesk.handle(JSON.stringify({ type: 'setChartType', payload: { type: value } }));
    }, type);
    assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.type), type);
  }

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'setPriceScale', payload: { mode: 'log' } }));
  });
  assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.scaleMode), 'log');

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'setTheme', payload: { dark: false, colorBlind: true } }));
  });
  assert.strictEqual(await page.evaluate(() => document.documentElement.getAttribute('data-theme')), 'light');
  assert.strictEqual(await page.evaluate(() => document.documentElement.getAttribute('data-cb')), 'true');

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'updateCandle', payload: { candle: [1700004800, 1, 2, 0.5, 1.5, 4] } }));
  });
  await page.waitForFunction('window.ChartDesk.state.raw[window.ChartDesk.state.raw.length - 1].time === 1700004800');

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({
      type: 'setIndicators',
      payload: {
        list: [
          { uid: 'ma', type: 'ma', params: { period: 20 }, visible: true },
          { uid: 'rsi', type: 'rsi', params: { period: 14 }, visible: true },
          { uid: 'macd', type: 'macd', params: { fast: 12, slow: 26, signal: 9 }, visible: true },
        ],
      },
    }));
  });
  await page.waitForFunction("Object.keys(window.ChartDesk.state.panes).length === 2");
  assert.strictEqual(await page.evaluate(() => document.querySelectorAll('.pane').length), 2);
  assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.indicators.length), 3);

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'setIndicators', payload: { list: [] } }));
  });
  await page.waitForFunction("Object.keys(window.ChartDesk.state.panes).length === 0");
  assert.strictEqual(await page.evaluate(() => document.querySelectorAll('.pane').length), 0);

  // Drawing layer: draw a trendline with two taps, then undo/redo it.
  await page.evaluate(() => window.ChartDeskDrawings.setTool('trendline'));
  const box = await page.$eval('#drawings-canvas', (el) => {
    const r = el.getBoundingClientRect();
    return { x: r.x, y: r.y, w: r.width, h: r.height };
  });
  await page.mouse.click(box.x + box.w * 0.25, box.y + box.h * 0.3);
  await page.mouse.click(box.x + box.w * 0.55, box.y + box.h * 0.6);
  await page.waitForFunction('window.ChartDeskDrawings.__state.drawings.length === 1');
  assert.strictEqual(await page.evaluate(() => window.ChartDeskDrawings.getDrawings()[0].type), 'trendline');

  await page.evaluate(() => window.ChartDeskDrawings.undo());
  await page.waitForFunction('window.ChartDeskDrawings.__state.drawings.length === 0');
  await page.evaluate(() => window.ChartDeskDrawings.redo());
  await page.waitForFunction('window.ChartDeskDrawings.__state.drawings.length === 1');

  await page.evaluate(() => {
    window.ChartDeskDrawings.setDrawings([
      { id: 'x1', type: 'hline', points: [{ time: null, price: 100 }], locked: true },
      { id: 'x2', type: 'rect', points: [{ time: 1700000000, price: 90 }, { time: 1700003000, price: 110 }], locked: false },
    ]);
  });
  assert.strictEqual(await page.evaluate(() => window.ChartDeskDrawings.__state.drawings.length), 2);

  // Bar replay: start at bar 20, step, paper trade, stop.
  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'replayStart', payload: { index: 20 } }));
  });
  await page.waitForFunction('window.ChartDesk.state.replay.active === true');
  assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.replay.index), 20);

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'replayStep', payload: { delta: 1 } }));
  });
  assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.replay.index), 21);

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'replayTrade', payload: { action: 'buy' } }));
  });
  assert.strictEqual(await page.evaluate(() => window.ChartDesk.state.replay.position.side), 'long');

  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'replayStop' }));
  });
  await page.waitForFunction('window.ChartDesk.state.replay.active === false');

  // Pine subset runs on the live chart and adds an overlay series.
  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({
      type: 'runPine',
      payload: { code: 'indicator("test")\nplot(ta.sma(close, 5), title="ma", color=color.green)' },
    }));
  });
  await page.waitForFunction('window.ChartDesk.state.pine.series.length === 1');
  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'clearPine' }));
  });
  await page.waitForFunction('window.ChartDesk.state.pine.series.length === 0');

  // Unknown commands must be ignored, not thrown.
  await page.evaluate(() => {
    window.ChartDesk.handle(JSON.stringify({ type: 'no-such-command', payload: {} }));
  });
  assert.strictEqual(await page.evaluate(() => typeof window.ChartDesk.handle), 'function');

  assert.deepStrictEqual(errors, [], 'chart page should have no JS errors');
});
