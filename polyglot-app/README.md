# Polyglot Desktop App

Light, fast cross-platform desktop app shell with:
- **Frontend:** React + TypeScript + Tailwind CSS + Vite
- **Desktop shell:** Tauri 2.x
- **Backend:** Python FastAPI service
- **Worker:** Go HTTP service

## Project Layout

```text
polyglot-app/
  tauri-app/           Tauri desktop shell + React frontend
    src-tauri/         Rust backend for native desktop IPC
    src/               TypeScript/React UI code
  backend/             Python FastAPI backend service
  go-service/          Go worker service
  scripts/             Launch/verify helpers
```

## Quick Start

```bash
# 1) Start all services
python scripts/start.py

# 2) In another shell, frontend dev server
cd tauri-app
npm install
npm run dev

# 3) Open browser
http://localhost:5173
```

## Tauri Native Mode

```bash
cd tauri-app
npm run build
npm run tauri dev
```

## Verification

- Rust check: `cd tauri-app/src-tauri && cargo check`
- Frontend build: `cd tauri-app && npm run build`
- Python syntax: `python -m py_compile backend/app/api.py backend/app/cli.py backend/app.py`
- Go vet: `cd go-service && go vet ./...`
- Launch script: `python scripts/start.py`

## Backend Endpoints

- Python: `http://127.0.0.1:8125/health`, `POST /process`
- Go: `http://127.0.0.1:8126/health`, `GET/POST /items`

## Frontend Behavior

- Browser mode calls backends over HTTP.
- Tauri mode keeps HTTP calls too; Rust shell can proxy or add native commands.
