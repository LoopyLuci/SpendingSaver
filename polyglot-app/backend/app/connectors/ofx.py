"""
OfxConnector - imports transactions from OFX (.ofx/.qfx) and QIF files.

Wraps the existing parser in app/bank_import.py (parse_ofx / parse_qif) as a
connector. Pass the raw file content in `params['content']` and optionally the
filename in `params['filename']` to let the connector pick the right parser.
"""
from typing import Dict, List

from app.connectors import Connector, ConnectorError
from app.bank_import import parse_ofx, parse_qif


class OfxConnector(Connector):
    name = "ofx"
    description = "Import transactions from OFX/QFX or QIF bank files."
    category = "file"
    supports_oauth = False

    def connect(self, credentials: Dict) -> Dict:
        # A file connector has nothing to connect.
        return {"connected": True}

    def _parse(self, content: str, filename: str = "") -> List[Dict]:
        lower = (filename or "").lower()
        if lower.endswith(".qif"):
            parsed = parse_qif(content)
        else:
            parsed = parse_ofx(content)

        records = []
        for t in parsed:
            amount = t.get("amount")
            date = t.get("date")
            if amount is None or not date:
                continue
            records.append({
                "account": "",
                "amount": float(amount),
                "description": str(t.get("description") or ""),
                "date": str(date),
                "category": str(t.get("category") or "") or None,
                "raw_merchant": str(t.get("description") or "") or None,
            })
        return records

    def fetch_transactions(self, params: Dict) -> List[Dict]:
        content = params.get("content")
        if not content:
            raise ConnectorError("No file content provided (params['content']).", code="OFX_NO_CONTENT")
        filename = params.get("filename") or params.get("name") or ""
        return self._parse(content, filename=filename)
