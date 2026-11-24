# SpendingSaver operator runbook

This runbook applies to `polyglot-app/` (Tauri + Python + Go), not the separate
browser-only or Android projects. Commands begin at the repository root unless
the section explicitly states another working directory.

## Prerequisites

Install Python 3.9+, Go, Node.js/npm, and (for native Tauri builds) Rust plus
the Tauri prerequisites for your operating system.

## Start local services

Install the backend once (a virtual environment is recommended):

```powershell
Set-Location polyglot-appackend
python -m pip install -e ".[dev]"
```

Then start the API:

```powershell
python -m uvicorn app.api:app --host 127.0.0.1 --port 8125
```

In a separate terminal from the repository root, start the Go service:

```powershell
Set-Location polyglot-app\go-service
go run .
```

The Python API command binds to `127.0.0.1:8125`. The Go service listens on
all interfaces at port 8126; do not expose it to an untrusted network.

In another terminal from the repository root, run the frontend:

```powershell
Set-Location polyglot-app\tauri-app
npm ci
npm run dev
```

Use the URL printed by Vite (port 5173 by default). Health checks:

- `http://127.0.0.1:8125/health`
- `http://127.0.0.1:8126/health`

### One-command helper

`python polyglot-app\scripts\start.py` starts the Python API (using the Python
that runs the script, so activate the venv with the backend installed first)
and the Go service (building `go-service.exe` with `go build` if missing), then
waits until Ctrl+C. It does not start the frontend; run `npm run dev` (above) in
another terminal. Verified on Windows: both `/health` endpoints answered and
the Vite dev server served on 5173.

### Tauri development (`tauri dev`)

`npm run tauri dev` (from `polyglot-app	auri-app`) runs `beforeDevCommand`
(`npm run dev`, the Vite server on the fixed port 5173, `strictPort`) and loads
`devUrl` in the native window. Stop any other process on 5173 first. It needs
Rust and the Tauri prerequisites and was not launched in this verification;
`beforeDevCommand` previously ran a production build, which does not start a
server on `devUrl`. The services above must be started separately.

## Test and build

From the repository root, after installing the dependencies required by each
project:

```powershell
# Python backend
Set-Location polyglot-app\backend
python -m pytest tests/test_api.py -q

# Go service
Set-Location ..\go-service
go test -count=1 ./...

# Frontend
Set-Location ..\tauri-app
npm ci
npm run test
npm run build

# Rust shell
Set-Location src-tauri
cargo test --lib --tests
cargo check
```

The integration script runs the Python, Go, and frontend tests:

```powershell
python polyglot-app\scripts\integration_tests.py
```

CI (`.github/workflows/ci.yml` at the repository root) runs the Android
build/tests, backend tests and migration round trip, Go tests, and the desktop
frontend tests and build. It does not run Rust tests or packaging; the former
nested `polyglot-app/.github/workflows/` files were not discovered by GitHub
Actions and referenced a missing `requirements.txt`, so they were removed.

## Database and migrations

- Default SQLite database: `backend/data/spendingsaver.db`.
- Migration scripts: `backend/alembic/versions/`.
- Back up the database before migrations that affect valuable data.

From the repository root:

```powershell
Set-Location polyglot-app\backend
python -m alembic upgrade head
python -m alembic current
```

`downgrade` is migration-dependent and may lose data; inspect the relevant
migration and retain a backup before using it.

## Optional Android sync hub

The backend can act as a sync hub for paired Android devices. It is off until a
device pairs; the desktop app does not need it. Pairing, security, merge rules
and troubleshooting are in [`sync.md`](sync.md). Back up
`backend/data/spendingsaver.db` before the first run of a version that includes
it (new `sync_log` and `sync_devices` tables are added; see the upgrade notes
there). Test settings: the backend tests use a throwaway database through
`SS_DB_PATH` and `SS_DATA_DIR` (see `backend/tests/conftest.py`); set the same
variables to run a scratch hub.

## Package

From the repository root:

```powershell
Set-Location polyglot-app\tauri-app
npm ci
npm run tauri build
```

Inspect `src-tauri/tauri.conf.json` for configured bundle targets and sidecar
resources. Build/package required sidecar artifacts before bundling (`backend/dist-offline/backend-service` has no checked-in build script; packaging and signing were not verified and there is no packaging CI). Windows
certificate signing guidance is in
[`scripts/windows-signing.md`](../scripts/windows-signing.md).

## Troubleshooting

- **Port 8125 or 8126 is occupied:** identify the process bound to the port
  using Windows Resource Monitor or `Get-NetTCPConnection` and inspect its
  owning PID before stopping it.
- **Tauri packaging cannot find sidecars:** build the backend and Go sidecar
  inputs expected by `tauri.conf.json` and confirm their paths exist.
- **WebView2 is unavailable on Windows:** install the Microsoft Edge WebView2
  Runtime.
- **Database migration fails:** stop the application, retain a copy of the
  database, inspect the Alembic revision state, and resolve the migration
  issue before retrying.

## Security and operations

The Python command above binds to loopback, but the Go service listens on all
interfaces. Keep services on a trusted network and configure appropriate
firewall/authentication controls before remote access. The MCP adapter can
write financial data; see its
[configuration and authentication notes](../../spendingsaver-mcp/README.md).

## Running the native desktop app

`npm run tauri -- build --debug --no-bundle` (from `polyglot-app/tauri-app`) produces
`src-tauri/target/debug/spendingsaver.exe`. On start it launches the Python backend (127.0.0.1:8125) and the Go
service (8126) itself, freeing those ports first. Unless a bundled backend exists, it runs
`python -m uvicorn app.api:app` using the first Python that can import `sqlalchemy`, `fastapi` and `uvicorn`:
`SPENDINGSAVER_PYTHON`, then a project virtual environment (`backend/.venv`, `.venv`, `../.venv`,
`../.venv-check`), then `python`/`py` on PATH. Phone Sync → Connect from anywhere → Turn on → New phone invite
shows the pairing QR; the link agent (`polyglot-app/link`, `cargo build --release`) must be rebuilt after
Link changes or invites fail with "link.make_code is not available". The phone must be able to reach the PC
(same Wi-Fi, or a relay).
