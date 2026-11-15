# SpendingSaver Desktop and Backend

This project contains the Tauri desktop shell and React UI, a Python FastAPI
backend, and a Go HTTP service. It is distinct from the standalone browser app
at the repository root and the local-only Android app.

## Layout

- `tauri-app/` — React + TypeScript frontend and Tauri 2 shell.
- `backend/` — FastAPI service, SQLite persistence, and Alembic migrations.
- `go-service/` — Go HTTP service.
- `scripts/` — local startup, tests, accessibility/load checks, and packaging
  helpers.
- `docs/runbook.md` — operator commands, database migration instructions,
  packaging, and troubleshooting.

## Prerequisites

Install the tools needed for the target you want to run:

- Python 3.9 or newer and the backend dependencies.
- Go (the module declares Go 1.22).
- Node.js/npm for the frontend.
- Rust and the platform prerequisites for native Tauri development/builds.
- Backend dependency metadata is currently incomplete for a clean install; see
  the root [`TODO.md`](../TODO.md) before setting up a new environment.

## Run the services and frontend

From the repository root, in one terminal, start the FastAPI service:

```powershell
Set-Location polyglot-app\backend
python -m uvicorn app.api:app --host 127.0.0.1 --port 8125
```

In a second terminal from the repository root, start the Go service:

```powershell
Set-Location polyglot-app\go-service
go run .
```

The Python API command binds to loopback at port 8125. The Go service listens
on all interfaces at port 8126; do not expose either service to an untrusted
network. The helper script `scripts/start.py` is not currently a reliable
substitute; see the root [`TODO.md`](../TODO.md).

In another terminal from the repository root, run the frontend in browser mode:

```powershell
Set-Location polyglot-app\tauri-app
npm ci
npm run dev
```

Vite prints the URL (configured for port 5173).

## Build and tests

Run commands from this directory unless a command shows another working
directory:

```powershell
# Backend tests
Set-Location backend
python -m pytest tests/test_api.py -q

# Go tests
Set-Location ..\go-service
go test -count=1 ./...

# Frontend tests and production build
Set-Location ..\tauri-app
npm ci
npm run test
npm run build

# Rust tests
Set-Location src-tauri
cargo test --lib --tests
```

The integration helper can run backend, Go, and frontend tests from
`polyglot-app/`:

```powershell
python scripts/integration_tests.py
```

## Native package

From `tauri-app/`, with Node, Rust, and the platform's Tauri prerequisites
installed:

```powershell
npm ci
npm run tauri build
```

Windows packaging configuration is in `tauri-app/src-tauri/tauri.conf.json`.
See the runbook for signing and release caveats. Release builds may also need
the backend and Go sidecar artifacts described by the Tauri bundle config;
check those inputs before packaging.

## Data and database

The Python service stores its SQLite database under `backend/data/` by
default. Schema migrations are in `backend/alembic/versions/`; use Alembic
from the `backend/` directory. Back up the database before applying migrations
to valuable data.

## MCP integration

The separate [`spendingsaver-mcp`](../spendingsaver-mcp/README.md) package
connects to this backend's HTTP API. Its tools include write operations; keep
the backend on a trusted network and review its authentication behavior before
using it outside local development.

## Documentation

- [Operator runbook](docs/runbook.md)
- [Changelog](CHANGELOG.md)
- [Windows signing notes](scripts/windows-signing.md)
