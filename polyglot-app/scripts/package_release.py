import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TAURI = ROOT / "tauri-app"
RELEASE = ROOT / "release"


def run(cmd, cwd=None):
    subprocess.run(cmd, shell=True, cwd=cwd or ROOT, check=True)


def main() -> int:
    RELEASE.mkdir(exist_ok=True)
    run("npm run build", cwd=TAURI)
    run("cargo tauri build", cwd=TAURI)
    target_dir = TAURI / "src-tauri" / "target" / "release"
    src_bin = TAURI / "src-tauri" / "target" / "release" / "tauri-app.exe"
    if not src_bin.exists():
        print(f"missing release binary: {src_bin}")
        return 1
    shutil.copy2(src_bin, RELEASE / "PolyglotApp.exe")
    for item in (TAURI / "dist").iterdir():
        if item.is_dir():
            shutil.copytree(item, RELEASE / item.name, dirs_exist_ok=True)
        else:
            shutil.copy2(item, RELEASE / item.name)
    bundle_src = TAURI / "src-tauri" / "target" / "release" / "bundle" / "nsis"
    if bundle_src.exists():
        for item in bundle_src.iterdir():
            if item.is_file():
                shutil.copy2(item, RELEASE / item.name)
    print(f"packaged release to {RELEASE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
