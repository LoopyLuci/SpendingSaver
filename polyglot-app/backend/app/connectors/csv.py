"""
CsvConnector - imports transactions from a CSV file.

Wraps the existing CSV import behavior as a connector: pass the raw CSV text
in `params['content']` and the connector parses it into normalized records
and inserts them into the database.
"""
import csv
import io
from typing import Dict, List

from app.connectors import Connector, ConnectorError


class CsvConnector(Connector):
    name = "csv"
    description = "Import transactions from a CSV file."
    category = "file"
    supports_oauth = False

    def connect(self, credentials: Dict) -> Dict:
        # A file connector has nothing to connect.
        return {"connected": True}

    def _parse(self, content: str, account_id: str = "") -> List[Dict]:
        reader = csv.DictReader(io.StringIO(content))
        if not reader.fieldnames:
            raise ConnectorError("CSV has no header row.", code="CSV_NO_HEADER")

        def pick(row, *keys):
            lowered = {k.strip().lower(): v for k, v in row.items()}
            for k in keys:
                for actual, val in lowered.items():
                    if actual == k:
                        return val
            return None

        records = []
        for row in reader:
            amount_raw = pick(row, "amount", "value", "amt", "transaction amount")
            date = pick(row, "date", "transaction date", "posted date", "dtposted")
            description = (
                pick(row, "description", "memo", "name", "merchant", "payee", "details")
                or ""
            )
            category = pick(row, "category", "categoryname") or ""
            if amount_raw is None or date is None or str(amount_raw).strip() == "":
                continue
            try:
                amount = float(str(amount_raw).replace("$", "").replace(",", "").strip())
            except ValueError:
                continue
            records.append({
                "account": account_id or pick(row, "account", "account name") or "",
                "amount": amount,
                "description": str(description).strip(),
                "date": str(date).strip(),
                "category": str(category).strip() or None,
                "raw_merchant": str(description).strip() or None,
            })
        return records

    def fetch_transactions(self, params: Dict) -> List[Dict]:
        content = params.get("content") or params.get("content_text")
        if not content:
            raise ConnectorError("No CSV content provided (params['content']).", code="CSV_NO_CONTENT")
        account_id = params.get("account_id") or params.get("account") or ""
        return self._parse(content, account_id=account_id)
