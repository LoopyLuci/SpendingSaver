//! "Connect from anywhere": runs the optional `ss-link-agent` sidecar (the Transferd transport) and
//! talks to its loopback control channel. The agent publishes this app's phone-sync hub to *paired*
//! Transferd devices over a relay or direct path, end to end encrypted, with no open port. The hub
//! itself only serves `/api/sync/v1/*` and `/health`, so nothing else is reachable through the link,
//! and a phone still needs the pairing key and gets a per-device token exactly as on the Wi-Fi.
//!
//! The agent is a separate executable so this app starts instantly and works without it: when it is
//! missing or the user never turns the link on, nothing here runs.
use serde_json::{json, Value};
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::time::Duration;

pub struct Link {
    child: Option<Child>,
    /// Held open so the agent can tell when this app is gone (see SS_LINK_EXIT_ON_STDIN_EOF).
    _keepalive: Option<std::process::ChildStdin>,
    pub node_id: String,
    control: u16,
    token: String,
}

impl Drop for Link {
    fn drop(&mut self) {
        if let Some(c) = &mut self.child {
            let _ = c.kill();
            let _ = c.wait();
        }
    }
}

/// Where to find the agent: `SS_LINK_AGENT`, next to this executable (the installer puts it there),
/// or the cargo build output of a source checkout.
pub fn find_agent() -> Option<PathBuf> {
    let name = if cfg!(windows) { "ss-link-agent.exe" } else { "ss-link-agent" };
    let mut candidates: Vec<PathBuf> = vec![];
    if let Ok(p) = std::env::var("SS_LINK_AGENT") {
        candidates.push(PathBuf::from(p));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.join(name));
            // target/release -> desktop -> repository folder, then link/target/release
            for up in dir.ancestors().skip(1).take(4) {
                candidates.push(up.join("link").join("target").join("release").join(name));
            }
        }
    }
    candidates.into_iter().find(|p| p.is_file())
}

/// Splits a comma/space separated list into `host:port` relays, rejecting anything else.
pub fn parse_relays(text: &str) -> Result<Vec<String>, String> {
    let mut out = vec![];
    for part in text.split(|c: char| c == ',' || c.is_whitespace()).filter(|s| !s.is_empty()) {
        let Some((host, port)) = part.rsplit_once(':') else { return Err(format!("\"{part}\" should look like host:port, for example relay.example.com:5901")) };
        let ok_host = !host.is_empty() && host.len() <= 253 && host.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '[' | ']' | ':'));
        if !ok_host || port.parse::<u16>().map(|p| p == 0).unwrap_or(true) {
            return Err(format!("\"{part}\" is not a valid relay address (host:port)"));
        }
        out.push(part.to_string());
    }
    Ok(out)
}

fn free_loopback_port() -> Result<u16, String> {
    let l = TcpListener::bind(("127.0.0.1", 0)).map_err(|e| e.to_string())?;
    l.local_addr().map(|a| a.port()).map_err(|e| e.to_string())
}

/// Starts the agent and waits (up to 30 s, so call this off the UI thread) until it is ready.
pub fn start(state_dir: &Path, name: &str, relays: &[String], hub_port: u16) -> Result<Link, String> {
    let agent = find_agent().ok_or("The link component (ss-link-agent) is not installed. Build it from spendingsaver-desktop/link, or use a release installer.")?;
    std::fs::create_dir_all(state_dir).map_err(|e| format!("cannot create {}: {e}", state_dir.display()))?;
    let control = free_loopback_port()?;
    let token = uuid::Uuid::new_v4().simple().to_string();
    let mut cmd = Command::new(&agent);
    cmd.env("SS_LINK_STATE", state_dir)
        .env("SS_LINK_NAME", name)
        .env("SS_LINK_RELAYS", relays.join(","))
        .env("SS_LINK_HUB", format!("127.0.0.1:{hub_port}"))
        .env("SS_LINK_CONTROL", format!("127.0.0.1:{control}"))
        .env("SS_LINK_TOKEN", &token)
        .env("SS_LINK_EXIT_ON_STDIN_EOF", "1")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    let mut child = cmd.spawn().map_err(|e| format!("could not start the link component: {e}"))?;
    let keepalive = child.stdin.take();
    let stdout = child.stdout.take().ok_or("no output from the link component")?;
    let (tx, rx) = mpsc::channel();
    std::thread::spawn(move || {
        for line in BufReader::new(stdout).lines().map_while(Result::ok) {
            if let Some(id) = line.strip_prefix("READY ") {
                let _ = tx.send(id.trim().to_string());
                return;
            }
        }
        // EOF before READY: the agent exited; dropping tx tells the waiter.
    });
    match rx.recv_timeout(Duration::from_secs(30)) {
        Ok(node_id) => Ok(Link { child: Some(child), _keepalive: keepalive, node_id, control, token }),
        Err(_) => {
            let _ = child.kill();
            let _ = child.wait();
            Err("The link component did not start. Check the relay address and that the state folder is writable.".into())
        }
    }
}

impl Link {
    #[cfg(test)]
    fn for_test(control: u16, token: &str) -> Link {
        Link { child: None, _keepalive: None, node_id: "test".into(), control, token: token.into() }
    }

    /// One control request. The agent only answers on loopback and only with this run's token.
    pub fn call(&self, op: &str, args: Value) -> Result<Value, String> {
        let mut s = TcpStream::connect_timeout(&([127, 0, 0, 1], self.control).into(), Duration::from_secs(5)).map_err(|e| format!("link control: {e}"))?;
        s.set_read_timeout(Some(Duration::from_secs(20))).ok();
        s.set_write_timeout(Some(Duration::from_secs(5))).ok();
        let req = json!({"op": op, "args": args, "token": self.token}).to_string() + "\n";
        s.write_all(req.as_bytes()).map_err(|e| format!("link control: {e}"))?;
        let mut line = String::new();
        BufReader::new(s).read_line(&mut line).map_err(|e| format!("link control: {e}"))?;
        let v: Value = serde_json::from_str(line.trim()).map_err(|e| format!("bad reply from the link component: {e}"))?;
        if v["ok"].as_bool() == Some(true) {
            Ok(v["result"].clone())
        } else {
            Err(v["error"].as_str().unwrap_or("link error").to_string())
        }
    }

    /// A one-time, expiring code for the phone: the signed Transferd invite and the sync pairing key
    /// in one scannable string. Shown only in this window.
    pub fn phone_code(&self, pairing_key: &str) -> Result<String, String> {
        let inv = self.call("overlay.invite_create", json!({"ttl_s": 600}))?;
        let invite = inv["invite"].as_str().ok_or("the link component returned no invite")?;
        let packed = self.call("link.make_code", json!({"invite": invite, "key": pairing_key}))?;
        packed["code"].as_str().map(str::to_string).ok_or_else(|| "the link component returned no code".into())
    }

    /// Names of the Transferd devices paired with this computer.
    pub fn peers(&self) -> Vec<String> {
        self.call("link.info", json!({}))
            .ok()
            .and_then(|v| v["peers"].as_array().cloned())
            .map(|a| a.iter().map(|p| p["name"].as_str().filter(|s| !s.is_empty()).unwrap_or("Phone").to_string()).collect())
            .unwrap_or_default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A stand-in for the agent's control channel that checks the token and answers two ops.
    fn fake_agent(token: &'static str) -> u16 {
        let l = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = l.local_addr().unwrap().port();
        std::thread::spawn(move || {
            for stream in l.incoming().flatten() {
                let mut line = String::new();
                BufReader::new(stream.try_clone().unwrap()).read_line(&mut line).unwrap();
                let req: Value = serde_json::from_str(&line).unwrap();
                let reply = if req["token"] != token {
                    json!({"ok": false, "error": "unauthorized"})
                } else {
                    match req["op"].as_str().unwrap() {
                        "overlay.invite_create" => json!({"ok": true, "result": {"invite": "INVITE"}}),
                        "link.make_code" => json!({"ok": true, "result": {"code": format!("ss1:{}:{}", req["args"]["invite"].as_str().unwrap(), req["args"]["key"].as_str().unwrap())}}),
                        "link.info" => json!({"ok": true, "result": {"peers": [{"name": "Pixel"}, {"name": ""}]}}),
                        _ => json!({"ok": false, "error": "not available"}),
                    }
                };
                let mut s = stream;
                let _ = s.write_all((reply.to_string() + "\n").as_bytes());
            }
        });
        port
    }

    #[test]
    fn builds_a_phone_code_from_invite_and_key() {
        let link = Link::for_test(fake_agent("tok"), "tok");
        assert_eq!(link.phone_code("ABC123").unwrap(), "ss1:INVITE:ABC123");
        assert_eq!(link.peers(), vec!["Pixel".to_string(), "Phone".to_string()]);
    }

    #[test]
    fn wrong_token_and_unknown_ops_are_errors() {
        let port = fake_agent("tok");
        assert_eq!(Link::for_test(port, "nope").call("link.info", json!({})).unwrap_err(), "unauthorized");
        assert_eq!(Link::for_test(port, "tok").call("shell.exec", json!({})).unwrap_err(), "not available");
    }

    #[test]
    fn relay_list_is_validated() {
        assert_eq!(parse_relays("relay.example.com:5901, 10.0.0.2:5901").unwrap(), vec!["relay.example.com:5901", "10.0.0.2:5901"]);
        assert!(parse_relays("").unwrap().is_empty());
        assert!(parse_relays("no-port").is_err());
        assert!(parse_relays("host:0").is_err());
        assert!(parse_relays("ho st;rm:5901").is_err());
        assert!(parse_relays("https://x:5901").is_err());
    }

    /// Runs the real agent when it has been built (skipped otherwise): it starts, hands out a scannable
    /// phone code, and stops with the Link.
    #[test]
    fn real_agent_makes_a_phone_code() {
        if find_agent().is_none() {
            eprintln!("ss-link-agent not built; skipping");
            return;
        }
        let dir = std::env::temp_dir().join(format!("ss-link-test-{}", uuid::Uuid::new_v4().simple()));
        let link = start(&dir, "Test Desktop", &["127.0.0.1:59".to_string()], 8125).expect("agent starts");
        let code = link.phone_code("ABC123").expect("code");
        assert!(code.starts_with("ss1:"), "{code}");
        assert!(code.len() < 600, "code should stay small enough for a sparse QR: {}", code.len());
        drop(link);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
