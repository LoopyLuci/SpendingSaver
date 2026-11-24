//! SQLite storage. Opening is a few milliseconds, so the window never waits on it.
use crate::model::*;
use rusqlite::{params, Connection};
use std::path::{Path, PathBuf};

pub struct Db {
    conn: Connection,
}

pub fn default_path() -> PathBuf {
    let base = std::env::var_os("SPENDINGSAVER_DATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(|p| PathBuf::from(p).join("SpendingSaver")))
        .or_else(|| std::env::var_os("XDG_DATA_HOME").map(|p| PathBuf::from(p).join("spendingsaver")))
        .or_else(|| std::env::var_os("HOME").map(|p| PathBuf::from(p).join(".local/share/spendingsaver")))
        .unwrap_or_else(|| PathBuf::from("."));
    base.join("spendingsaver-desktop.db")
}

const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, balance REAL NOT NULL,
  currency TEXT NOT NULL DEFAULT 'USD', deleted INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS categories(id TEXT PRIMARY KEY, name TEXT NOT NULL, parent_id TEXT, color TEXT,
  deleted INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS transactions(id TEXT PRIMARY KEY, account_id TEXT NOT NULL, amount REAL NOT NULL,
  description TEXT NOT NULL, category_id TEXT, date TEXT NOT NULL, notes TEXT, created_at INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS budgets(id TEXT PRIMARY KEY, category_id TEXT NOT NULL, amount REAL NOT NULL,
  period TEXT NOT NULL DEFAULT 'monthly', start_date TEXT NOT NULL DEFAULT '', deleted INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(date);
CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions(account_id);
CREATE TABLE IF NOT EXISTS sync_log(seq INTEGER PRIMARY KEY AUTOINCREMENT, entity TEXT NOT NULL, entity_id TEXT NOT NULL,
  device_id TEXT, logged_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sync_devices(id TEXT PRIMARY KEY, name TEXT, token_hash TEXT NOT NULL, created_at INTEGER NOT NULL,
  last_seen INTEGER, revoked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS sync_ctx(one INTEGER PRIMARY KEY CHECK(one=1), device TEXT);
INSERT OR IGNORE INTO sync_ctx(one, device) VALUES(1, NULL);
CREATE TRIGGER IF NOT EXISTS log_accounts_insert AFTER INSERT ON accounts BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('account',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_accounts_update AFTER UPDATE ON accounts BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('account',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_categories_insert AFTER INSERT ON categories BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('category',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_categories_update AFTER UPDATE ON categories BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('category',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_transactions_insert AFTER INSERT ON transactions BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('transaction',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_transactions_update AFTER UPDATE ON transactions BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('transaction',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_budgets_insert AFTER INSERT ON budgets BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('budget',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
CREATE TRIGGER IF NOT EXISTS log_budgets_update AFTER UPDATE ON budgets BEGIN INSERT INTO sync_log(entity,entity_id,device_id,logged_at) VALUES('budget',NEW.id,(SELECT device FROM sync_ctx),CAST(strftime('%s','now') AS INTEGER)*1000); END;
";

impl Db {
    pub fn open(path: &Path) -> rusqlite::Result<Db> {
        if let Some(dir) = path.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        let conn = Connection::open(path)?;
        Self::init(conn)
    }

    pub fn memory() -> rusqlite::Result<Db> {
        Self::init(Connection::open_in_memory()?)
    }

    fn init(conn: Connection) -> rusqlite::Result<Db> {
        let _ = conn.pragma_update(None, "journal_mode", "WAL");
        let _ = conn.pragma_update(None, "synchronous", "NORMAL");
        let _ = conn.pragma_update(None, "foreign_keys", "OFF");
        conn.execute_batch(SCHEMA)?;
        Ok(Db { conn })
    }

    pub fn conn(&self) -> &Connection {
        &self.conn
    }

    /// The database file, or None for the in-memory fallback.
    pub fn file(&self) -> Option<PathBuf> {
        self.conn.path().filter(|p| !p.is_empty()).map(PathBuf::from)
    }

    // ---- reads (live rows only) ----
    pub fn load(&self) -> rusqlite::Result<Data> {
        let mut d = Data::default();
        d.accounts = self.accounts(false)?;
        d.categories = self.categories(false)?;
        d.transactions = self.transactions(false)?;
        d.budgets = self.budgets(false)?;
        d.accounts.retain(|a| !a.deleted);
        d.categories.retain(|a| !a.deleted);
        d.transactions.retain(|a| !a.deleted);
        d.budgets.retain(|a| !a.deleted);
        d.accounts.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        d.categories.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        Ok(d)
    }

    fn accounts(&self, _all: bool) -> rusqlite::Result<Vec<Account>> {
        let mut s = self.conn.prepare("SELECT id,name,type,balance,currency,deleted,updated_at FROM accounts")?;
        let rows = s.query_map([], |r| {
            Ok(Account { id: r.get(0)?, name: r.get(1)?, kind: r.get(2)?, balance: r.get(3)?, currency: r.get(4)?, deleted: r.get::<_, i64>(5)? != 0, updated_at: r.get(6)? })
        })?;
        rows.collect()
    }
    fn categories(&self, _all: bool) -> rusqlite::Result<Vec<Category>> {
        let mut s = self.conn.prepare("SELECT id,name,parent_id,color,deleted,updated_at FROM categories")?;
        let rows = s.query_map([], |r| {
            Ok(Category { id: r.get(0)?, name: r.get(1)?, parent_id: r.get(2)?, color: r.get(3)?, deleted: r.get::<_, i64>(4)? != 0, updated_at: r.get(5)? })
        })?;
        rows.collect()
    }
    fn transactions(&self, _all: bool) -> rusqlite::Result<Vec<Transaction>> {
        let mut s = self.conn.prepare("SELECT id,account_id,amount,description,category_id,date,notes,created_at,deleted,updated_at FROM transactions ORDER BY date DESC, created_at DESC")?;
        let rows = s.query_map([], |r| {
            Ok(Transaction { id: r.get(0)?, account_id: r.get(1)?, amount: r.get(2)?, description: r.get(3)?, category_id: r.get(4)?, date: r.get(5)?, notes: r.get(6)?, created_at: r.get(7)?, deleted: r.get::<_, i64>(8)? != 0, updated_at: r.get(9)? })
        })?;
        rows.collect()
    }
    fn budgets(&self, _all: bool) -> rusqlite::Result<Vec<Budget>> {
        let mut s = self.conn.prepare("SELECT id,category_id,amount,period,start_date,deleted,updated_at FROM budgets")?;
        let rows = s.query_map([], |r| {
            Ok(Budget { id: r.get(0)?, category_id: r.get(1)?, amount: r.get(2)?, period: r.get(3)?, start_date: r.get(4)?, deleted: r.get::<_, i64>(5)? != 0, updated_at: r.get(6)? })
        })?;
        rows.collect()
    }

    // ---- writes: every write stamps updated_at; deletes are tombstones, never physical ----
    pub fn save_account(&self, a: &Account) -> rusqlite::Result<()> {
        self.conn.execute(
            "INSERT INTO accounts(id,name,type,balance,currency,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7)
             ON CONFLICT(id) DO UPDATE SET name=?2,type=?3,balance=?4,currency=?5,deleted=?6,updated_at=?7",
            params![a.id, a.name, a.kind, a.balance, a.currency, a.deleted as i64, now_ms()],
        )?;
        Ok(())
    }
    pub fn save_category(&self, c: &Category) -> rusqlite::Result<()> {
        self.conn.execute(
            "INSERT INTO categories(id,name,parent_id,color,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6)
             ON CONFLICT(id) DO UPDATE SET name=?2,parent_id=?3,color=?4,deleted=?5,updated_at=?6",
            params![c.id, c.name, c.parent_id, c.color, c.deleted as i64, now_ms()],
        )?;
        Ok(())
    }
    pub fn save_transaction(&self, t: &Transaction) -> rusqlite::Result<()> {
        let created = if t.created_at == 0 { now_ms() } else { t.created_at };
        self.conn.execute(
            "INSERT INTO transactions(id,account_id,amount,description,category_id,date,notes,created_at,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10)
             ON CONFLICT(id) DO UPDATE SET account_id=?2,amount=?3,description=?4,category_id=?5,date=?6,notes=?7,deleted=?9,updated_at=?10",
            params![t.id, t.account_id, t.amount, t.description, t.category_id, t.date, t.notes, created, t.deleted as i64, now_ms()],
        )?;
        Ok(())
    }
    pub fn save_budget(&self, b: &Budget) -> rusqlite::Result<()> {
        self.conn.execute(
            "INSERT INTO budgets(id,category_id,amount,period,start_date,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7)
             ON CONFLICT(id) DO UPDATE SET category_id=?2,amount=?3,period=?4,start_date=?5,deleted=?6,updated_at=?7",
            params![b.id, b.category_id, b.amount, b.period, b.start_date, b.deleted as i64, now_ms()],
        )?;
        Ok(())
    }

    /// Replace the live dataset with a backup, as the Android app does: rows not in the backup become tombstones.
    pub fn restore(&mut self, b: &Backup) -> rusqlite::Result<()> {
        let tx = self.conn.transaction()?;
        let now = now_ms();
        for t in ["accounts", "categories", "transactions", "budgets"] {
            tx.execute(&format!("UPDATE {t} SET deleted=1, updated_at=?1 WHERE deleted=0"), params![now])?;
        }
        for a in &b.accounts {
            tx.execute("INSERT INTO accounts(id,name,type,balance,currency,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,0,?6)
                ON CONFLICT(id) DO UPDATE SET name=?2,type=?3,balance=?4,currency=?5,deleted=0,updated_at=?6",
                params![a.id, a.name, a.kind, a.balance, a.currency, now])?;
        }
        for c in &b.categories {
            tx.execute("INSERT INTO categories(id,name,parent_id,color,deleted,updated_at) VALUES(?1,?2,?3,?4,0,?5)
                ON CONFLICT(id) DO UPDATE SET name=?2,parent_id=?3,color=?4,deleted=0,updated_at=?5",
                params![c.id, c.name, c.parent_id, c.color, now])?;
        }
        for t in &b.transactions {
            tx.execute("INSERT INTO transactions(id,account_id,amount,description,category_id,date,notes,created_at,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,0,?9)
                ON CONFLICT(id) DO UPDATE SET account_id=?2,amount=?3,description=?4,category_id=?5,date=?6,notes=?7,deleted=0,updated_at=?9",
                params![t.id, t.account_id, t.amount, t.description, t.category_id, t.date, t.notes, t.created_at, now])?;
        }
        for x in &b.budgets {
            tx.execute("INSERT INTO budgets(id,category_id,amount,period,start_date,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,0,?6)
                ON CONFLICT(id) DO UPDATE SET category_id=?2,amount=?3,period=?4,start_date=?5,deleted=0,updated_at=?6",
                params![x.id, x.category_id, x.amount, x.period, x.start_date, now])?;
        }
        tx.commit()
    }

    pub fn get_setting(&self, key: &str) -> Option<String> {
        self.conn.query_row("SELECT value FROM settings WHERE key=?1", params![key], |r| r.get(0)).ok()
    }
    pub fn set_setting(&self, key: &str, value: &str) {
        let _ = self.conn.execute(
            "INSERT INTO settings(key,value) VALUES(?1,?2) ON CONFLICT(key) DO UPDATE SET value=?2",
            params![key, value],
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn acct(name: &str, bal: f64) -> Account {
        Account { id: new_id(), name: name.into(), kind: "checking".into(), balance: bal, currency: "USD".into(), deleted: false, updated_at: 0 }
    }

    #[test]
    fn round_trip_and_soft_delete() {
        let db = Db::memory().unwrap();
        let mut a = acct("Main", 10.0);
        db.save_account(&a).unwrap();
        assert_eq!(db.load().unwrap().accounts.len(), 1);
        a.deleted = true;
        db.save_account(&a).unwrap();
        assert!(db.load().unwrap().accounts.is_empty());
    }

    #[test]
    fn restore_replaces_dataset_and_tombstones_the_rest() {
        let mut db = Db::memory().unwrap();
        let old = acct("Old", 1.0);
        db.save_account(&old).unwrap();
        let keep = acct("Keep", 5.0);
        let b = Backup { version: BACKUP_VERSION, exported_at: 0, accounts: vec![keep.clone()], categories: vec![], transactions: vec![], budgets: vec![] };
        db.restore(&b).unwrap();
        let d = db.load().unwrap();
        assert_eq!(d.accounts.len(), 1);
        assert_eq!(d.accounts[0].name, "Keep");
    }

    #[test]
    fn android_backup_json_is_readable() {
        let json = r#"{"version":1,"exportedAt":5,"accounts":[{"id":"a","name":"Vacation","type":"savings","balance":2500.5,"currency":"USD","deleted":false,"updatedAt":9}],
            "categories":[{"id":"c","name":"Food","updatedAt":1}],
            "transactions":[{"id":"t","accountId":"a","amount":-40.0,"description":"Lunch","categoryId":"c","date":"2026-11-24","createdAt":2,"deleted":false,"updatedAt":3}],
            "budgets":[{"id":"b","categoryId":"c","amount":80.0,"period":"monthly","startDate":"","updatedAt":4}]}"#;
        let b: Backup = serde_json::from_str(json).unwrap();
        assert_eq!(b.accounts[0].kind, "savings");
        assert_eq!(b.transactions[0].category_id.as_deref(), Some("c"));
        let mut db = Db::memory().unwrap();
        db.restore(&b).unwrap();
        assert_eq!(db.load().unwrap().transactions.len(), 1);
    }

    #[test]
    fn settings_persist() {
        let db = Db::memory().unwrap();
        assert_eq!(db.get_setting("theme"), None);
        db.set_setting("theme", "dark");
        db.set_setting("theme", "light");
        assert_eq!(db.get_setting("theme").as_deref(), Some("light"));
    }
}
