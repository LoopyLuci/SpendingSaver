//! Optional SimpleFIN Bridge link (US bank data the user owns), the same design as the backend's
//! `simplefin.py` and `docs/bank-linking.md`.
//!
//! The user creates a one-time *setup token* at SimpleFIN Bridge and pastes it here. This app claims it for an
//! *access URL* (a read-only credential the user can disable at the bridge at any time), keeps it in a file in
//! the app's data folder, and pulls accounts and transactions straight from the bridge to this PC. No
//! SpendingSaver server is involved.
//!
//! Rules: read-only (only `/claim` and `/accounts` are called); HTTPS to `*.simplefin.org` only; no redirects;
//! bounded time and size; the access URL is never logged, shown, synced or put in a backup; bank text is
//! untrusted and sanitised; imports are idempotent and never overwrite the user's edits.
use crate::db::Db;
use crate::model::*;
use base64::Engine;
use rusqlite::params;
use serde_json::Value;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;

const HOST: &str = "simplefin.org";
const MAX_BODY: u64 = 20 * 1024 * 1024;
pub const WINDOW_DAYS: i64 = 90;
const OVERLAP_DAYS: i64 = 5;
pub const MIN_INTERVAL_S: i64 = 3600;

#[derive(Debug, Default, Clone, PartialEq)]
pub struct Stats {
    pub accounts: usize,
    pub added: usize,
    pub skipped: usize,
}

pub fn credential_file(data_dir: &Path) -> PathBuf {
    data_dir.join("simplefin.url")
}

pub fn load_access_url(data_dir: &Path) -> Option<String> {
    std::fs::read_to_string(credential_file(data_dir)).ok().map(|s| s.trim().to_string()).filter(|s| !s.is_empty())
}

pub fn save_access_url(data_dir: &Path, url: &str) -> std::io::Result<()> {
    let _ = std::fs::create_dir_all(data_dir);
    std::fs::write(credential_file(data_dir), url)
}

pub fn forget(data_dir: &Path) {
    let _ = std::fs::remove_file(credential_file(data_dir));
}

/// Printable, single line, bounded.
pub fn clean(text: &str, limit: usize) -> String {
    text.chars().map(|c| if c.is_control() { ' ' } else { c }).collect::<String>().trim().chars().take(limit).collect()
}

/// HTTPS to simplefin.org only. Returns the URL without credentials, and the credentials if present.
pub fn check_url(url: &str) -> Result<(String, Option<(String, String)>), String> {
    let bad = || "That is not a SimpleFIN address (https://*.simplefin.org only).".to_string();
    let rest = url.strip_prefix("https://").ok_or_else(bad)?;
    let (auth_host, path) = match rest.find('/') {
        Some(i) => (&rest[..i], &rest[i..]),
        None => (rest, ""),
    };
    let (creds, host_port) = match auth_host.rsplit_once('@') {
        Some((c, h)) => (Some(c), h),
        None => (None, auth_host),
    };
    let host = host_port.split(':').next().unwrap_or("").to_lowercase();
    if !(host == HOST || host.ends_with(&format!(".{HOST}"))) || host_port.contains(['\\', ' ']) {
        return Err(bad());
    }
    let creds = creds.map(|c| {
        let (u, p) = c.split_once(':').unwrap_or((c, ""));
        (u.to_string(), p.to_string())
    });
    Ok((format!("https://{host_port}{path}"), creds))
}

fn agent() -> ureq::Agent {
    ureq::AgentBuilder::new().redirects(0).timeout_connect(Duration::from_secs(10)).timeout(Duration::from_secs(30)).build()
}

fn read_limited(resp: ureq::Response) -> Result<String, String> {
    let mut buf = String::new();
    resp.into_reader().take(MAX_BODY + 1).read_to_string(&mut buf).map_err(|_| "SimpleFIN returned something unreadable.".to_string())?;
    if buf.len() as u64 > MAX_BODY {
        return Err("The bridge reply was unexpectedly large; ignored.".into());
    }
    Ok(buf)
}

/// Exchanges a one-time setup token for the access URL (the token works once).
pub fn claim(setup_token: &str) -> Result<String, String> {
    let compact: String = setup_token.split_whitespace().collect();
    let raw = base64::engine::general_purpose::STANDARD
        .decode(compact.as_bytes())
        .or_else(|_| base64::engine::general_purpose::URL_SAFE.decode(compact.as_bytes()))
        .map_err(|_| "That is not a valid SimpleFIN setup token.".to_string())?;
    let claim_url = String::from_utf8(raw).map_err(|_| "That is not a valid SimpleFIN setup token.".to_string())?;
    let (bare, _) = check_url(claim_url.trim())?;
    let resp = match agent().post(&bare).set("Content-Length", "0").call() {
        Ok(r) => r,
        Err(ureq::Error::Status(403, _)) => {
            return Err("SimpleFIN refused the token: it was already used or does not exist. If you did not use it yourself, treat it as compromised and disable it at the bridge, then create a new one.".into())
        }
        Err(ureq::Error::Status(c, _)) => return Err(format!("SimpleFIN answered with status {c}.")),
        Err(_) => return Err("Could not reach SimpleFIN. Check your connection and try again.".into()),
    };
    let access = read_limited(resp)?.trim().to_string();
    let (_, creds) = check_url(&access)?;
    match creds {
        Some((u, _)) if !u.is_empty() => Ok(access),
        _ => Err("SimpleFIN returned an unexpected reply.".into()),
    }
}

pub fn fetch(access_url: &str, start: Option<i64>) -> Result<Value, String> {
    let (bare, creds) = check_url(access_url)?;
    let mut req = agent().get(&format!("{}/accounts", bare.trim_end_matches('/'))).query("version", "2");
    if let Some(s) = start {
        req = req.query("start-date", &s.to_string());
    }
    if let Some((u, p)) = creds {
        let token = base64::engine::general_purpose::STANDARD.encode(format!("{u}:{p}"));
        req = req.set("Authorization", &format!("Basic {token}"));
    }
    let resp = match req.call() {
        Ok(r) => r,
        Err(ureq::Error::Status(402, _)) => return Err("SimpleFIN says payment is required. Check your bridge subscription.".into()),
        Err(ureq::Error::Status(403, _)) => return Err("SimpleFIN rejected the saved credential (disabled or wrong). Disconnect and connect again.".into()),
        Err(ureq::Error::Status(c, _)) => return Err(format!("SimpleFIN answered with status {c}.")),
        Err(_) => return Err("Could not reach SimpleFIN. Try again later.".into()),
    };
    let v: Value = serde_json::from_str(&read_limited(resp)?).map_err(|_| "SimpleFIN returned something unreadable.".to_string())?;
    if !v.get("accounts").map(|a| a.is_array()).unwrap_or(false) {
        return Err("SimpleFIN returned an unexpected shape.".into());
    }
    Ok(v)
}

/// A decimal string to a 2-place number; rejects NaN, infinity and absurd sizes.
pub fn money_of(v: &Value) -> Option<f64> {
    let n = match v {
        Value::String(s) => s.trim().parse::<f64>().ok()?,
        Value::Number(n) => n.as_f64()?,
        _ => return None,
    };
    (n.is_finite() && n.abs() <= 1e12).then(|| (n * 100.0).round() / 100.0)
}

pub fn date_of(v: &Value) -> Option<String> {
    let secs = v.as_i64().or_else(|| v.as_str().and_then(|s| s.parse().ok()))?;
    if secs <= 0 {
        return None;
    }
    chrono::DateTime::from_timestamp(secs, 0).map(|d| d.format("%Y-%m-%d").to_string())
}

pub fn messages(v: &Value) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for e in v.get("errlist").and_then(|e| e.as_array()).into_iter().flatten() {
        if let Some(m) = e.get("msg").or_else(|| e.get("code")).and_then(|m| m.as_str()) {
            out.push(clean(m, 200));
        }
    }
    for e in v.get("errors").and_then(|e| e.as_array()).into_iter().flatten() {
        if let Some(m) = e.as_str() {
            out.push(clean(m, 200));
        }
    }
    out.retain(|m| !m.is_empty());
    out.dedup();
    out
}

fn ref_get(db: &Db, kind: &str, ext: &str) -> Option<String> {
    db.conn().query_row("SELECT local_id FROM external_refs WHERE kind=?1 AND external_id=?2", params![kind, ext], |r| r.get(0)).ok()
}

fn ref_set(db: &Db, kind: &str, ext: &str, local: &str) {
    let _ = db.conn().execute(
        "INSERT INTO external_refs(kind,external_id,local_id) VALUES(?1,?2,?3) ON CONFLICT(kind,external_id) DO UPDATE SET local_id=?3",
        params![kind, ext, local],
    );
}

/// Creates or refreshes accounts and adds only *new* posted transactions. Existing rows stay as the user has them.
pub fn import(db: &Db, data: &Value) -> Stats {
    let mut st = Stats::default();
    let live = db.load().map(|d| d.accounts).unwrap_or_default();
    let conns: std::collections::HashMap<String, String> = data
        .get("connections")
        .and_then(|c| c.as_array())
        .into_iter()
        .flatten()
        .filter_map(|c| Some((c.get("conn_id")?.as_str()?.to_string(), clean(c.get("name")?.as_str()?, 60))))
        .collect();
    for acct in data.get("accounts").and_then(|a| a.as_array()).into_iter().flatten() {
        let Some(ext) = acct.get("id").and_then(|i| i.as_str()).filter(|s| !s.is_empty()) else { continue };
        let existing = ref_get(db, "account", ext).and_then(|id| live.iter().find(|a| a.id == id && !a.deleted).cloned());
        let mut local = match existing {
            Some(a) => a,
            None => {
                let name = acct.get("name").and_then(|n| n.as_str()).map(|n| clean(n, 60)).unwrap_or_default();
                let conn = acct.get("conn_id").and_then(|c| c.as_str()).and_then(|c| conns.get(c)).cloned().unwrap_or_default();
                let label = [conn, name].into_iter().filter(|p| !p.is_empty()).collect::<Vec<_>>().join(" – ");
                let cur = acct.get("currency").and_then(|c| c.as_str()).filter(|c| c.len() == 3 && c.chars().all(|x| x.is_ascii_alphabetic())).map(|c| c.to_uppercase()).unwrap_or_else(|| "USD".into());
                let a = Account { id: new_id(), name: if label.is_empty() { "Linked account".into() } else { label }, kind: "Checking".into(), balance: 0.0, currency: cur, deleted: false, updated_at: 0 };
                if db.save_account(&a).is_err() {
                    continue;
                }
                ref_set(db, "account", ext, &a.id);
                st.accounts += 1;
                a
            }
        };
        if let Some(bal) = acct.get("balance").and_then(money_of) {
            if (local.balance - bal).abs() > 1e-9 {
                local.balance = bal; // the bank's figure is authoritative for a linked account
                let _ = db.save_account(&local);
            }
        }
        for tx in acct.get("transactions").and_then(|t| t.as_array()).into_iter().flatten() {
            let Some(tid) = tx.get("id").and_then(|i| i.as_str()).filter(|s| !s.is_empty()) else { continue };
            if tx.get("pending").and_then(|p| p.as_bool()).unwrap_or(false) {
                continue;
            }
            let (Some(amount), Some(date)) = (tx.get("amount").and_then(money_of), tx.get("posted").and_then(date_of)) else {
                st.skipped += 1;
                continue;
            };
            let key = format!("{ext}:{tid}");
            if ref_get(db, "transaction", &key).is_some() {
                continue;
            }
            let desc = tx.get("description").and_then(|d| d.as_str()).map(|d| clean(d, 200)).filter(|d| !d.is_empty()).unwrap_or_else(|| "Bank transaction".into());
            let t = Transaction { id: new_id(), account_id: local.id.clone(), amount, description: desc, category_id: None, date, notes: Some("imported via SimpleFIN".into()), created_at: 0, deleted: false, updated_at: 0 };
            if db.save_transaction(&t).is_ok() {
                ref_set(db, "transaction", &key, &t.id);
                st.added += 1;
            }
        }
    }
    st
}

/// Start of the window to request: the first sync reads 90 days, later ones a few days before the last sync.
pub fn window_start(now: i64, last: Option<i64>) -> i64 {
    match last {
        None => now - WINDOW_DAYS * 86400,
        Some(l) => (l - OVERLAP_DAYS * 86400).max(now - WINDOW_DAYS * 86400),
    }
}

/// One full sync: claims nothing, fetches, imports, records the time. Enforces the bridge's hourly limit.
pub fn sync(db: &Db, data_dir: &Path) -> Result<(Stats, Vec<String>), String> {
    let access = load_access_url(data_dir).ok_or("Connect SimpleFIN first.")?;
    let now = chrono::Utc::now().timestamp();
    let last: Option<i64> = db.get_setting("simplefin_last_sync").and_then(|s| s.parse().ok());
    if let Some(l) = last {
        if now - l < MIN_INTERVAL_S {
            return Err(format!("SimpleFIN allows about one update an hour. Try again in {} min.", (MIN_INTERVAL_S - (now - l)) / 60 + 1));
        }
    }
    let result = fetch(&access, Some(window_start(now, last))).map(|v| {
        let st = import(db, &v);
        (st, messages(&v))
    });
    match &result {
        Ok((st, msgs)) => {
            db.set_setting("simplefin_last_sync", &now.to_string());
            db.set_setting("simplefin_last_result", &format!("{} new accounts, {} new transactions", st.accounts, st.added));
            db.set_setting("simplefin_last_error", &msgs.join("; "));
        }
        Err(e) => db.set_setting("simplefin_last_error", e),
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn only_simplefin_https_is_allowed() {
        assert!(check_url("https://beta-bridge.simplefin.org/simplefin/claim/abc").is_ok());
        assert!(check_url("https://simplefin.org/x").is_ok());
        assert!(check_url("http://bridge.simplefin.org/x").is_err());
        assert!(check_url("https://evil.com/x").is_err());
        assert!(check_url("https://simplefin.org.evil.com/x").is_err());
        assert!(check_url("https://evilsimplefin.org/x").is_err());
        assert!(check_url("https://user:pw@evil.com@bridge.simplefin.org/x").is_ok()); // the host is what follows the last @
    }

    #[test]
    fn credentials_are_split_from_the_url() {
        let (bare, creds) = check_url("https://u1:p1@bridge.simplefin.org/simplefin").unwrap();
        assert_eq!(bare, "https://bridge.simplefin.org/simplefin");
        assert_eq!(creds, Some(("u1".into(), "p1".into())));
    }

    #[test]
    fn numbers_dates_and_text_are_sanitised() {
        assert_eq!(money_of(&json!("-12.345")), Some(-12.35));
        assert_eq!(money_of(&json!("NaN")), None);
        assert_eq!(money_of(&json!("1e30")), None);
        assert_eq!(date_of(&json!(1735689600)), Some("2025-01-01".into()));
        assert_eq!(date_of(&json!(0)), None);
        assert_eq!(clean("a\u{7}b\nc  ", 10), "a b c");
        assert_eq!(window_start(1_000_000_000, None), 1_000_000_000 - 90 * 86400);
        assert_eq!(window_start(1_000_000_000, Some(999_000_000)), 999_000_000 - 5 * 86400);
    }

    fn feed(balance: &str, tx_desc: &str) -> Value {
        json!({"connections":[{"conn_id":"c1","name":"Test Bank"}],
               "accounts":[{"id":"acc1","conn_id":"c1","name":"Checking","currency":"USD","balance":balance,
                 "transactions":[{"id":"t1","posted":1735689600,"amount":"-4.50","description":tx_desc},
                                 {"id":"t2","posted":1735689600,"amount":"9.00","description":"Pending","pending":true},
                                 {"id":"t3","posted":0,"amount":"1.00","description":"bad date"}]}]})
    }

    #[test]
    fn import_is_idempotent_and_keeps_user_edits() {
        let db = Db::memory().unwrap();
        let st = import(&db, &feed("100.00", "Coffee"));
        assert_eq!(st, Stats { accounts: 1, added: 1, skipped: 1 });
        let d = db.load().unwrap();
        assert_eq!(d.accounts[0].name, "Test Bank – Checking");
        assert_eq!(d.accounts[0].balance, 100.0);
        // The user renames the transaction; a later fetch must not overwrite or duplicate it.
        let mut t = d.transactions[0].clone();
        t.description = "My coffee".into();
        db.save_transaction(&t).unwrap();
        let st2 = import(&db, &feed("90.00", "Coffee"));
        assert_eq!(st2.added, 0);
        assert_eq!(st2.accounts, 0);
        let d2 = db.load().unwrap();
        assert_eq!(d2.transactions.len(), 1);
        assert_eq!(d2.transactions[0].description, "My coffee");
        assert_eq!(d2.accounts.len(), 1);
        assert_eq!(d2.accounts[0].balance, 90.0);
    }

    #[test]
    fn the_access_url_lives_in_its_own_file() {
        let dir = std::env::temp_dir().join(format!("ss-bank-{}", new_id()));
        assert_eq!(load_access_url(&dir), None);
        save_access_url(&dir, "https://u:p@bridge.simplefin.org/simplefin\n").unwrap();
        assert_eq!(load_access_url(&dir).as_deref(), Some("https://u:p@bridge.simplefin.org/simplefin"));
        forget(&dir);
        assert_eq!(load_access_url(&dir), None);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
