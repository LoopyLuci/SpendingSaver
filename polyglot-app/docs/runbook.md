# SpendingSaver Operator Runbook

## Build

```bash
# backend
cd backend
python -m pytest tests/test_api.py -q

# go service
cd go-service
go test -count=1 ./...

# tauri frontend
cd polyglot-app/tauri-app
npm ci
npm run test
npm run build

# tauri rust check
cd polyglot-app/tauri-app/src-tauri
cargo check
```

## Run

```bash
# terminal 1: python backend
cd backend
uvicorn app:app --host 127.0.0.1 --port 8125

# terminal 2: go service
cd go-service
go run .

# terminal 3: tauri dev / browser
cd polyglot-app/tauri-app
npm run dev
# open http://localhost:5173
```

## Package

```bash
cd polyglot-app/tauri-app
npx tauri build --bundles app
# artifacts: src-tauri/target/release/SpendingSaver.exe
```

## Windows signing

```powershell
$thumbprint = "<cert thumbprint>"
$exe = "src-tauri\target\release\SpendingSaver.exe"
signtool sign /sha1 $thumbprint /tr http://timestamp.digicert.com /td sha256 /fd sha256 $exe
signtool verify /pa $exe
```

## Database

- SQLite file: `backend/data/spendingsaver.db`
- Migrations: `backend/alembic/versions/`
- Upgrade: `cd backend && alembic upgrade head`
- Downgrade: `cd backend && alembic downgrade base`

## Observability

- Backend logs: stdout / systemd journal
- Frontend logs: Tauri log plugin or browser console
- Frontend telemetry: Settings > Telemetry > On
- Health endpoints: `http://127.0.0.1:8125/health`, `http://127.0.0.1:8126/health`

## Troubleshooting

- Port 8125/8126 busy: `netstat -ano | grep ":8125" | grep LISTENING` then `taskkill /F /PID <pid>`
- Tauri binary locked: `tasklist | grep -i spendingsaver` then `taskkill //F //IM SpendingSaver.exe`
- WebView2 missing: install Evergreen WebView2 Runtime
