//! Optional phone-sync hub (protocol v1, see `spendingsaver-desktop/docs/sync.md`). Off unless the user turns it on.
//! It runs on its own thread with its own SQLite connection, so it never blocks the window.
use rusqlite::{params, Connection, OptionalExtension};
use serde_json::{json, Map, Value};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

pub const PROTOCOL: i64 = 1;
const MAX_SKEW_MS: i64 = 5 * 60 * 1000;
const MAX_CHANGES: usize = 1000;
const ORDER: [&str; 4] = ["account", "category", "transaction", "budget"];

pub struct Hub {
    pub port: u16,
    pub changed: Arc<AtomicU64>,
    stop: Arc<AtomicBool>,
}

impl Drop for Hub {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::SeqCst);
    }
}

/// Starts the hub on the first free port from 8125. `on_change` runs after a phone changed data.
pub fn start(db_file: PathBuf, on_change: impl Fn() + Send + 'static) -> Result<Hub, String> {
    let conn = open(&db_file)?;
    backfill(&conn).map_err(|e| e.to_string())?;
    let mut server = None;
    let mut port = 8125;
    for p in 8125..8140 {
        if let Ok(s) = tiny_http::Server::http(("0.0.0.0", p)) {
            server = Some(s);
            port = p;
            break;
        }
    }
    let server = server.ok_or("No free port between 8125 and 8139.")?;
    let changed = Arc::new(AtomicU64::new(0));
    let stop = Arc::new(AtomicBool::new(false));
    let (c2, s2) = (changed.clone(), stop.clone());
    std::thread::Builder::new()
        .name("sync-hub".into())
        .spawn(move || {
            let mut conn = conn;
            let mut fails: HashMap<String, Vec<Instant>> = HashMap::new();
            while !s2.load(Ordering::SeqCst) {
                let Ok(Some(mut req)) = server.recv_timeout(Duration::from_millis(400)) else { continue };
                let peer = req.remote_addr().map(|a| a.ip().to_string()).unwrap_or_default();
                let method = req.method().as_str().to_string();
                let path = req.url().split('?').next().unwrap_or("").to_string();
                let auth = req.headers().iter().find(|h| h.field.equiv("Authorization")).map(|h| h.value.as_str().to_string());
                let mut body = String::new();
                {
                    use std::io::Read;
                    let _ = req.as_reader().take(8 * 1024 * 1024).read_to_string(&mut body);
                }
                let (code, out, dirty) = handle(&mut conn, &mut fails, &peer, &method, &path, auth.as_deref(), &body);
                let resp = tiny_http::Response::from_string(out.to_string())
                    .with_status_code(code)
                    .with_header(tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap());
                let _ = req.respond(resp);
                if dirty {
                    c2.fetch_add(1, Ordering::SeqCst);
                    on_change();
                }
            }
        })
        .map_err(|e| e.to_string())?;
    Ok(Hub { port, changed, stop })
}

fn open(path: &PathBuf) -> Result<Connection, String> {
    let c = Connection::open(path).map_err(|e| e.to_string())?;
    let _ = c.busy_timeout(Duration::from_secs(5));
    Ok(c)
}

fn backfill(c: &Connection) -> rusqlite::Result<()> {
    let n: i64 = c.query_row("SELECT COUNT(*) FROM sync_log", [], |r| r.get(0))?;
    if n == 0 {
        for (ent, tab) in [("account", "accounts"), ("category", "categories"), ("transaction", "transactions"), ("budget", "budgets")] {
            c.execute(&format!("INSERT INTO sync_log(entity,entity_id,device_id,logged_at) SELECT '{ent}', id, NULL, updated_at FROM {tab}"), [])?;
        }
    }
    Ok(())
}

fn now() -> i64 {
    crate::model::now_ms()
}

fn hash(token: &str) -> String {
    Sha256::digest(token.as_bytes()).iter().map(|b| format!("{b:02x}")).collect()
}

fn ct_eq(a: &str, b: &str) -> bool {
    a.len() == b.len() && a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

pub fn wire(ms: i64) -> String {
    chrono::DateTime::from_timestamp_millis(ms).unwrap_or_default().format("%Y-%m-%dT%H:%M:%S%.3fZ").to_string()
}

fn parse_ts(v: &Value) -> Result<i64, String> {
    let s = v.as_str().ok_or("timestamp must be an ISO-8601 string")?.trim();
    if let Ok(d) = chrono::DateTime::parse_from_rfc3339(s) {
        return Ok(d.timestamp_millis());
    }
    chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S%.f")
        .map(|n| n.and_utc().timestamp_millis())
        .map_err(|_| "invalid timestamp".to_string())
}

pub fn pairing_key(c: &Connection) -> String {
    if let Ok(k) = c.query_row("SELECT value FROM settings WHERE key='pairing_key'", [], |r| r.get::<_, String>(0)) {
        return k;
    }
    new_key(c)
}

pub fn new_key(c: &Connection) -> String {
    let k: String = uuid::Uuid::new_v4().simple().to_string()[..6].to_uppercase();
    let _ = c.execute("INSERT INTO settings(key,value) VALUES('pairing_key',?1) ON CONFLICT(key) DO UPDATE SET value=?1", params![k]);
    k
}

fn err(code: u16, msg: &str) -> (u16, Value, bool) {
    (code, json!({ "detail": msg }), false)
}

fn handle(conn: &mut Connection, fails: &mut HashMap<String, Vec<Instant>>, peer: &str, method: &str, path: &str, auth: Option<&str>, body: &str) -> (u16, Value, bool) {
    let payload: Value = if body.trim().is_empty() { json!({}) } else { serde_json::from_str(body).unwrap_or(Value::Null) };
    match (method, path) {
        ("POST", "/api/sync/v1/pair") => pair(conn, fails, peer, &payload),
        ("GET", "/health") => (200, json!({"ok": true}), false),
        ("GET", "/api/sync/v1/status") | ("POST", "/api/sync/v1/exchange") => {
            let Some(device) = authorize(conn, auth) else { return err(401, "invalid or revoked device token") };
            if path.ends_with("status") {
                let cursor: i64 = conn.query_row("SELECT COALESCE(MAX(seq),0) FROM sync_log", [], |r| r.get(0)).unwrap_or(0);
                (200, json!({"protocol": PROTOCOL, "device_id": device, "server_time": wire(now()), "cursor": cursor}), false)
            } else if payload.is_object() {
                exchange(conn, &device, &payload)
            } else {
                err(400, "request body must be a JSON object")
            }
        }
        _ => err(404, "not found"),
    }
}

fn authorize(conn: &Connection, auth: Option<&str>) -> Option<String> {
    let token = auth?.strip_prefix("Bearer ")?.trim();
    let digest = hash(token);
    let mut s = conn.prepare("SELECT id, token_hash FROM sync_devices WHERE revoked=0").ok()?;
    let rows: Vec<(String, String)> = s.query_map([], |r| Ok((r.get(0)?, r.get(1)?))).ok()?.flatten().collect();
    let id = rows.into_iter().find(|(_, h)| ct_eq(h, &digest))?.0;
    let _ = conn.execute("UPDATE sync_devices SET last_seen=?1 WHERE id=?2", params![now(), id]);
    Some(id)
}

fn pair(conn: &Connection, fails: &mut HashMap<String, Vec<Instant>>, peer: &str, p: &Value) -> (u16, Value, bool) {
    let recent = fails.entry(peer.to_string()).or_default();
    recent.retain(|t| t.elapsed() < Duration::from_secs(600));
    if recent.len() >= 5 {
        return err(429, "too many pairing attempts; try again later");
    }
    let given = p.get("key").and_then(|v| v.as_str()).unwrap_or("").trim().to_uppercase();
    let name: String = p.get("name").and_then(|v| v.as_str()).unwrap_or("").trim().chars().take(40).collect();
    let device = p.get("device_id").and_then(|v| v.as_str()).unwrap_or("").trim().to_string();
    if device.is_empty() || device.len() > 64 {
        return err(400, "device_id required");
    }
    if given.is_empty() || !ct_eq(&given, &pairing_key(conn).to_uppercase()) {
        recent.push(Instant::now());
        return err(401, "invalid pairing key");
    }
    let token = format!("{}{}", uuid::Uuid::new_v4().simple(), uuid::Uuid::new_v4().simple());
    let r = conn.execute(
        "INSERT INTO sync_devices(id,name,token_hash,created_at,last_seen,revoked) VALUES(?1,?2,?3,?4,?4,0)
         ON CONFLICT(id) DO UPDATE SET name=COALESCE(NULLIF(?2,''),name), token_hash=?3, revoked=0, last_seen=?4",
        params![device, name, hash(&token), now()],
    );
    if let Err(e) = r {
        return err(500, &e.to_string());
    }
    let host = std::env::var("COMPUTERNAME").or_else(|_| std::env::var("HOSTNAME")).unwrap_or_else(|_| "desktop".into());
    (200, json!({"protocol": PROTOCOL, "device_id": device, "token": token, "hub": host}), false)
}

// ---------------------------------------------------------------- records

fn record(conn: &Connection, entity: &str, id: &str) -> Option<Value> {
    let (sql, n): (&str, usize) = match entity {
        "account" => ("SELECT name,type,balance,currency,deleted,updated_at FROM accounts WHERE id=?1", 0),
        "category" => ("SELECT name,color,parent_id,deleted,updated_at FROM categories WHERE id=?1", 1),
        "transaction" => ("SELECT account_id,amount,description,category_id,date,notes,created_at,deleted,updated_at FROM transactions WHERE id=?1", 2),
        "budget" => ("SELECT category_id,amount,period,start_date,deleted,updated_at FROM budgets WHERE id=?1", 3),
        _ => return None,
    };
    conn.query_row(sql, params![id], |r| {
        let (data, deleted, updated): (Value, i64, i64) = match n {
            0 => (json!({"name": r.get::<_, String>(0)?, "type": r.get::<_, String>(1)?, "balance": r.get::<_, f64>(2)?, "currency": r.get::<_, String>(3)?}), r.get(4)?, r.get(5)?),
            1 => (json!({"name": r.get::<_, String>(0)?, "color": r.get::<_, Option<String>>(1)?, "parent_id": r.get::<_, Option<String>>(2)?}), r.get(3)?, r.get(4)?),
            2 => (
                json!({"account_id": r.get::<_, String>(0)?, "amount": r.get::<_, f64>(1)?, "description": r.get::<_, String>(2)?,
                       "category_id": r.get::<_, Option<String>>(3)?, "date": r.get::<_, String>(4)?, "notes": r.get::<_, Option<String>>(5)?,
                       "created_at": wire(r.get::<_, i64>(6)?)}),
                r.get(7)?,
                r.get(8)?,
            ),
            _ => (json!({"category_id": r.get::<_, String>(0)?, "amount": r.get::<_, f64>(1)?, "period": r.get::<_, String>(2)?, "start_date": r.get::<_, String>(3)?}), r.get(4)?, r.get(5)?),
        };
        Ok(json!({"entity": entity, "id": id, "updated_at": wire(updated), "deleted": deleted != 0, "data": data}))
    })
    .optional()
    .ok()
    .flatten()
}

fn updated_at(conn: &Connection, entity: &str, id: &str) -> Option<i64> {
    let tab = table(entity)?;
    conn.query_row(&format!("SELECT updated_at FROM {tab} WHERE id=?1"), params![id], |r| r.get(0)).optional().ok().flatten()
}

fn table(entity: &str) -> Option<&'static str> {
    Some(match entity {
        "account" => "accounts",
        "category" => "categories",
        "transaction" => "transactions",
        "budget" => "budgets",
        _ => return None,
    })
}

fn exists(conn: &Connection, entity: &str, id: &str) -> bool {
    updated_at(conn, entity, id).is_some()
}

fn text(d: &Value, k: &str, required: bool, max: usize) -> Result<Option<String>, String> {
    match d.get(k) {
        None | Some(Value::Null) if required => Err(format!("{k} is required")),
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(s)) if required && s.trim().is_empty() => Err(format!("{k} must not be empty")),
        Some(Value::String(s)) if s.chars().count() > max => Err(format!("{k} is too long")),
        Some(Value::String(s)) => Ok(Some(s.clone())),
        _ => Err(format!("{k} must be a string")),
    }
}

fn num(d: &Value, k: &str) -> Result<f64, String> {
    match d.get(k) {
        Some(Value::Number(n)) => n.as_f64().filter(|f| f.is_finite()).ok_or(format!("{k} must be finite")),
        None | Some(Value::Null) => Err(format!("{k} is required")),
        _ => Err(format!("{k} must be a number")),
    }
}

fn date_ok(s: &str) -> bool {
    chrono::NaiveDate::parse_from_str(s, "%Y-%m-%d").is_ok() && s.len() == 10
}

/// Applies one pushed record. Ok(true) = applied, Ok(false) = the hub's copy is newer or equal.
fn apply(conn: &Connection, rec: &Value, now_ms: i64) -> Result<bool, String> {
    let obj = rec.as_object().ok_or("record must be an object")?;
    let entity = obj.get("entity").and_then(|v| v.as_str()).unwrap_or("");
    let tab = table(entity).ok_or_else(|| format!("unknown entity: {entity:?}"))?;
    let id = obj.get("id").and_then(|v| v.as_str()).filter(|s| !s.is_empty() && s.len() <= 64).ok_or("id must be a non-empty string")?;
    let deleted = match obj.get("deleted") {
        None => false,
        Some(Value::Bool(b)) => *b,
        _ => return Err("deleted must be a boolean".into()),
    };
    let incoming = parse_ts(obj.get("updated_at").unwrap_or(&Value::Null)).map_err(|e| format!("updated_at: {e}"))?.min(now_ms + MAX_SKEW_MS);
    let existing = updated_at(conn, entity, id);
    if let Some(have) = existing {
        if incoming <= have {
            return Ok(false);
        }
    }
    let empty = json!({});
    let d = obj.get("data").unwrap_or(&empty);
    if !d.is_object() {
        return Err("data must be an object".into());
    }
    let partial = existing.is_some();
    let del = deleted as i64;
    // On a partial update (tombstones carry no data) only the flags change.
    if partial && d.as_object().map(|m| m.is_empty()).unwrap_or(true) {
        conn.execute(&format!("UPDATE {tab} SET deleted=?1, updated_at=?2 WHERE id=?3"), params![del, incoming, id]).map_err(|e| e.to_string())?;
        return Ok(true);
    }
    match entity {
        "account" => {
            let name = text(d, "name", true, 500)?.unwrap();
            let kind = text(d, "type", true, 50)?.unwrap();
            let cur = text(d, "currency", true, 10)?.unwrap();
            let bal = num(d, "balance")?;
            conn.execute(
                "INSERT INTO accounts(id,name,type,balance,currency,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7)
                 ON CONFLICT(id) DO UPDATE SET name=?2,type=?3,balance=?4,currency=?5,deleted=?6,updated_at=?7",
                params![id, name, kind, bal, cur, del, incoming],
            )
        }
        "category" => {
            let name = text(d, "name", true, 500)?.unwrap();
            let color = text(d, "color", false, 20)?;
            let parent = text(d, "parent_id", false, 64)?;
            conn.execute(
                "INSERT INTO categories(id,name,parent_id,color,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6)
                 ON CONFLICT(id) DO UPDATE SET name=?2,parent_id=?3,color=?4,deleted=?5,updated_at=?6",
                params![id, name, parent, color, del, incoming],
            )
        }
        "transaction" => {
            let account = text(d, "account_id", true, 64)?.unwrap();
            let amount = num(d, "amount")?;
            let desc = text(d, "description", true, 1000)?.unwrap();
            let cat = text(d, "category_id", false, 64)?;
            let date = text(d, "date", true, 10)?.unwrap();
            if !date_ok(&date) {
                return Err("date must be YYYY-MM-DD".into());
            }
            let notes = text(d, "notes", false, 5000)?;
            let created = match d.get("created_at") {
                Some(v) if !v.is_null() => parse_ts(v)?,
                _ => incoming,
            };
            if !exists(conn, "account", &account) {
                return Err(format!("missing_reference: account_id={account}"));
            }
            if let Some(c) = &cat {
                if !exists(conn, "category", c) {
                    return Err(format!("missing_reference: category_id={c}"));
                }
            }
            conn.execute(
                "INSERT INTO transactions(id,account_id,amount,description,category_id,date,notes,created_at,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10)
                 ON CONFLICT(id) DO UPDATE SET account_id=?2,amount=?3,description=?4,category_id=?5,date=?6,notes=?7,deleted=?9,updated_at=?10",
                params![id, account, amount, desc, cat, date, notes, created, del, incoming],
            )
        }
        _ => {
            let cat = text(d, "category_id", true, 64)?.unwrap();
            let amount = num(d, "amount")?;
            let period = text(d, "period", true, 20)?.unwrap();
            let start = text(d, "start_date", false, 10)?.unwrap_or_default();
            if !exists(conn, "category", &cat) {
                return Err(format!("missing_reference: category_id={cat}"));
            }
            conn.execute(
                "INSERT INTO budgets(id,category_id,amount,period,start_date,deleted,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7)
                 ON CONFLICT(id) DO UPDATE SET category_id=?2,amount=?3,period=?4,start_date=?5,deleted=?6,updated_at=?7",
                params![id, cat, amount, period, start, del, incoming],
            )
        }
    }
    .map_err(|e| e.to_string())?;
    Ok(true)
}

fn exchange(conn: &mut Connection, device: &str, p: &Value) -> (u16, Value, bool) {
    let cursor = match p.get("cursor") {
        None => 0,
        Some(v) => match v.as_i64() {
            Some(n) if n >= 0 => n,
            _ => return err(400, "cursor must be a non-negative integer"),
        },
    };
    let pushed: Vec<Value> = match p.get("changes") {
        None => vec![],
        Some(Value::Array(a)) => a.clone(),
        _ => return err(400, "changes must be a list"),
    };
    if pushed.len() > MAX_CHANGES {
        return err(413, &format!("at most {MAX_CHANGES} changes per request"));
    }
    let limit = match p.get("limit") {
        None => 500,
        Some(v) => match v.as_i64() {
            Some(n) if n >= 1 => n.min(2000),
            _ => return err(400, "limit must be a positive integer"),
        },
    } as usize;

    let mut sorted = pushed;
    let rank = |r: &Value| ORDER.iter().position(|e| Some(*e) == r.get("entity").and_then(|v| v.as_str())).unwrap_or(ORDER.len());
    sorted.sort_by(|a, b| rank(a).cmp(&rank(b)).then_with(|| a.get("updated_at").and_then(|v| v.as_str()).unwrap_or("").cmp(b.get("updated_at").and_then(|v| v.as_str()).unwrap_or(""))));

    let mut applied = 0;
    let mut rejected = vec![];
    let mut stale: Vec<(String, String)> = vec![];
    let now_ms = now();
    let tx = match conn.transaction() {
        Ok(t) => t,
        Err(e) => return err(500, &e.to_string()),
    };
    let _ = tx.execute("UPDATE sync_ctx SET device=?1", params![device]);
    for rec in &sorted {
        let ident = json!({"entity": rec.get("entity"), "id": rec.get("id")});
        let sp = tx.execute("SAVEPOINT rec", []);
        if sp.is_err() {
            continue;
        }
        match apply(&tx, rec, now_ms) {
            Ok(true) => {
                applied += 1;
                let _ = tx.execute("RELEASE rec", []);
            }
            Ok(false) => {
                let _ = tx.execute("RELEASE rec", []);
                stale.push((rec["entity"].as_str().unwrap_or("").into(), rec["id"].as_str().unwrap_or("").into()));
            }
            Err(reason) => {
                let _ = tx.execute("ROLLBACK TO rec", []);
                let _ = tx.execute("RELEASE rec", []);
                let mut o = ident.as_object().cloned().unwrap_or_default();
                o.insert("reason".into(), Value::String(reason));
                rejected.push(Value::Object(o));
            }
        }
    }
    let _ = tx.execute("UPDATE sync_ctx SET device=NULL", []);
    if let Err(e) = tx.commit() {
        return err(500, &e.to_string());
    }

    // pull
    let rows: Vec<(i64, String, String)> = {
        let mut s = match conn.prepare("SELECT seq,entity,entity_id FROM sync_log WHERE seq>?1 AND (device_id IS NULL OR device_id<>?2) ORDER BY seq LIMIT ?3") {
            Ok(s) => s,
            Err(e) => return err(500, &e.to_string()),
        };
        s.query_map(params![cursor, device, (limit + 1) as i64], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?))).map(|m| m.flatten().collect()).unwrap_or_default()
    };
    let has_more = rows.len() > limit;
    let rows = &rows[..rows.len().min(limit)];
    let max_seq: i64 = conn.query_row("SELECT COALESCE(MAX(seq),0) FROM sync_log", [], |r| r.get(0)).unwrap_or(0);
    let new_cursor = if has_more || !rows.is_empty() { rows.last().map(|r| r.0).unwrap_or(cursor) } else { cursor.max(max_seq) };

    let mut out: Vec<((usize, String), Value)> = vec![];
    let mut seen: std::collections::HashSet<(String, String)> = Default::default();
    for (_, ent, id) in rows.iter().map(|r| (r.0, r.1.clone(), r.2.clone())).chain(stale.iter().map(|s| (0, s.0.clone(), s.1.clone()))) {
        if !seen.insert((ent.clone(), id.clone())) {
            continue;
        }
        if let Some(rec) = record(conn, &ent, &id) {
            let key = (ORDER.iter().position(|e| *e == ent).unwrap_or(9), rec["updated_at"].as_str().unwrap_or("").to_string());
            out.push((key, rec));
        }
    }
    out.sort_by(|a, b| a.0.cmp(&b.0));
    let records: Vec<Value> = out.into_iter().map(|x| x.1).collect();
    let mut resp = Map::new();
    resp.insert("protocol".into(), json!(PROTOCOL));
    resp.insert("cursor".into(), json!(new_cursor));
    resp.insert("has_more".into(), json!(has_more));
    resp.insert("changes".into(), Value::Array(records));
    resp.insert("applied".into(), json!(applied));
    resp.insert("rejected".into(), Value::Array(rejected));
    (200, Value::Object(resp), applied > 0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Db;

    fn setup() -> Connection {
        let path = std::env::temp_dir().join(format!("ss-hub-{}.db", uuid::Uuid::new_v4()));
        let db = Db::open(&path).unwrap();
        drop(db);
        let c = Connection::open(&path).unwrap();
        c
    }

    #[test]
    fn pairing_and_exchange_round_trip() {
        let mut c = setup();
        let mut fails = HashMap::new();
        let key = pairing_key(&c);
        let (code, _, _) = handle(&mut c, &mut fails, "1.2.3.4", "POST", "/api/sync/v1/pair", None, r#"{"key":"WRONG1","device_id":"d1"}"#);
        assert_eq!(code, 401);
        let (code, v, _) = handle(&mut c, &mut fails, "1.2.3.4", "POST", "/api/sync/v1/pair", None, &format!(r#"{{"key":"{key}","device_id":"d1","name":"Phone"}}"#));
        assert_eq!(code, 200);
        let token = v["token"].as_str().unwrap().to_string();
        let auth = format!("Bearer {token}");
        let body = json!({"cursor": 0, "changes": [
            {"entity":"account","id":"a1","updated_at":"2026-01-01T00:00:00.000Z","deleted":false,"data":{"name":"Main","type":"checking","balance":10.0,"currency":"USD"}},
            {"entity":"transaction","id":"t1","updated_at":"2026-01-01T00:00:01.000Z","deleted":false,"data":{"account_id":"a1","amount":-5.5,"description":"Coffee","date":"2026-01-01","created_at":"2026-01-01T00:00:01.000Z"}},
            {"entity":"transaction","id":"bad","updated_at":"2026-01-01T00:00:01.000Z","deleted":false,"data":{"account_id":"nope","amount":1,"description":"x","date":"2026-01-01"}}
        ]});
        let (code, v, dirty) = handle(&mut c, &mut fails, "1.2.3.4", "POST", "/api/sync/v1/exchange", Some(&auth), &body.to_string());
        assert_eq!(code, 200);
        assert!(dirty);
        assert_eq!(v["applied"], 2);
        assert_eq!(v["rejected"].as_array().unwrap().len(), 1);
        // The pusher does not get its own rows back.
        assert_eq!(v["changes"].as_array().unwrap().len(), 0);
        // A second device pulls them.
        let (_, v2, _) = handle(&mut c, &mut fails, "1.2.3.4", "POST", "/api/sync/v1/pair", None, &format!(r#"{{"key":"{key}","device_id":"d2"}}"#));
        let auth2 = format!("Bearer {}", v2["token"].as_str().unwrap());
        let (_, v, _) = handle(&mut c, &mut fails, "1.2.3.4", "POST", "/api/sync/v1/exchange", Some(&auth2), r#"{"cursor":0}"#);
        assert_eq!(v["changes"].as_array().unwrap().len(), 2);
        assert_eq!(v["changes"][0]["entity"], "account");
        // Unauthenticated is refused.
        let (code, _, _) = handle(&mut c, &mut fails, "1.2.3.4", "GET", "/api/sync/v1/status", None, "");
        assert_eq!(code, 401);
    }

    #[test]
    fn pairing_is_rate_limited() {
        let mut c = setup();
        let mut fails = HashMap::new();
        for _ in 0..5 {
            handle(&mut c, &mut fails, "9.9.9.9", "POST", "/api/sync/v1/pair", None, r#"{"key":"NOPE00","device_id":"d"}"#);
        }
        let (code, _, _) = handle(&mut c, &mut fails, "9.9.9.9", "POST", "/api/sync/v1/pair", None, r#"{"key":"NOPE00","device_id":"d"}"#);
        assert_eq!(code, 429);
    }

    #[test]
    fn older_push_loses_and_hub_copy_is_returned() {
        let mut c = setup();
        let mut fails = HashMap::new();
        let key = pairing_key(&c);
        let (_, v, _) = handle(&mut c, &mut fails, "h", "POST", "/api/sync/v1/pair", None, &format!(r#"{{"key":"{key}","device_id":"d1"}}"#));
        let auth = format!("Bearer {}", v["token"].as_str().unwrap());
        c.execute("INSERT INTO accounts(id,name,type,balance,currency,deleted,updated_at) VALUES('a','Hub','checking',1,'USD',0,?1)", params![now()]).unwrap();
        let body = json!({"cursor":0,"changes":[{"entity":"account","id":"a","updated_at":"2020-01-01T00:00:00.000Z","deleted":false,"data":{"name":"Old","type":"checking","balance":9,"currency":"USD"}}]});
        let (_, v, _) = handle(&mut c, &mut fails, "h", "POST", "/api/sync/v1/exchange", Some(&auth), &body.to_string());
        assert_eq!(v["applied"], 0);
        assert_eq!(v["changes"][0]["data"]["name"], "Hub");
    }
}
