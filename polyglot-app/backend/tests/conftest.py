"""Run the backend tests against a throwaway database and data directory.

This must run before ``app.database`` is imported, so the tests never touch
the developer's real ``backend/data`` directory (database, pairing key).
"""
import os
import tempfile

_TMP = tempfile.mkdtemp(prefix="ss-backend-tests-")
os.environ["SS_DB_PATH"] = os.path.join(_TMP, "spendingsaver.db")
os.environ["SS_DATA_DIR"] = _TMP
os.environ["SS_TESTING"] = "1"  # the in-process test client counts as the local machine

# The per-IP rate limiter (100 requests/minute) would otherwise trip on the
# volume of requests the whole suite makes from the single test client.
from app.security import rate_limiter  # noqa: E402

rate_limiter.max_requests = 1_000_000
