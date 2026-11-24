# ChartDesk backend

Cloudflare Worker providing:

- `GET /api/health` — liveness.
- `GET /api/candles?symbol=BTCUSDT&interval=1m&limit=500` — Binance proxy with edge cache (app fallback path).
- `GET /api/symbols` — exchange info proxy.
- `POST /api/alerts` `{deviceToken, symbol, direction, price}` — create a server-side price alert.
- `GET /api/alerts?token=…` — list alerts for a device.
- `DELETE /api/alerts/:id?token=…` — delete.
- `GET /api/alerts/events?token=…&since=…` — unseen triggers (the app polls this and raises a local notification).
- `POST /api/alerts/check` — evaluate now (also runs on the cron trigger).
- Cron `* * * * *` — evaluate all active alerts against Binance prices and write `alert_events`.

## Deploy

```bash
cd backend
npm install
npx wrangler d1 create chartdesk          # copy database_id into wrangler.toml
npm run migrate
npx wrangler deploy
```

Secrets: none required. The Worker only talks to public Binance endpoints. Do not put user API keys here — the AI
assistant key stays in the app's settings.

## Notes

- Alerts are scoped by a random device token generated on the phone; no account is needed.
- The cron evaluation is idempotent: an alert flips to `active = 0` in the same batch that writes its event.
- Free-tier friendly: one D1 database, no KV, no Durable Objects, edge cache for market data.
