"""ModuleRegistry: auto-discovers app/modules/, imports each module, and
attaches its routes (module.router) to the FastAPI app.

New modules: add a file under app/modules/ exposing `router = APIRouter()`,
optionally add its name to app/modules/__init__.py MODULES, and it is wired in
automatically — no edits to app/api.py required.
"""
import importlib
import pkgutil

from fastapi import FastAPI

import app.modules as modules_pkg
from app.modules import MODULES as MODULE_NAMES


class ModuleRegistry:
    def __init__(self):
        self.modules = []
        self._loaded_names = set()

    def discover(self) -> list:
        """Import and register every module in app/modules/."""
        for mod in pkgutil.iter_modules(modules_pkg.__path__):
            name = mod.name
            if name.startswith("_"):
                continue
            if MODULE_NAMES and name not in MODULE_NAMES:
                continue
            self._load(name)
        return self.modules

    def register(self, name: str):
        """Explicitly register a single module by name (e.g. for demo/tests)."""
        return self._load(name)

    def _load(self, name: str):
        if name in self._loaded_names:
            return next((m for m in self.modules if m.__name__ == name), None)
        module = importlib.import_module(f"app.modules.{name}")
        self._loaded_names.add(name)
        self.modules.append(module)
        return module

    def attach(self, app: FastAPI):
        """Attach every discovered module's router to the given app."""
        for module in self.modules:
            router = getattr(module, "router", None)
            if router is not None:
                prefix = getattr(module, "prefix", "")
                app.include_router(router, prefix=prefix)
            elif hasattr(module, "register"):
                module.register(app)


registry = ModuleRegistry()
