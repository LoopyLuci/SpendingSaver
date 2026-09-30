"""SpendingSaver MCP Client - HTTP client for backend API."""
import os
import requests
from typing import Any

API_BASE = os.environ.get("SS_API_URL", "http://127.0.0.1:8125")
TOKEN = os.environ.get("SS_MCP_TOKEN", "")

def _headers() -> dict:
    h = {"Content-Type": "application/json"}
    if TOKEN:
        h["Authorization"] = f"Bearer {TOKEN}"
    return h

def api_get(path: str, params: dict = None) -> Any:
    r = requests.get(f"{API_BASE}{path}", headers=_headers(), params=params, timeout=10)
    r.raise_for_status()
    return r.json()

def api_post(path: str, data: dict = None) -> Any:
    r = requests.post(f"{API_BASE}{path}", headers=_headers(), json=data, timeout=10)
    r.raise_for_status()
    return r.json()

def api_put(path: str, data: dict = None) -> Any:
    r = requests.put(f"{API_BASE}{path}", headers=_headers(), json=data, timeout=10)
    r.raise_for_status()
    return r.json()

def api_delete(path: str) -> Any:
    r = requests.delete(f"{API_BASE}{path}", headers=_headers(), timeout=10)
    r.raise_for_status()
    return r.json()
