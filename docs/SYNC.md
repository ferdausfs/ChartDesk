# Account & sync

## Account (local)

- Email + password, stored in DataStore on the device.
- Password is never stored: PBKDF2-HMAC-SHA256, 120k iterations, 16-byte random salt (`PasswordHasher`).
- Sign-in state is a local flag; there is no remote identity until the Worker sync endpoints are deployed.

## Sync (portable bundle)

`SyncManager.exportJson()` writes one JSON document with every user-owned row:

| Section | Contents |
| --- | --- |
| `watchlists`, `items` | named lists and their symbols/order |
| `layouts` | name, symbol, timeframe, chart type, grid |
| `scripts` | Pine scripts |
| `drawings` | per-symbol drawing payloads (time/price points) |
| `indicators` | per-symbol indicator configs |
| `alerts` | price alerts and trigger timestamps |

Import merges by primary key inside a single Room transaction. The bundle is shared through the Android file picker,
so it works with Drive, email, USB or another device without a server.

## Cloud path

The Worker is ready to host `/api/sync` endpoints backed by D1 (see `backend/`), but deployment is blocked by the
Cloudflare token lacking Workers/D1 edit permissions. When the token is updated:

1. Deploy the Worker and D1 (see `backend/README.md`).
2. Add `POST /api/sync/:deviceToken` (push bundle) and `GET /api/sync/:deviceToken` (pull bundle) using the same
   `SyncPayload` JSON.
3. Point `SyncManager` at those endpoints behind the signed-in account.

The bundle schema is versioned (`version: 1`) so a future cloud implementation can migrate old exports.
