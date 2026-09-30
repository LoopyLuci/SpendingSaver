"""
Universal, pluggable data-connector layer for SpendingSaver.

A Connector pulls account data from a source (a bank, a file format, Open
Banking / Plaid-style APIs) or documents manual entry. Every connector:

  - exposes a stable `name`, `description` and `supports_oauth` flag
  - implements `connect(credentials)` to establish/link the source
  - implements `fetch_transactions(params)` returning a list of normalized
    transaction records with these fields:
        account     (account name/identifier)
        amount      (float)
        description (string)
        date        (ISO 'YYYY-MM-DD')
        category    (optional category name)
        raw_merchant(optional raw merchant/description from the source)
"""
from abc import ABC, abstractmethod
from typing import List, Dict


class ConnectorError(Exception):
    """Raised when a connector cannot complete an operation."""

    def __init__(self, message: str, code: str = "CONNECTOR_ERROR"):
        super().__init__(message)
        self.code = code


class Connector(ABC):
    """Base class all SpendingSaver data connectors implement."""

    name: str = "base"
    description: str = ""
    category: str = "generic"
    supports_oauth: bool = False

    @abstractmethod
    def connect(self, credentials: Dict) -> Dict:
        """Establish the connection / link using the given credentials.

        Returns a small status dict (e.g. {"connected": True}).
        Connectors that need to persist secrets should do so in their own way
        (OpenBankingConnector stores them in the encrypted vault).
        """
        raise NotImplementedError

    @abstractmethod
    def fetch_transactions(self, params: Dict) -> List[Dict]:
        """Fetch and normalize transactions.

        `params` carries connector-specific inputs (file content, account id,
        date range, previously stored credentials, etc.).

        Returns a list of normalized transaction dicts (see module docstring).
        """
        raise NotImplementedError

    def describe(self) -> Dict:
        return {
            "name": self.name,
            "description": self.description,
            "category": self.category,
            "supports_oauth": self.supports_oauth,
        }
