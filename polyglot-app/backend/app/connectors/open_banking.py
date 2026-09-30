"""
OpenBankingConnector - scaffold for Open Banking / Plaid-style OAuth bank links.

This is intentionally a future-proof scaffold: it establishes the storage and
flow for linking a real bank account via OAuth (Open Banking / Plaid), but the
live sync is not implemented yet.

- `connect(credentials)` stores the customer_secret / access_token in the
  encrypted vault (app.vault.Vault) so secrets never touch the DB in plaintext.
- `fetch_transactions(params)` reports NOT_CONFIGURED until a valid access
  token is present in the vault. Drop in the real bank-API fetch here later.
"""
from typing import Dict, List

from app.connectors import Connector
from app.vault import Vault, CONNECTOR_ENTITY

NOT_CONFIGURED = "NOT_CONFIGURED"


class OpenBankingConnector(Connector):
    name = "open_banking"
    description = (
        "Open Banking / Plaid-style bank account linking via OAuth "
        "(scaffold - real sync not yet connected)."
    )
    category = "bank"
    supports_oauth = True

    def __init__(self):
        self._vault = Vault()

    def connect(self, credentials: Dict) -> Dict:
        # Persist any supplied secrets in the encrypted vault.
        stored = False
        if credentials:
            self._vault.store(CONNECTOR_ENTITY, self.name, credentials)
            stored = True
        return {"connected": True, "credentials_stored": stored}

    def _token_available(self, params: Dict) -> bool:
        # The API layer attaches this entity's stored credentials under
        # params['credentials']; fall back to the connector's own vault row.
        creds = params.get("credentials")
        if creds is None:
            creds = self._vault.retrieve(CONNECTOR_ENTITY, self.name)
        token = creds.get("access_token") or creds.get("token") or creds.get("customer_secret")
        return bool(token)

    def fetch_transactions(self, params: Dict) -> List[Dict]:
        if not self._token_available(params):
            # No real credentials supplied yet - signal that clearly.
            return [{
                "error": NOT_CONFIGURED,
                "message": "Open Banking connector has no stored access token. "
                           "POST /api/connectors/open_banking/connect with "
                           "customer_secret / access_token to configure.",
            }]

        # Real bank-API fetch goes here in the future. Credentials are
        # available via: params.get('credentials', {})
        return []
