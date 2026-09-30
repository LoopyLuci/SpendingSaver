#!/usr/bin/env bash
# build_offline_desktop.sh
# Builds a fully self-contained SpendingSaver desktop app for offline use.
# Bundles: Python backend (PyInstaller) + Go service (prebuilt) + Tauri frontend.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(cd "$ROOT/polyglot-app" && pwd)"
BACKEND="$ROOT/backend"
GO_SERVICE="$ROOT/go-service"
TAURI="$ROOT/tauri-app/src-tauri"
PYTHON="C:/Users/limpi/AppData/Local/hermes/hermes-agent/venv/Scripts/python.exe"

echo "=== SpendingSaver Offline Desktop Build ==="

echo ""
echo "=== [1/5] Building Python backend standalone (PyInstaller) ==="
(cd "$BACKEND" && "$PYTHON" -m PyInstaller backend-service.spec --noconfirm --distpath dist-offline --workpath build-offline 2>&1 | tail -3)
BUNDLED_BACKEND="$BACKEND/dist-offline/backend-service"
[ -d "$BUNDLED_BACKEND" ] && echo "Backend bundle OK: $BUNDLED_BACKEND" || { echo "ERROR: backend bundle missing"; exit 1; }

echo ""
echo "=== [2/5] Building Go service binary ==="
(cd "$GO_SERVICE" && go build -o go-service.exe . 2>&1 | tail -3)

echo ""
echo "=== [3/5] Building frontend (Vite) ==="
(cd "$ROOT/tauri-app" && npm run build 2>&1 | tail -3)

echo ""
echo "=== [4/5] Building Tauri release binary ==="
(cd "$TAURI" && cargo build --release 2>&1 | tail -3)

echo ""
echo "=== [5/5] Assembling self-contained package next to the exe ==="
RELEASE_DIR="$TAURI/target/release"
rm -rf "$RELEASE_DIR/backend-service"
cp -r "$BUNDLED_BACKEND" "$RELEASE_DIR/backend-service"    # -> release/backend-service/backend-service.exe
cp "$GO_SERVICE/go-service.exe" "$RELEASE_DIR/go-service.exe"

echo ""
echo "=== Offline build complete ==="
echo "Executable: $RELEASE_DIR/spendingsaver.exe"
echo "Bundled (no Python/Go install needed):"
echo "  - $RELEASE_DIR/backend-service/backend-service.exe"
echo "  - $RELEASE_DIR/go-service.exe"