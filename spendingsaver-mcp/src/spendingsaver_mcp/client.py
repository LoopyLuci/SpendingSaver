"""HTTP client for the SpendingSaver backend, used by the MCP server.

Every backend call made by the MCP adapter goes through ``request`` so that
the base URL and bearer token are resolved in exactly one place.

Configuration is read from the environment on every call:

- ``SS_API_URL``: backend base URL (default ``http://127.0.0.1:8125``).
- ``SS_MCP_TOKEN``: optional bearer token, sent as ``Authorization: Bearer``.
  It is never logged or included in error messages.
"""
import os
from typing import Any

import requests

DEFAULT_API_URL = "http://127.0.0.1:8125"
TIMEOUT_SECONDS = 10


def api_base() -> str:
    return os.environ.get("SS_API_URL", DEFAULT_API_URL).rstrip("/")


def _headers() -> dict:
    headers = {"Content-Type": "application/json"}
    token = os.environ.get("SS_MCP_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def request(method: str, path: str, params: dict | None = None, data: Any = None) -> Any:
    """Call the backend and return the decoded JSON body.

    ``params`` are sent as the query string (what the backend's GET routes
    read); ``data`` is sent as a JSON body. Raises ``requests`` exceptions on
    transport errors and non-2xx responses.
    """
    r = requests.request(
        method,
        f"{api_base()}{path}",
        headers=_headers(),
        params=params,
        json=data,
        timeout=TIMEOUT_SECONDS,
    )
    r.raise_for_status()
    return r.json()


def api_get(path: str, params: dict = None) -> Any:
    return request("GET", path, params=params)


def api_post(path: str, data: dict = None) -> Any:
    return request("POST", path, data=data)


def api_put(path: str, data: dict = None) -> Any:
    return request("PUT", path, data=data)


def api_delete(path: str) -> Any:
    return request("DELETE", path)
