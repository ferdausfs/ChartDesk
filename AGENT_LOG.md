# Agent log

Newest entries first. Never delete an entry.

---

## 2026-11-24 — Session 1 (feature 9)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 9 — Pine v6 subset.
- **What changed:** `assets/chart/pine.js` (indentation-aware lexer, Pratt parser, per-bar evaluator) supporting
  `indicator`, `plot`, `plotshape`, `hline`, `bgcolor`, `input.*`, `ta.*` (sma/ema/rsi/atr/highest/lowest/change/
  crossover/crossunder), `math.*`, `request.security` (chart-series fallback + warning), `var`, `:=`, `if/else`,
  `for`, arrays and colours; readable line/column errors; overlay plotting on the chart with auto re-run on
  symbol/timeframe changes; Room v5 `scripts` table + migration; sample library; editor with Pine syntax
  highlighting, save/rename/delete, run and error panel; `docs/PINE-SUPPORT.md` lists the exact supported and
  unsupported surface.
- **Touched files:** `assets/chart/{pine.js,app.js,index.html}`, `data/scripts/*`, `data/db/ChartDeskDatabase.kt`,
  `di/DatabaseModule.kt`, `chart/{ChartCommand,ChartEvent,ChartProtocol}.kt`, `feature/scripts/*`,
  `ui/scripts/PineHighlighter.kt`, `ui/screens/{ScriptsScreen.kt,PlaceholderScreens.kt}`, `docs/PINE-SUPPORT.md`,
  `js-tests/{pine.test.cjs,browser.test.cjs,package.json}`.
- **Verification:** 51/51 JS tests (15 Pine suites + browser run/clear); `./gradlew assembleDebug testDebugUnitTest`
  green.
- **Commit:** `__HASH9__`.
- **Pending:** feature 10 (alerts).
- **Notes:** named arguments (`title=`, `color=`) are supported; `ta.*` accepts builtin/user series but nested
  `ta.sma(...)` inside `ta.crossover(...)` only sees the current value — documented limitation.

## 2026-11-24 — Session 1 (feature 8)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 8 — bar replay.
- **What changed:** replay helpers (`assets/chart/replay.js`: index clamping, stepping, speed→interval, paper-trade
  state machine, PnL) plus app-side replay in `app.js` (series truncated to the replay cursor, indicators and
  drawings recompute on the slice, live updates ignored while replaying, interval timer with 0.5/1/2/5× speed);
  start-point sheet with a slider; in-chart replay bar (play/pause, ±1 bar, speed, stop, Buy/Sell/Close, live PnL);
  replay auto-stops on symbol/timeframe change; Kotlin bridge commands and `replayState` event.
- **Touched files:** `assets/chart/{replay.js,app.js,index.html}`, `chart/{ChartCommand,ChartEvent,ChartProtocol}.kt`,
  `feature/chart/ChartViewModel.kt`, `ui/chart/{ReplayBar.kt,ChartToolbar.kt}`, `ui/screens/ChartScreen.kt`,
  `data/Candle.kt` (Locale.US formatting), `js-tests/{replay.test.cjs,browser.test.cjs,package.json}`.
- **Verification:** 36/36 JS tests (replay maths + headless browser starting at bar 20, stepping, paper-buy, stop);
  `./gradlew assembleDebug testDebugUnitTest` green.
- **Commit:** `1c35cb2`.
- **Pending:** feature 9 (Pine subset).
- **Notes:** paper trades are intentionally local to the replay session (not persisted) and reset when replay stops.

## 2026-11-24 — Session 1 (feature 7)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 7 — drawing tools.
- **What changed:** canvas overlay (`assets/chart/drawings.js`) bound to the chart's time/price scales with
  11 tools (trendline, ray, horizontal, vertical, rectangle, fib retracement, fib channel, text, long position,
  short position, measure); select/move by handles, lock, delete, clear, undo/redo with 60-step history; text label
  dialog driven from Kotlin; measure read-out posted to the shell; per-symbol persistence in Room v4 (`drawings`
  table + migration); collapsible left drawing rail with pointer/select modes; drawings survive chart-type changes
  because the module gets the new series reference.
- **Touched files:** `assets/chart/{drawings.js,index.html,app.js}`, `data/drawings/*`, `data/db/ChartDeskDatabase.kt`,
  `di/DatabaseModule.kt`, `chart/{ChartCommand,ChartEvent,ChartProtocol}.kt`, `feature/chart/ChartViewModel.kt`,
  `ui/chart/DrawingRail.kt`, `ui/screens/ChartScreen.kt`, `js-tests/drawings.test.cjs`,
  `app/src/test/.../DrawingCodecTest.kt`.
- **Verification:** 28/28 JS tests (drawing module + headless browser drawing a trendline with two real mouse clicks,
  undo → 0, redo → 1); `./gradlew assembleDebug testDebugUnitTest` green.
- **Commit:** `88b926d`.
- **Pending:** feature 8 (bar replay).
- **Notes:** canvas pointer events are only enabled in select/tool mode so chart panning keeps working; long-press
  drag on drawings is intentionally not used because the canvas already owns the gesture.

## 2026-11-24 — Session 1 (feature 6)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 6 — indicators.
- **What changed:** new `assets/chart/indicators.js` with 12 indicators (MA/EMA/WMA/Bollinger/VWAP/Ichimoku/
  SuperTrend/Volume/RSI/MACD/ATR/Stochastic), all computed in JS from the raw series; overlay indicators draw on the
  price chart, oscillators get their own synced panes (time-scale linking both ways, time axis moves to the bottom
  pane); per-indicator parameter editing; per-symbol persistence in Room v3 (`indicators` table + migration);
  indicator legend; theme propagation to panes; volume indicator toggles the built-in histogram.
- **Touched files:** `assets/chart/{index.html,tokens.css,app.js,indicators.js}`, `data/indicators/*`,
  `data/db/ChartDeskDatabase.kt`, `di/DatabaseModule.kt`, `chart/ChartCommand.kt`, `feature/chart/ChartViewModel.kt`,
  `ui/chart/IndicatorsSheet.kt`, `ui/screens/ChartScreen.kt`, `js-tests/indicators.test.cjs`,
  `app/src/test/.../IndicatorCatalogTest.kt`.
- **Verification:** 24/24 JS tests (12 indicator math suites + headless browser adding MA/RSI/MACD → 2 panes, removing
  them → 0 panes); `./gradlew assembleDebug testDebugUnitTest` green.
- **Commit:** `c6beba5`.
- **Pending:** feature 7 (drawing tools).
- **Notes:** Kotlin `IndicatorCatalog` must stay in sync with `registry` in `indicators.js` (ids, params, defaults).
  Panes are separate lightweight-charts instances; only the bottom pane shows the time axis.

## 2026-11-24 — Session 1 (feature 5)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 5 — watchlist.
- **What changed:** Room v2 with `watchlists` + `watchlist_items` and a real migration; repository for multiple
  named lists (create/rename/delete), add/remove symbols, transactional reorder; 5-second ticker polling for the
  selected list with offline flag; sort modes (default/change/price/name); long-press drag reorder with haptic;
  add-symbol search dialog; landscape right-hand watchlist panel; row tap opens the chart tab on that symbol through
  `ChartRequestBus`.
- **Touched files:** `data/db/ChartDeskDatabase.kt`, `di/DatabaseModule.kt`, `data/watchlist/*`,
  `data/ChartRequestBus.kt`, `feature/watchlist/*`, `feature/nav/NavigationViewModel.kt`,
  `ui/screens/WatchlistScreen.kt`, `ui/nav/ChartDeskRoot.kt`, `feature/chart/ChartViewModel.kt`,
  `app/src/test/.../WatchlistTest.kt`.
- **Verification:** `./gradlew assembleDebug testDebugUnitTest` green (30 JVM tests incl. reorder + sorting).
- **Commit:** `__HASH5__`.
- **Pending:** feature 6 (indicators).
- **Notes:** `WatchlistSorting` is pure so sorting stays unit-testable; the ticker loop is cancelled and restarted
  whenever the symbol set changes to avoid stale subscriptions.

## 2026-11-24 — Session 1 (feature 4)

- **Agent / tool:** OpenCode Android agent; `dev-server` for build/tests/git.
- **Feature:** 4 — toolbar & layout.
- **What changed:** real top toolbar (symbol block, timeframe pill, chart-type menu, indicators placeholder,
  screenshot, settings, fullscreen); DataStore-backed settings sheet (theme dark/light/auto, colour-blind palette,
  haptics, grid, crosshair, watermark, price scale, launch defaults); theme now applied app-wide from settings;
  immersive fullscreen (system bars hidden, bottom bar collapses); screenshot via `PixelCopy` + FileProvider share;
  landscape layout with left rail + right watchlist placeholder and no bottom bar; shared UI components
  (`ui/components/Common.kt`); chart bridge gained `setOptions` for grid/crosshair/watermark.
- **Touched files:** `ui/chart/ChartToolbar.kt`, `ui/settings/SettingsSheet.kt`, `ui/nav/ChartDeskRoot.kt`,
  `ui/screens/ChartScreen.kt`, `feature/chart/ChartViewModel.kt`, `feature/settings/*`, `data/settings/*`,
  `chart/{ChartCommand,ChartController,ScreenshotManager}.kt`, `assets/chart/app.js`, manifest + FileProvider.
- **Verification:** `./gradlew assembleDebug testDebugUnitTest` green (23 JVM tests); JS tests unchanged and green.
- **Commit:** `c91ca39`.
- **Pending:** feature 5 (watchlist).
- **Notes:** settings flows are the single source for theme; chart theme is pushed through the bridge so the WebView
  never diverges from the Compose shell.

## 2026-11-24 — Session 1 (features 1–3)

- **Agent / tool:** OpenCode Android agent (DeepSeek V4.1 Flash) authoring; `dev-server` for build, tests, git.
- **Features:** 1 bootstrap, 2 chart core, 3 market data.
- **What changed:**
  - New public repo `ferdausfs/ChartDesk`; Gradle 8.7 / AGP 8.5.2 / Kotlin 2.0.20, Compose, Hilt, Room, DataStore,
    OkHttp; five-tab shell; palette token file with dark, light and colour-blind variants; adaptive icon;
    CI with unit tests, headless chart tests, debug APK and conditional signed release APK.
  - Vendored `lightweight-charts` 4.2.0 (Apache-2.0) into `assets/chart/` with licence and visible attribution;
    `app.js` chart engine (5 chart types, crosshair legend, last-price line, log/%/auto scale, double-tap reset,
    long-press inspect, haptics); typed `ChartController` bridge with queueing and event parsing.
  - Binance spot REST + WebSocket provider behind `DataProvider`; Room candle/symbol cache; 9 timeframes;
    exponential reconnect backoff with post-reconnect gap refill; symbol search; offline/error/loading states.
- **Touched files:** `app/src/main/**`, `js-tests/**`, `.github/workflows/android.yml`, `docs/*`, `README.md`.
- **Verification:** `./gradlew assembleDebug testDebugUnitTest` green (23 JVM tests); `npm test --prefix js-tests`
  green (9 tests incl. headless Chromium chart boot, 5 chart types, theme, scale, live update, bad-command safety);
  debug APK 18.7 MB produced.
- **Commit:** `3fbe8fa` — feat: bootstrap shell, chart core and live Binance feed.
- **Decisions:**
  - Robolectric removed: this server is `linux/arm64` and Robolectric 4.13 cannot load Conscrypt's JNI provider
    (`no conscrypt_openjdk_jni-linux-aarch_64`), so every Robolectric test fails before running. DoD fallback used:
    JVM unit tests + real headless Chromium tests of the chart layer. Documented in `docs/TESTING.md`.
  - Features 1–3 landed as one commit because they were developed continuously before the first green build;
    from feature 4 onward each feature gets its own commit.
  - Crypto first, forex later (owner decision); AI key user-supplied; alerts local-notification first.
- **Pending:** feature 4 (toolbar & layout), then 5–14.
- **Notes for next agent:** lightweight-charts pinned to 4.2.0 (`addCandlestickSeries` API) — do not bump to v5
  without rewriting `app.js`. Vendored JS files exist in the repo now, so a plain tar sync no longer loses them;
  deleted files must be removed on the server explicitly (no rsync there).
