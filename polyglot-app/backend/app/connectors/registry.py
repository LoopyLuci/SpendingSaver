"""
Registers connector subclasses automatically.

Discovery walks every module inside `app.connectors`, imports it, and registers
any class that subclasses Connector (excluding the abstract base itself).
This means a new connector is picked up just by adding a module to the package.
"""
import importlib
import pkgutil
from typing import Dict, List, Optional

from app.connectors import Connector


class ConnectorRegistry:
    """Holds all discovered connectors, keyed by connector name."""

    def __init__(self, auto_discover: bool = True):
        self._connectors: Dict[str, Connector] = {}
        if auto_discover:
            self.discover()

    def discover(self) -> None:
        """Import every submodule of app.connectors and register connectors."""
        import app.connectors as pkg

        # Skip infrastructure modules that are not themselves connectors.
        skip = {"api", "registry", "_ingest", "base"}

        for module_info in pkgutil.iter_modules(pkg.__path__):
            if module_info.name in skip or module_info.name.startswith("_"):
                continue
            module = importlib.import_module(f"{pkg.__name__}.{module_info.name}")
            for attr in vars(module).values():
                if (
                    isinstance(attr, type)
                    and issubclass(attr, Connector)
                    and attr is not Connector
                    and getattr(attr, "name", None)
                ):
                    self._connectors[attr.name] = attr()

    def register(self, connector: Connector) -> None:
        self._connectors[connector.name] = connector

    def get(self, name: str) -> Optional[Connector]:
        return self._connectors.get(name)

    def all(self) -> List[Connector]:
        return list(self._connectors.values())

    def list(self) -> List[Dict]:
        """Serializable summary of installed connectors (public metadata)."""
        return sorted((c.describe() for c in self.all()), key=lambda d: d["name"])

    def __contains__(self, name: str) -> bool:
        return name in self._connectors


# Module-level singleton so the whole app shares one registry.
registry = ConnectorRegistry()
