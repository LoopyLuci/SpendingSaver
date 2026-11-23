# Optional sync between the desktop backend and the Android app

Sync is **opt-in**. The desktop app/backend and the Android app each keep their
own complete database and work fully on their own. Nothing is sent anywhere until
you pair an Android device with a desktop backend and tap **Sync now**. Unpairing
(or never pairing) leaves both databases exactly as they are.

Stores involved:

| App | Store | Role in sync |
| --- | --- | --- |
| Desktop backend | SQLite `backend/data/spendingsaver.db` | **Hub.** Holds the merged copy and a change log. |
| Android | Room database `spendingsaver.db` | **Client.** Pushes and pulls on demand. |
| Browser app, MCP adapter | their own / the backend API | Not part of sync. (The MCP adapter sees synced data because it reads the backend.) |

## What syncs

Accounts, categories, transactions and budgets, limited to the fields both apps
store:

- account: name, type, balance, currency
- category: name, color (Android's `parentId` is Android-only and is kept locally)
- transaction: account, amount, description, category, date, notes, created time
- budget: category, amount, period, start date

Everything else (recurring items, goals, debts, attachments, budget carry-over,
app settings, the Android AI and tax data) stays where it was created.

## Pairing

1. Start the backend. To let a phone reach it over your network, bind it to a
   LAN address (for example `uvicorn app.api:app --host 0.0.0.0 --port 8125` from
   `polyglot-app/backend`) and allow the port through your firewall. The default
   helper script binds `127.0.0.1` only, which is not reachable from a phone.
2. On the desktop machine itself, read the pairing key:
   `GET http://127.0.0.1:8125/api/host/info` → `key`. The key is returned only to
   requests from the machine itself; remote callers get `null`.
3. In the Android app: **Settings → Desktop sync (optional)**, enter the hub
   address (`192.168.1.20:8125`) and the pairing key, then **Pair and sync**.

Pairing returns a per-device token. The hub stores only its SHA-256; the phone
stores the token in app-private preferences that are excluded from Android cloud
backup and device transfer. Revoke a device from the hub machine:

```text
GET    http://127.0.0.1:8125/api/sync/v1/devices
DELETE http://127.0.0.1:8125/api/sync/v1/devices/{device_id}
```

Rotate the pairing key with `POST /api/host/rotate-key` (hub machine only).
Wrong keys are rate-limited per client address.

## Network security

- The token and your finance data cross the network, so use a network you trust.
  Plain `http://` is accepted by the app **only** for local-network addresses
  (loopback, private ranges, `.local`, single-label hostnames); anything else must
  be `https://`. The backend does not terminate TLS itself; put a reverse proxy in
  front of it to use `https://`.
- **Network guard.** Requests from the hub machine itself (loopback: the desktop
  app, MCP adapter, scripts) need no login, so the desktop works standalone.
  Requests from any *other* machine are rejected with 401 unless they carry a
  valid user JWT (`/api/auth/login`) or the `SS_SYNC_TOKEN` secret. Exempt:
  `/health`, `/api/auth/*`, `/api/host/info` (key hidden from remote callers),
  `/api/host/pair` and `/api/sync/v1/*`, which enforce their own pairing-key /
  device-token auth. A paired phone's device token only opens `/api/sync/v1/*`.
  Set `SS_ALLOW_OPEN_LAN=1` to restore the old open-LAN behaviour (not
  recommended). The guard trusts the connecting address, so behind a reverse
  proxy on the same machine every request looks local; protect the proxy itself.
- Do not expose the backend to the internet. The legacy `/api/sync/upsert` and
  `/api/sync/changes` routes still exist for older clients (now behind the guard).

## How merging works

Protocol v1 (`backend/app/modules/sync_v1.py`, `android/.../sync/`):

- Every insert/update/delete of a synced row on the hub appends to `sync_log`
  (a monotonic `seq`). A client pulls everything after its saved cursor, so
  clients never depend on wall clocks to find new data.
- Each sync pushes the phone's rows changed since its last clean sync, then pulls
  until the hub has no more. One request is `POST /api/sync/v1/exchange`.
- **Conflict rule: last write wins per row**, by `updated_at`. A row only
  replaces a copy that is strictly older; equal timestamps keep the hub's copy.
  Edits to *different* rows never conflict. Two edits to the *same* row on two
  devices keep whichever was saved later; the other edit is lost.
- **Deletes** are tombstones (soft deletes) and sync like any edit; a newer edit
  can bring a deleted row back. The desktop refuses to delete an account that
  still has active transactions, and a pushed deletion is applied as-is, so delete
  an account's transactions first on whichever device you delete from.
- **Clocks matter.** Timestamps are the device clocks at edit time. A timestamp
  more than 5 minutes in the future is clamped. Keep both devices' time correct.
- A record the hub cannot accept (invalid values, or a transaction whose account
  does not exist yet) is reported back and retried on the next sync; the rest of
  the batch still applies.

An Android **Restore backup** is treated as a deliberate edit: restored rows are
re-stamped "now" and active rows missing from the backup become tombstones, so
the restore propagates to the hub on the next sync instead of being undone.

## Upgrading an existing desktop database

Back up `backend/data/spendingsaver.db` first. The backend creates the two new
tables, `sync_log` and `sync_devices`, at startup, and seeds the log from your
existing rows once. The equivalent Alembic revision `a1c3e5f70b21` is idempotent;
run it from `polyglot-app/backend` with `alembic upgrade head`. It only adds
tables and log rows. Downgrading drops the two tables (devices must pair again);
financial tables are never touched.

## Troubleshooting

| Message in the app | Meaning |
| --- | --- |
| Could not reach the hub | Wrong address/port, backend not running, or firewall. The app keeps working. |
| The pairing key was not accepted | Wrong key (rotated?). Read it again on the hub machine. |
| The hub no longer trusts this device | The device was revoked. Unpair and pair again. |
| Too many attempts | Pairing rate limit; wait a few minutes. |
| That address is not a SpendingSaver hub | Wrong server, or a backend too old to sync. |
