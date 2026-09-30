# -*- mode: python ; coding: utf-8 -*-
"""
PyInstaller hook for the SpendingSaver app package.
Ensures dynamically-discovered submodules (connectors, modules, etc.) are
bundled, since they are loaded via pkgutil.iter_modules at runtime.
"""
from PyInstaller.utils.hooks import collect_submodules

hiddenimports = []

# Connector and module registries use pkgutil.iter_modules() at runtime to
# auto-discover implementations. Bundle every submodule so none are missed.
hiddenimports += collect_submodules("app.connectors")
hiddenimports += collect_submodules("app.modules")