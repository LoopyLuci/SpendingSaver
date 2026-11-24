# SpendingSaver operator runbook

This runbook applies to `polyglot-app/` (the native Rust desktop app and the optional Python + Go backend), not
the separate browser-only or Android projects. Commands begin at the repository root unless the section states
another working directory.

## Prerequisites

The desktop app needs only Rust ([rustup](https://rustup.rs)). The optional backend needs Python 3.9+ and, for the
Go service, Go.

## Run the desktop app

```powershell
Set-Location polyglot-app\desktop
cargo run --release
```

Data lives in `%APPDATA%\SpendingSaver\spendingsaver-desktop.db` (`SPENDINGSAVER_DATA` overrides the folder). To
start fresh, close the app and move that file. To keep a copy, use Import / Export, or copy the file while the app
is closed. The desktop app does not use the backend's database.

## Start the optional backend services

Only needed for the MCP adapter or direct API use. Install the backend once (a virtual environment is
recommended):

```powershell
Set-Location polyglot-app\backend
python -m pip install -e ".[dev]"
python -m uvicorn app.api:app --host 127.0.0.1 --port 8125
```

In a separate terminal, the Go service:

```powershell
Set-Location polyglot-app\go-service
go run .
```

The Python API binds to `127.0.0.1:8125`. The Go service listens on all interfaces at port 8126; do not expose it
to an untrusted network. Health checks: `http://127.0.0.1:8125/health`, `http://127.0.0.1:8126/health`.

`python polyglot-app\scripts\start.py` starts both (using the Python that runs the script, so activate the
environment with the backend installed first). The desktop app's phone sync also listens on port 8125 or the next
free port, so it can run at the same time as the backend.

## Test and build

```powershell
# Python backend
Set-Location polyglot-app\backend
python -m pytest tests/test_api.py -q

# Go service
Set-Location ..\go-service
go test -count=1 ./...

# Desktop app
Set-Location ..\desktop
cargo test --release
```

`powershell -ExecutionPolicy Bypass -File scripts/check.ps1` (repository root) runs every project's tests.
`python polyglot-app\scripts\integration_tests.py` runs the Python, Go and desktop tests.

CI (`.github/workflows/ci.yml`) runs the Android build/tests, backend tests and migration round trip, Go tests and
the desktop tests. It does not run packaging.

## Database and migrations (backend)

- Default SQLite database: `backend/data/spendingsaver.db`.
- Migration scripts: `backend/alembic/versions/`.
- Back up the database before migrations that affect valuable data.

```powershell
Set-Location polyglot-app\backend
python -m alembic upgrade head
python -m alembic current
```

`downgrade` is migration-dependent and may lose data; inspect the migration and keep a backup first.

The desktop app's own database creates and extends its tables itself, never drops data, and deletes rows only as
tombstones.

## Phone sync

Both the desktop app and the backend can act as the sync hub for paired Android devices. It is off until you turn it
on (desktop: **Phone Sync → Turn on phone sync**). Pairing, security, merge rules and troubleshooting are in
[`sync.md`](sync.md). The backend adds `sync_log` and `sync_devices` tables on first run of a version that includes
it; back up `backend/data/spendingsaver.db` first (see the upgrade notes in `sync.md`). The backend tests use a
throwaway database through `SS_DB_PATH` and `SS_DATA_DIR` (see `backend/tests/conftest.py`).

## Package

`cargo build --release` in `polyglot-app/desktop` produces one self-contained executable
(`target/release/spendingsaver`). There is no installer or code-signing setup; on Windows run `signtool sign` on
that file with your own certificate if you distribute it.

## Troubleshooting

- **Port 8125 or 8126 is occupied:** identify the process bound to the port using `Get-NetTCPConnection` and
  inspect its owning PID before stopping it. The desktop app moves to the next free port by itself.
- **The phone cannot reach the desktop:** the phone must be on the same Wi-Fi as the computer, and Windows must
  allow the app on private networks (it asks the first time sync is turned on).
- **Database migration fails (backend):** stop the application, keep a copy of the database, inspect the Alembic
  revision state, and resolve the issue before retrying.

## Security and operations

The Python command above binds to loopback, but the Go service listens on all interfaces. Keep services on a
trusted network and configure firewall/authentication controls before any remote access. The MCP adapter can write
financial data; see its [configuration and authentication notes](../../spendingsaver-mcp/README.md).
