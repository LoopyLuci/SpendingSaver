"""Module registry package.

Drop a new module file (e.g. app/modules/insights.py, app/modules/sync.py) into
this directory. Each module exposes an `APIRouter` named `router` (and
optionally a `name` / `enabled`). The ModuleRegistry in app/registry.py
auto-discovers every module here and attaches its routes to the FastAPI app, so
new modules never touch app/api.py.
"""

# Ordered list of module names to load (in discovery order). Omit a name to
# exclude it; keep it out of the list to disable a module.
MODULES = [
    "insights",
    "sync",
    "sync_v1",
]
