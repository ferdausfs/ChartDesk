# Alerts

## How they run now (local-first)

1. Alerts live in Room (`alerts`, `alert_events`) and are evaluated against Binance prices:
   - in-app every 30 seconds while the Alerts screen is open;
   - in the background every 15 minutes via `AlertCheckWorker` (WorkManager, network constraint).
2. A triggered alert flips to `active = 0`, writes an `alert_events` row and raises a local notification
   (`chartdesk_alerts` channel, POST_NOTIFICATIONS requested on Android 13+).
3. No server round-trip is required, so alerts work on the free tier and without an account.

## Server path (code ready, deploy blocked)

`backend/` contains the Worker + D1 schema for server-side evaluation (`POST /api/alerts`, cron every minute,
`GET /api/alerts/events` for delivery). Deployment is blocked on a Cloudflare **Account ID**: the supplied API token
can verify itself but has no permission to list accounts or zones, so Wrangler cannot pick a target account.

To finish the server path:

```bash
cd backend
npm install
CLOUDFLARE_ACCOUNT_ID=<account-id> npx wrangler d1 create chartdesk   # put the id in wrangler.toml
CLOUDFLARE_ACCOUNT_ID=<account-id> npm run migrate
CLOUDFLARE_ACCOUNT_ID=<account-id> npx wrangler deploy
```

Then add the Worker URL as `API_BASE_URL` in the app build config and switch `AlertRepository` to the remote API.
The D1 schema and endpoints are already written and documented in `backend/README.md`.

## Limitations

- No FCM project in this build: notifications are local, so a trigger only shows while the device is online and
  WorkManager gets a slot (15 min minimum). FCM is the next step once a Firebase project exists.
- Indicator/drawing-cross alerts are not implemented yet; only price alerts ship. The alert model already has
  `direction` and a numeric `price`, so adding `indicator` alerts is a schema + evaluator extension.
