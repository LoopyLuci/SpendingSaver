#!/usr/bin/env python3
"""Run the full integration test suite across all layers."""
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TAURI = ROOT / "tauri-app"
BACKEND = ROOT / "backend"
GO = ROOT / "go-service"


def run(cmd, cwd=None) -> subprocess.CompletedProcess:
    print(f"> {cmd}")
    return subprocess.run(cmd, shell=True, cwd=cwd or ROOT, capture_output=True, text=True)


def main() -> int:
    failed = False

    print("== python backend tests ==")
    r = run("python -m pytest tests/test_api.py -q", cwd=BACKEND)
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== go tests ==")
    r = run("go test ./...", cwd=GO)
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== frontend tests ==")
    r = run("npm run test", cwd=TAURI)
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    if failed:
        return 1
    print("\n[ok] integration test suite passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
