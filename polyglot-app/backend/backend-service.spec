# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec for SpendingSaver backend -> backend-service.exe
# Build: pyinstaller backend-service.spec --noconfirm
import os

block_cipher = None

# Paths
BACKEND_DIR = os.path.dirname(os.path.abspath(SPEC))
APP_DIR = os.path.join(BACKEND_DIR, "app")

a = Analysis(
    ["standalone_server.py"],
    pathex=[BACKEND_DIR, APP_DIR],
    binaries=[],
    datas=[
        # Bundle the connectors + modules packages so runtime auto-discovery works.
        (os.path.join(APP_DIR, "connectors"), os.path.join("app", "connectors")),
        (os.path.join(APP_DIR, "modules"), os.path.join("app", "modules")),
    ],
    hiddenimports=[
        "app.api",
        "app.api_core",
        "app.api_backend",
        "app.auth",
        "app.bank_import",
        "app.database",
        "app.db_encryption",
        "app.email_service",
        "app.exchange_rates",
        "app.outbox",
        "app.registry",
        "app.receipt_ocr",
        "app.security",
        "app.vault",
        "app.hosting",
        "app.connectors",
        "app.connectors.registry",
        "app.connectors.manual",
        "app.connectors.csv",
        "app.connectors.ofx",
        "app.connectors.open_banking",
        "app.connectors._ingest",
        "app.modules",
        "app.modules.insights",
        "app.modules.sync",
        "uvicorn",
        "uvicorn.logging",
        "uvicorn.loops",
        "uvicorn.loops.auto",
        "uvicorn.protocols",
        "uvicorn.protocols.http",
        "uvicorn.protocols.http.auto",
        "uvicorn.protocols.http.h11_impl",
        "uvicorn.protocols.websockets",
        "uvicorn.protocols.websockets.auto",
        "uvicorn.protocols.websockets.websockets_impl",
        "uvicorn.lifespan",
        "uvicorn.lifespan.on",
        "uvicorn.middleware",
        "fastapi",
        "starlette",
        "sqlalchemy",
        "pydantic",
        "multipart",
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="backend-service",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="backend-service",
)
