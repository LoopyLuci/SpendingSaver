#!/usr/bin/env python3
"""Launch the polyglot desktop app stack."""
import subprocess
import sys
import time
import requests
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # polyglot-app/
PYTHON_PORT = 8125
GO_PORT = 8126


def is_listening(port: int) -> bool:
    try:
        requests.get(f"http://127.0.0.1:{port}/health", timeout=1)
        return True
    except requests.RequestException:
        return False


def start_backend():
    backend_dir = ROOT / "backend"
    if is_listening(PYTHON_PORT):
        print("Python backend already running")
        return None
    print("Starting Python backend...")
    return subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "app.api:app", "--host", "127.0.0.1", "--port", str(PYTHON_PORT)],
        cwd=backend_dir,
    )


def start_go():
    go_dir = ROOT / "go-service"
    if is_listening(GO_PORT):
        print("Go service already running")
        return None
    print("Starting Go service...")
    go_bin = go_dir / "go-service.exe" if sys.platform == "win32" else go_dir / "go-service"
    if not go_bin.exists():
        subprocess.run(["go", "build", "-o", go_bin.name], cwd=go_dir, check=True)
    return subprocess.Popen([str(go_bin)], cwd=go_dir)


def main() -> int:
    procs = []
    try:
        procs = [p for p in [start_backend(), start_go()] if p is not None]
        if procs:
            time.sleep(1.5)
        print(f"Python: {f'http://127.0.0.1:{PYTHON_PORT}' if is_listening(PYTHON_PORT) else 'offline'}")
        print(f"Go: {f'http://127.0.0.1:{GO_PORT}' if is_listening(GO_PORT) else 'offline'}")
        print("Open http://localhost:5173 for the web frontend")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nShutting down...")
            return 0
    finally:
        for p in procs:
            p.terminate()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
