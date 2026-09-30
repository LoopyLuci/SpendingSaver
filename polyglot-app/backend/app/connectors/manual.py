"""
ManualConnector - represents manual data entry.

A connector that documents that transactions are entered by hand (via the
existing manual transaction endpoints or direct UI forms). It performs no
automatic fetch and returns nothing.
"""
from typing import Dict, List

from app.connectors import Connector


class ManualConnector(Connector):
    name = "manual"
    description = "Manual data entry - transactions are entered by hand."
    category = "manual"
    supports_oauth = False

    def connect(self, credentials: Dict) -> Dict:
        # Nothing to establish for manual entry.
        return {"connected": True, "message": "Manual entry needs no connection."}

    def fetch_transactions(self, params: Dict) -> List[Dict]:
        # No-op by design: there is no automated source to pull from.
        return []
