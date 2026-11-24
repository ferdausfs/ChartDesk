/* ChartDesk replay engine helpers: pure state maths, no DOM. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ChartDeskReplay = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var SPEEDS = [0.5, 1, 2, 5];

  function clampIndex(index, total) {
    if (!total || total <= 0) return 0;
    if (!isFinite(index)) return 0;
    return Math.max(0, Math.min(total - 1, Math.floor(index)));
  }

  function step(index, total, delta) {
    return clampIndex(index + (delta || 1), total);
  }

  /** speed = bars per second. */
  function intervalMs(speed) {
    var s = SPEEDS.indexOf(speed) >= 0 ? speed : 1;
    return Math.round(1000 / s);
  }

  function nextSpeed(speed) {
    var i = SPEEDS.indexOf(speed);
    return SPEEDS[(i + 1) % SPEEDS.length];
  }

  function pnl(side, entry, price, qty) {
    if (!side || !isFinite(entry) || !isFinite(price)) return 0;
    var q = isFinite(qty) && qty > 0 ? qty : 1;
    return (price - entry) * q * (side === 'long' ? 1 : -1);
  }

  function pnlPercent(side, entry, price) {
    if (!side || !entry) return 0;
    return (pnl(side, entry, price) / entry) * 100;
  }

  /**
   * Paper trade state machine.
   * buy  -> open long, or close an existing short
   * sell -> open short, or close an existing long
   * close -> flat
   */
  function trade(position, action, price) {
    if (!isFinite(price) || price <= 0) return position || null;
    if (action === 'close') return null;
    if (!position) {
      return { side: action === 'buy' ? 'long' : 'short', entry: price, qty: 1 };
    }
    if (action === 'buy' && position.side === 'short') return null;
    if (action === 'sell' && position.side === 'long') return null;
    return position;
  }

  function summary(position, price) {
    if (!position) return { side: null, entry: 0, pnl: 0, pnlPercent: 0 };
    return {
      side: position.side,
      entry: position.entry,
      pnl: pnl(position.side, position.entry, price),
      pnlPercent: pnlPercent(position.side, position.entry, price),
    };
  }

  return {
    SPEEDS: SPEEDS,
    clampIndex: clampIndex,
    step: step,
    intervalMs: intervalMs,
    nextSpeed: nextSpeed,
    pnl: pnl,
    pnlPercent: pnlPercent,
    trade: trade,
    summary: summary,
  };
});
