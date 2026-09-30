"""
Connector API endpoints.

Registers, connects to, and invokes data connectors.

  GET  /api/connectors                     - list installed connectors
  POST /api/connectors/{name}/connect      - store credentials in the vault
  POST /api/connectors/{name}/fetch        - fetch transactions into the DB
"""
from typing import Dict, Optional

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel

from app.connectors.registry import registry
from app.connectors._ingest import insert_transactions
from app.vault import Vault, CONNECTOR_ENTITY
from app.sync_security import require_sync_access

router = APIRouter()

# One shared vault instance encrypts all connector credentials.
vault = Vault()


class ConnectRequest(BaseModel):
    credentials: Dict = {}
    entity_id: Optional[str] = None  # optional per-account logical id


class FetchRequest(BaseModel):
    params: Dict = {}
    account_id: Optional[str] = None
    entity_id: Optional[str] = None
    # Parse-only mode: return normalized records without persisting them.
    # Used by the frontend CSV import preview step.
    dry_run: Optional[bool] = False


def _connector_or_404(name: str):
    connector = registry.get(name)
    if connector is None:
        raise HTTPException(status_code=404, detail=f"connector '{name}' not found")
    return connector


@router.get("/api/connectors")
def list_connectors():
    return registry.list()


@router.post("/api/connectors/{name}/connect")
def connect_connector(name: str, payload: ConnectRequest, _: bool = Depends(require_sync_access)):
    connector = _connector_or_404(name)
    entity_id = payload.entity_id or name

    # Universal credential storage in the encrypted vault, then let the
    # connector perform any source-specific linking.
    if payload.credentials:
        vault.store(CONNECTOR_ENTITY, entity_id, payload.credentials)
    try:
        result = connector.connect(payload.credentials)
    except Exception as exc:  # noqa: BLE001 - surface as a clean 400
        raise HTTPException(status_code=400, detail=f"connect failed: {exc}") from exc

    return {
        "name": name,
        "entity_id": entity_id,
        "credentials_stored": bool(payload.credentials),
        **result,
    }


@router.post("/api/connectors/{name}/fetch")
def fetch_connector(name: str, payload: FetchRequest, _: bool = Depends(require_sync_access)):
    connector = _connector_or_404(name)
    entity_id = payload.entity_id or name

    # Pass previously stored credentials to the connector so stateful sources
    # (e.g. Open Banking) can authenticate. Always attach the key (even empty)
    # so connectors know which entity context they are operating in.
    creds = vault.retrieve(CONNECTOR_ENTITY, entity_id)
    params = dict(payload.params)
    params.setdefault("credentials", creds or {})
    if payload.account_id and "account_id" not in params:
        params["account_id"] = payload.account_id

    try:
        records = connector.fetch_transactions(params)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001 - surface as a clean 400
        raise HTTPException(status_code=400, detail=f"fetch failed: {exc}") from exc

    if not isinstance(records, list):
        raise HTTPException(status_code=400, detail="connector returned unexpected result")

    # Surface connector-level errors (e.g. Open Banking NOT_CONFIGURED).
    for rec in records:
        if isinstance(rec, dict) and rec.get("error"):
            raise HTTPException(status_code=400, detail=rec.get("message") or rec["error"])

    # Parse-only dry-run: return normalized records without writing to the DB.
    # The frontend uses this to preview an import before confirming it.
    if payload.dry_run:
        return {
            "name": name,
            "entity_id": entity_id,
            "dry_run": True,
            "fetched": len(records),
            "items": records,
        }

    # Persist fetched records into the database.
    try:
        created = insert_transactions(
            records,
            user_id=params.get("user_id"),
            account_id=payload.account_id or params.get("account_id"),
            source=name,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 - surface as a clean 400
        raise HTTPException(status_code=400, detail=f"fetch failed: {exc}") from exc
    return {
        "name": name,
        "entity_id": entity_id,
        "fetched": len(records),
        "items": created,
    }
