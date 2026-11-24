# Architecture

## Decision record

- **Hybrid native, not full WebView.** Kotlin/Compose owns navigation, lists, alerts, settings, layouts and auth.
  The chart itself is a WebView running `lightweight-charts` from `assets/` — no CDN, no network dependency for the
  renderer, no remote code.
- **One bridge.** `ChartBridge` is the only JS ⇄ Kotlin surface. Calls are typed JSON envelopes
  (`{"type": "...", "payload": {...}}`); responses carry a request id. No free-form eval from Kotlin.
- **Indicators, drawings, replay and Pine run in JS** next to the series data. Kotlin never recomputes chart math;
  it stores and syncs configuration.
- **Market data behind `DataProvider`.** Crypto (Binance public REST/WS) first, forex second. Providers are swappable
  and every candle is normalised to `Candle(timeSec, open, high, low, close, volume)`.
- **Backend on Cloudflare Workers.** Market-data proxy with caching, alert evaluation, AI relay. Free-tier first.
- **Offline-first storage.** Room for watchlists, drawings, layouts, scripts, alert cache; DataStore for settings.

## Module map (single Gradle module, package-separated)

```
com.chartdesk.app
├── data/            Room entities, DAOs, repositories, DataProvider implementations
├── chart/           ChartBridge, ChartWebView, ChartController, JS asset loader
├── feature/         screen-level ViewModels + Compose UI (chart, watchlist, scripts, alerts, more)
├── ui/theme         single palette + typography token file
├── ui/components    shared state views (loading / empty / error / offline)
└── di               Hilt modules
```

## JS layer

```
assets/chart/
├── index.html                     host page, tokens.css
├── lightweight-charts...js        vendored Apache-2.0 build (pinned 4.2.0)
├── app.js                         chart bootstrap + bridge
├── indicators.js                  indicator math + panes
├── drawings.js                    drawing model, hit-testing, undo/redo
├── replay.js                      replay engine
└── pine.js                        Pine v6 subset lexer/parser/runtime
```

## Data flow

1. Kotlin requests candles from `DataProvider` (cache-first, Room + memory).
2. Kotlin pushes `setCandles` into the WebView bridge.
3. Live trades arrive over WebSocket → Kotlin normalises → `updateCandle` (throttled to frame budget).
4. User actions in JS (drawing added, crosshair moved, replay step) post events back; Kotlin persists what matters.

## Threading

- Network: OkHttp/WebSocket on its own dispatcher, flows on `Dispatchers.IO`.
- Chart updates: coalesced on the main thread at most once per frame.
- Persistence: Room suspend functions, never on main.
