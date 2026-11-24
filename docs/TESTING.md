# Testing

## What runs where

| Layer | Tool | Command | Runs in CI |
| --- | --- | --- | --- |
| Kotlin logic (parsers, candle math, bridge protocol) | JUnit 4 on the JVM | `./gradlew testDebugUnitTest` | yes |
| Chart JS math (Heikin-Ashi, aggregation, formatting) | `node:test` | `npm test --prefix js-tests` | yes |
| Chart UI in a real browser engine (lightweight-charts, bridge commands, themes) | `puppeteer-core` + headless Chromium | `npm test --prefix js-tests` | yes |
| Android UI / instrumented | Espresso + Compose test | requires a device or emulator | not on this server |

## Why no Robolectric on the server

The build host is `linux/arm64`. Robolectric 4.13 loads Android's Conscrypt JNI provider during test setup and there is
no `linux-aarch_64` binary in the Android all-jar, so every Robolectric test dies in `AndroidTestEnvironment` before
the test body runs. The failure is environmental, not app code.

Mitigation: the riskiest layer (the chart page) is exercised in a real Chromium instance, and every pure logic unit
is covered by plain JVM tests. A device-based Compose smoke test is kept as the next step once a runner with
`/dev/kvm` (or an ARM Robolectric build) is available. This is recorded in `AGENT_LOG.md` as an accepted limitation.

## Headless chart test coverage

`js-tests/browser.test.cjs` loads `app/src/main/assets/chart/index.html` from disk and checks:

- the page boots with no console/page errors and the TradingView attribution is present;
- `setCandles` fills the series (legend symbol, skeleton hidden, canvas present);
- all five chart types switch without throwing;
- price scale changes to logarithmic;
- theme + colour-blind attributes apply;
- a live `updateCandle` appends the bar;
- an unknown command is ignored instead of crashing the bridge.
