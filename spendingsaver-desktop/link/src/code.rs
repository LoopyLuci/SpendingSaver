//! The pairing code: a Transferd invite plus the sync pairing key in one compact string.
//!
//! A signed invite is JSON in base64 (about 650 characters). Its content is mostly fixed-size
//! binary (ids, fingerprint, nonce, signature), so the code packs it in binary and drops the field
//! names, giving about 280 characters: small enough for a clear, quickly scanned QR code. Nothing
//! is re-signed: [`unpack`] rebuilds the exact invite and Transferd verifies its signature as usual.

use serde_json::{json, Value};
use transferd_overlay::identity::{base64_url_decode, base64_url_encode};

/// What every pairing code starts with, so the app can tell it from a bare invite or a URL.
pub const PREFIX: &str = "ss1:";
const INVITE_PREFIX: &str = "tdov2:";
const FORMAT: u8 = 1;

fn hex_fixed(v: &Value, field: &str, n: usize) -> Result<Vec<u8>, String> {
    let bytes = hex::decode(v.get(field).and_then(Value::as_str).unwrap_or("")).map_err(|_| format!("invite {field} is not hex"))?;
    if bytes.len() == n { Ok(bytes) } else { Err(format!("invite {field} has the wrong length")) }
}

fn put_str(out: &mut Vec<u8>, s: &str) -> Result<(), String> {
    let b = s.as_bytes();
    out.push(u8::try_from(b.len()).map_err(|_| "a field is too long for a pairing code".to_string())?);
    out.extend_from_slice(b);
    Ok(())
}

fn put_list(out: &mut Vec<u8>, v: &Value, field: &str) -> Result<(), String> {
    let items: Vec<&str> = v.get(field).and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).collect()).unwrap_or_default();
    out.push(u8::try_from(items.len()).map_err(|_| "too many addresses".to_string())?);
    items.into_iter().try_for_each(|s| put_str(out, s))
}

/// Pack an invite and the sync pairing key into a pairing code.
pub fn pack(invite: &str, key: &str) -> Result<String, String> {
    let body = invite.trim().strip_prefix(INVITE_PREFIX).ok_or("not a Transferd invite")?;
    let raw = base64_url_decode(body).map_err(|e| e.to_string())?;
    let v: Value = serde_json::from_slice(&raw).map_err(|e| e.to_string())?;
    if v.get("version").and_then(Value::as_u64) != Some(2) {
        return Err("unsupported invite version".into());
    }
    let mut out = vec![FORMAT];
    put_str(&mut out, key.trim())?;
    out.extend(hex_fixed(&v, "node_id", 32)?);
    out.extend(hex_fixed(&v, "key_fingerprint", 32)?);
    out.extend(hex_fixed(&v, "nonce", 16)?);
    out.extend(v.get("expires_at").and_then(Value::as_u64).ok_or("invite has no expiry")?.to_be_bytes());
    out.extend(hex_fixed(&v, "signature", 64)?);
    put_str(&mut out, v.get("name").and_then(Value::as_str).unwrap_or(""))?;
    put_list(&mut out, &v, "relays")?;
    put_list(&mut out, &v, "direct")?;
    Ok(format!("{PREFIX}{}", base64_url_encode(&out)))
}

struct Reader<'a>(&'a [u8]);

impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> Result<&'a [u8], String> {
        if self.0.len() < n {
            return Err("the pairing code is incomplete".into());
        }
        let (head, rest) = self.0.split_at(n);
        self.0 = rest;
        Ok(head)
    }
    fn str(&mut self) -> Result<String, String> {
        let n = self.take(1)?[0] as usize;
        String::from_utf8(self.take(n)?.to_vec()).map_err(|_| "the pairing code is corrupt".to_string())
    }
    fn list(&mut self) -> Result<Vec<String>, String> {
        let n = self.take(1)?[0] as usize;
        (0..n).map(|_| self.str()).collect()
    }
}

/// Unpack a pairing code into the invite and the sync pairing key.
pub fn unpack(code: &str) -> Result<(String, String), String> {
    let body = code.trim().strip_prefix(PREFIX).ok_or("this is not a SpendingSaver pairing code")?;
    let raw = base64_url_decode(body).map_err(|_| "the pairing code is corrupt".to_string())?;
    let mut r = Reader(&raw);
    if r.take(1)?[0] != FORMAT {
        return Err("this pairing code is from a newer version; update the app".into());
    }
    let key = r.str()?;
    let node_id = hex::encode(r.take(32)?);
    let fingerprint = hex::encode(r.take(32)?);
    let nonce = hex::encode(r.take(16)?);
    let expires_at = u64::from_be_bytes(r.take(8)?.try_into().map_err(|_| "corrupt".to_string())?);
    let signature = hex::encode(r.take(64)?);
    let name = r.str()?;
    let relays = r.list()?;
    let direct = r.list()?;
    let invite = json!({
        "version": 2, "node_id": node_id, "name": name, "key_fingerprint": fingerprint,
        "relays": relays, "direct": direct, "expires_at": expires_at, "nonce": nonce, "signature": signature,
    });
    let invite = format!("{INVITE_PREFIX}{}", base64_url_encode(invite.to_string().as_bytes()));
    Ok((invite, key))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> String {
        let v = json!({
            "version": 2, "node_id": "ab".repeat(32), "name": "Desktop", "key_fingerprint": "cd".repeat(32),
            "relays": ["relay.example.com:5901"], "direct": [], "expires_at": 1_900_000_000u64,
            "nonce": "ef".repeat(16), "signature": "01".repeat(64),
        });
        format!("{INVITE_PREFIX}{}", base64_url_encode(v.to_string().as_bytes()))
    }

    #[test]
    fn round_trips_and_is_much_shorter() {
        let invite = sample();
        let code = pack(&invite, "ABCD-1234").unwrap();
        assert!(code.len() < invite.len() * 2 / 3, "{} vs {}", code.len(), invite.len());
        let (back, key) = unpack(&code).unwrap();
        assert_eq!(key, "ABCD-1234");
        let a: Value = serde_json::from_slice(&base64_url_decode(&invite[INVITE_PREFIX.len()..]).unwrap()).unwrap();
        let b: Value = serde_json::from_slice(&base64_url_decode(&back[INVITE_PREFIX.len()..]).unwrap()).unwrap();
        assert_eq!(a, b);
    }

    #[test]
    fn rejects_garbage() {
        assert!(unpack("hello").is_err());
        assert!(unpack("ss1:AAAA").is_err());
        assert!(pack("nope", "k").is_err());
    }
}
