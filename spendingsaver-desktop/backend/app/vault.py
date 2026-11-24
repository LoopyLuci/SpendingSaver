"""
Encrypted credential vault for SpendingSaver connectors.

Encrypts connector credentials (customer_secret, OAuth tokens, bank API keys)
at rest using Fernet (from the `cryptography` package). The encryption key is
derived from the SS_VAULT_KEY env var, falling back to a generated key
persisted in data/vault.key (chmod 600).

Credentials are stored in the `vault_credentials` table keyed by
(entity_type, entity_id) so each connector / account has its own row.
"""
import hashlib
import os
import json
import base64
import stat
import uuid
from datetime import datetime

from cryptography.fernet import Fernet

from app.database import SessionLocal, VaultCredentialModel

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
KEY_FILE = os.path.join(DATA_DIR, "vault.key")

# entity_type used for connector-level credentials (name -> entity_id)
CONNECTOR_ENTITY = "connector"


def _to_fernet_key(secret: str) -> bytes:
    """Coerce an arbitrary secret string into a valid url-safe Fernet key."""
    secret_bytes = secret.encode("utf-8")
    try:
        Fernet(secret_bytes)
        return secret_bytes
    except Exception:
        # Not already a valid Fernet key: hash it down to 32 bytes
        # and base64-encode to produce a valid key.
        digest = hashlib.sha256(secret_bytes).digest()
        return base64.urlsafe_b64encode(digest)


def _load_or_create_key() -> bytes:
    """Read the persisted vault key, generating + persisting one if absent."""
    os.makedirs(DATA_DIR, exist_ok=True)
    if os.path.exists(KEY_FILE):
        with open(KEY_FILE, "rb") as f:
            return f.read()

    key = Fernet.generate_key()
    with open(KEY_FILE, "wb") as f:
        f.write(key)
    try:
        # Restrict permissions to owner-only where the platform supports it.
        os.chmod(KEY_FILE, stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass
    return key


class Vault:
    """Encrypts/decrypts connector credentials and persists them in the DB."""

    def __init__(self, key: str = None):
        if key:
            self._fernet = Fernet(_to_fernet_key(key))
        else:
            self._fernet = Fernet(_load_or_create_key())

    # -- core crypto -------------------------------------------------------
    def encrypt(self, plaintext: str) -> str:
        return self._fernet.encrypt(plaintext.encode("utf-8")).decode("utf-8")

    def decrypt(self, token: str) -> str:
        return self._fernet.decrypt(token.encode("utf-8")).decode("utf-8")

    def encrypt_dict(self, data: dict) -> str:
        return self.encrypt(json.dumps(data))

    def decrypt_dict(self, token: str) -> dict:
        return json.loads(self.decrypt(token))

    # -- persistence -------------------------------------------------------
    def store(self, entity_type: str, entity_id: str, credentials: dict) -> dict:
        """Encrypt and persist credentials for (entity_type, entity_id)."""
        encrypted = self.encrypt_dict(credentials)
        db = SessionLocal()
        try:
            row = None
            for r in db.query(VaultCredentialModel).filter_by(
                entity_type=entity_type, entity_id=entity_id
            ):
                row = r
                break
            now = datetime.utcnow().isoformat()
            if row is None:
                row = VaultCredentialModel(
                    id=str(uuid.uuid4()),
                    entity_type=entity_type,
                    entity_id=entity_id,
                    encrypted_data=encrypted,
                    created_at=now,
                    updated_at=now,
                )
                db.add(row)
            else:
                row.encrypted_data = encrypted
                row.updated_at = now
            db.commit()
            return {"stored": True, "entity_type": entity_type, "entity_id": entity_id}
        finally:
            db.close()

    def retrieve(self, entity_type: str, entity_id: str) -> dict:
        """Decrypt and return credentials, or {} if none stored."""
        db = SessionLocal()
        try:
            row = None
            for r in db.query(VaultCredentialModel).filter_by(
                entity_type=entity_type, entity_id=entity_id
            ):
                row = r
                break
            if row is None:
                return {}
            return self.decrypt_dict(row.encrypted_data)
        finally:
            db.close()

    def delete(self, entity_type: str, entity_id: str) -> bool:
        db = SessionLocal()
        try:
            rows = db.query(VaultCredentialModel).filter_by(
                entity_type=entity_type, entity_id=entity_id
            ).all()
            for r in rows:
                db.delete(r)
            db.commit()
            return len(rows) > 0
        finally:
            db.close()
