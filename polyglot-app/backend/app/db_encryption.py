"""
SQLite encryption support for SpendingSaver.
Uses pysqlcipher3 if available, otherwise falls back to standard sqlite3.
"""
import os
import sys

DB_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "spendingsaver.db")
ENCRYPTION_KEY = os.environ.get("SS_DB_KEY", "")

def get_encrypted_engine():
    if ENCRYPTION_KEY:
        try:
            from pysqlcipher3 import dbapi2 as sqlite3
            from sqlalchemy import create_engine
            def connect(conn):
                conn.execute(f"PRAGMA key = '{ENCRYPTION_KEY}'")
            engine = create_engine(f"sqlite:///{DB_PATH}", module=sqlite3, connect_args={"check_same_thread": False})
            return engine
        except ImportError:
            pass
    
    from sqlalchemy import create_engine
    return create_engine(f"sqlite:///{DB_PATH}", connect_args={"check_same_thread": False})

def encrypt_existing_db(key: str) -> bool:
    try:
        from pysqlcipher3 import dbapi2 as sqlite3
        import shutil
        
        temp_path = DB_PATH + ".encrypted"
        
        src = sqlite3.connect(DB_PATH)
        dst = sqlite3.connect(temp_path)
        dst.execute(f"PRAGMA key = '{key}'")
        
        src.backup(dst)
        src.close()
        dst.close()
        
        backup_path = DB_PATH + ".backup"
        shutil.move(DB_PATH, backup_path)
        shutil.move(temp_path, DB_PATH)
        
        return True
    except Exception:
        return False

def verify_db_access(key: str) -> bool:
    try:
        from pysqlcipher3 import dbapi2 as sqlite3
        conn = sqlite3.connect(DB_PATH)
        conn.execute(f"PRAGMA key = '{key}'")
        result = conn.execute("SELECT count(*) FROM sqlite_master").fetchone()
        conn.close()
        return result is not None
    except Exception:
        return False
