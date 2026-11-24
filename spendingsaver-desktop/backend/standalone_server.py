"""
Standalone entry point for the SpendingSaver backend.
Used by PyInstaller to build a bundled backend-service.exe so the desktop
app can run fully offline without a system Python install.
"""
import os
import sys

# Ensure the app package is importable when frozen or run from source.
_HERE = os.path.dirname(os.path.abspath(__file__))
_APP_DIR = os.path.dirname(_HERE)  # backend/
if _APP_DIR not in sys.path:
    sys.path.insert(0, _APP_DIR)
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import uvicorn


def main() -> None:
    host = os.environ.get("SS_BIND_HOST", "0.0.0.0")  # LAN-accessible private server
    port = int(os.environ.get("SS_PORT", "8125"))
    # Allow overrides for testing / sidecar
    argv = sys.argv[1:]
    i = 0
    while i < len(argv):
        if argv[i] == "--host" and i + 1 < len(argv):
            host = argv[i + 1]
            i += 2
        elif argv[i] == "--port" and i + 1 < len(argv):
            port = int(argv[i + 1])
            i += 2
        else:
            i += 1

    uvicorn.run(
        "app.api:app",
        host=host,
        port=port,
        reload=False,
        # Rotate access/error logs daily to the data/logs dir so disks don't fill.
        log_config=_make_log_config(),
    )


def _make_log_config() -> dict:
    """Uvicorn logging config with date-based rotating file handlers + console."""
    import logging.config
    logs_dir = os.path.join(os.path.dirname(_HERE), "data", "logs")
    os.makedirs(logs_dir, exist_ok=True)
    return {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {
            "default": {"format": "%(asctime)s %(levelname)s %(name)s %(message)s"},
            "access": {"format": "%(asctime)s %(levelname)s %(message)s"},
        },
        "handlers": {
            "console": {
                "class": "logging.StreamHandler",
                "formatter": "default",
                "level": "INFO",
            },
            "file": {
                "class": "logging.handlers.TimedRotatingFileHandler",
                "filename": os.path.join(logs_dir, "backend.log"),
                "when": "midnight",
                "backupCount": 14,  # keep 2 weeks of rotated logs
                "formatter": "default",
                "level": "INFO",
            },
        },
        "loggers": {
            "uvicorn": {"handlers": ["console", "file"], "level": "INFO", "propagate": False},
            "uvicorn.error": {"handlers": ["console", "file"], "level": "INFO", "propagate": False},
            "uvicorn.access": {"handlers": ["console", "file"], "level": "INFO", "propagate": False},
        },
    }


if __name__ == "__main__":
    main()
