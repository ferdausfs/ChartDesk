# ChartDesk

Native Android trading terminal for crypto (forex next), built as a Kotlin/Compose shell around a locally bundled
[lightweight-charts](https://github.com/tradingview/lightweight-charts) canvas. Charts render in a WebView at
device refresh rate; everything else — navigation, watchlists, alerts, scripts, layouts, settings — is native.

ChartDesk is an independent project. It is **not affiliated with, endorsed by, or connected to TradingView or LuxAlgo**.
The chart renderer is TradingView's open-source `lightweight-charts` library (Apache-2.0); attribution is shown in-app
and the licence text ships in `app/src/main/assets/chart/LIGHTWEIGHT_CHARTS_LICENSE.txt`.

## Status

| Area | State |
| --- | --- |
| Project bootstrap, theme, CI | done |
| Chart core (5 types, scales, legend, touch) | done |
| Market data (Binance spot, cache, reconnect) | done |
| Toolbar, settings, fullscreen, screenshot | in progress |
| Watchlist, indicators, drawings, replay, Pine, alerts, layouts, AI | planned |

Live, newest-first engineering log: `AGENT_LOG.md`.

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew assembleRelease        # needs signing env vars, see below
```

Signing (never commit the keystore):

```bash
export CHARTDESK_KEYSTORE=/path/to/chartdesk-release.jks
export CHARTDESK_STORE_PASSWORD=...
export CHARTDESK_KEY_ALIAS=chartdesk
export CHARTDESK_KEY_PASSWORD=...
```

CI (`.github/workflows/android.yml`) builds debug + signed release APKs and uploads them as artifacts when the
`CHARTDESK_*` repository secrets are present.

## Layout

```
app/                     Android app (Kotlin, Compose, Hilt, Room)
  src/main/assets/chart/ bundled lightweight-charts + our JS chart layer
backend/                 Cloudflare Worker: market data proxy, alerts, AI relay
docs/                    architecture, design differences, Pine support matrix
AGENT_LOG.md             newest-first engineering log
```

## Known limits

- Forex candles are not wired yet; the crypto path ships first (decision logged in `AGENT_LOG.md`).
- AI assistant uses a key the user enters in Settings; no key is bundled or committed.
- Alerts are evaluated server-side and delivered as local notifications while the app is running/backgrounded
  (no FCM project in this build).
