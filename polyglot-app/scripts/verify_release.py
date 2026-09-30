#!/usr/bin/env python3
"""Verify the polyglot app stack and release artifacts."""
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TAURI = ROOT / "tauri-app"
RELEASE = ROOT / "release"
REQUIRED_COMMANDS = ["node", "npm", "cargo", "go"]


def check(cmd: str) -> bool:
    return shutil.which(cmd) is not None


def run(cmd: str, cwd=None) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, shell=True, cwd=cwd or ROOT, capture_output=True, text=True)


def main() -> int:
    failed = False
    print("== prerequisites ==")
    for cmd in REQUIRED_COMMANDS:
        if check(cmd):
            print(f"[ok] {cmd}")
        else:
            print(f"[missing] {cmd}")
            failed = True

    print("\n== frontend tests ==")
    r = run("npm run test", cwd=TAURI)
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== frontend build ==")
    r = run("npm run build", cwd=TAURI)
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== rust release tests ==")
    r = run("cargo test --release --test smoke_test", cwd=TAURI / "src-tauri")
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== rust release build ==")
    r = run("cargo build --release", cwd=TAURI / "src-tauri")
    print(r.stdout)
    if r.returncode != 0:
        print(r.stderr)
        failed = True

    print("\n== release artifacts ==")
    target_dir = TAURI / "src-tauri" / "target" / "release"
    binaries = sorted(target_dir.glob("*.exe"))
    if binaries:
        for b in binaries:
            print(f"[bin] {b}")
    else:
        print("[warn] no .exe binaries found under target/release")
        print("[info] listing candidate files instead:")
        for p in sorted(target_dir.iterdir())[:20]:
            print(f"       {p.name}")
        failed = True

    bundle_dir = TAURI / "src-tauri" / "target" / "release" / "bundle" / "nsis"
    if bundle_dir.exists():
        for p in sorted(bundle_dir.iterdir()):
            if p.is_file():
                print(f"[bundle] {p}")

    msi_dir = TAURI / "src-tauri" / "target" / "release" / "bundle" / "msi"
    if msi_dir.exists():
        for p in sorted(msi_dir.iterdir()):
            if p.is_file():
                print(f"[msi] {p}")

    if shutil.which("signtool"):
        print("\n== signtool ==")
        print("[ok] signtool found on PATH")
        sig = subprocess.run(
            ["signtool", "verify", "/pa", str(TAURI / "src-tauri" / "target" / "release" / "tauri-app.exe")],
            capture_output=True,
            text=True,
        )
        print(sig.stdout)
        if sig.returncode != 0:
            print("[warn] signtool verify returned non-zero; output above")
            print(sig.stderr)
        else:
            print("[ok] binary signature valid")

    if RELEASE.exists():
        print(f"[ok] release dir: {RELEASE}")
        for p in sorted(RELEASE.iterdir()):
            print(f"     {p.name}")
    else:
        print("[missing] release/ directory")

    if failed:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
