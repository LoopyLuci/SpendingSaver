# SpendingSaver operator runbook

This runbook applies to `polyglot-app/` (Tauri + Python + Go), not the separate
browser-only or Android projects. Commands begin at the repository root unless
the section explicitly states another working directory.

## Prerequisites

Install Python 3.9+, Go, Node.js/npm, and (for native Tauri builds) Rust plus
the Tauri prerequisites for your operating system.

## Start local services

The backend dependency metadata is incomplete for clean-environment setup; see
the root [`TODO.md`](../../TODO.md). Once the required Python packages are
available, start the API from the repository root:

```powershell
Set-Location polyglot-app\backend
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

The `scripts/start.py` helper currently launches Uvicorn with an invalid
application module (`app:app`). Use the direct command above until the helper
is fixed. The Go service binds beyond loopback; do not expose a development
server to an untrusted network.

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

The nested workflow configuration is not automatically discovered as GitHub
Actions workflow configuration at this repository's current layout. The
backend job also refers to a `requirements.txt` that is not present. See the
root [`TODO.md`](../../TODO.md).

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

## Package

From the repository root:

```powershell
Set-Location polyglot-app\tauri-app
npm ci
npm run tauri build
```

Inspect `src-tauri/tauri.conf.json` for configured bundle targets and sidecar
resources. Build/package required sidecar artifacts before bundling. Windows
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
