import json
import logging
import sys
import threading
import time
from pathlib import Path
from logging.handlers import RotatingFileHandler

BASE_DIR = Path(__file__).resolve().parent.parent
LOG_DIR = BASE_DIR / "logs"
LOG_DIR.mkdir(exist_ok=True)
LOG_FILE = LOG_DIR / "spendingsaver.json"


class JsonFormatter(logging.Formatter):
    def format(self, record):
        log = {
            "ts": self.formatTime(record, self.datefmt),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info:
            log["exc_info"] = self.formatException(record.exc_info)
        return json.dumps(log)


def enable_json_logging(stream=None):
    handler = RotatingFileHandler(LOG_FILE, maxBytes=5_000_000, backupCount=3, encoding="utf-8")
    handler.setFormatter(JsonFormatter())
    logger = logging.getLogger()
    logger.setLevel(logging.INFO)
    logger.addHandler(handler)
    if stream:
        handler2 = logging.StreamHandler(stream)
        handler2.setFormatter(JsonFormatter())
        logger.addHandler(handler2)


def log_event(msg: str, **ctx):
    logger = logging.getLogger("spendingsaver")
    ctx["msg"] = msg
    logger.info(json.dumps(ctx))
