# Agent log

Newest entries first. Never delete an entry.

---

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
- **Commit:** see `git log` on `main` (initial feature commit, hash `__HASH__`).
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
