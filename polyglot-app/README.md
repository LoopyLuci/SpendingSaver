# SpendingSaver desktop

The desktop app is a **native Rust program** (egui, no web view, no Tauri, no background services). It opens
instantly and keeps everything in one local SQLite file. This directory also holds an optional headless
backend used by the MCP adapter and the browser app's API.

## Layout

- `desktop/` — **the desktop app** (Rust, `eframe`/`egui`, `rusqlite`). Start here.
- `backend/` — optional FastAPI service with SQLite persistence and Alembic migrations (used by
  [`spendingsaver-mcp`](../spendingsaver-mcp/README.md); it has its own database).
- `go-service/` — optional Go HTTP service that goes with the backend.
- `link/` — Rust agent that carries sync over [Transferd](https://github.com/LoopyLuci/Transferd) so a phone
  can sync from outside the home network (used by the backend and the Android app).
- `scripts/` — startup, tests, accessibility and load checks.
- `docs/` — [runbook](docs/runbook.md), [sync protocol](docs/sync.md), [link](docs/link.md),
  [bank linking](docs/bank-linking.md).

## Run the desktop app

You need a Rust toolchain ([rustup](https://rustup.rs)). From this directory:

```powershell
Set-Location desktop
cargo run --release
```

or build once with `cargo build --release` and run `desktop/target/release/spendingsaver` (`.exe` on Windows).
The window opens in about a tenth of a second: the only start-up work is opening the database file.

What it does: dashboard, transactions (search and filter, add, edit, delete), accounts, budgets, categories,
the US federal **tax** estimator (same numbers as the Android app), JSON backup and CSV import/export, and
optional phone sync. See [`desktop/README.md`](desktop/README.md).

### Data

The app reads and writes `%APPDATA%\SpendingSaver\spendingsaver-desktop.db` (`SPENDINGSAVER_DATA` overrides
the folder). This is **not** the backend's database (`backend/data/spendingsaver.db`); the two do not share
data. A backup file made by the desktop app opens in the Android app and the reverse.

### Phone sync (optional)

Sync is off until you turn it on in **Phone Sync**. The app then listens on your local network (port 8125 or the
next free one) and shows a QR code; the Android app scans it. Details, security and merge rules:
[`docs/sync.md`](docs/sync.md). Syncing from outside your network uses the Transferd link, which is currently
provided by the backend (see [`docs/link.md`](docs/link.md)).

## Optional backend and MCP

Only needed if you use the MCP adapter or call the HTTP API yourself.

```powershell
Set-Location backend
python -m pip install -e ".[dev]"
python -m uvicorn app.api:app --host 127.0.0.1 --port 8125
```

Do not expose the backend or the Go service (port 8126, all interfaces) to an untrusted network. The MCP
adapter can change financial records; see its README.

## Tests

```powershell
Set-Location desktop; cargo test --release          # desktop app
Set-Location ..ackend; python -m pytest -q         # backend
Set-Location ..\go-service; go test -count=1 ./...   # go service
```

From the repository root, `scripts/check.ps1` runs every suite.

## Database migrations (backend only)

Migrations are in `backend/alembic/versions/`; run `python -m alembic upgrade head` from `backend/`. Back up
`backend/data/spendingsaver.db` first. The desktop app's own database creates and extends its tables itself and
never drops data.
