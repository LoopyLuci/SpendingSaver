//! `ss-link-agent`: the desktop sidecar. The backend starts it when the user turns on
//! "Connect from anywhere". It publishes the sync-only gateway to paired devices and serves a
//! tiny line-delimited JSON control channel on loopback.
//!
//! Environment:
//!   SS_LINK_STATE    directory for identity, peers and audit trail (required)
//!   SS_LINK_NAME     device name shown when pairing (default "SpendingSaver Desktop")
//!   SS_LINK_RELAYS   comma-separated relay `host:port` list (UDP); empty = direct only
//!   SS_LINK_LISTEN   optional direct listener, e.g. 0.0.0.0:8128
//!   SS_LINK_DIRECT   comma-separated addresses to publish in invites
//!   SS_LINK_HUB      sync-only gateway to publish (default 127.0.0.1:8126)
//!   SS_LINK_CONTROL  control address (default 127.0.0.1:8127); loopback only
//!   SS_LINK_TOKEN    if set, every control request must carry it as "token"

use std::net::SocketAddr;
use std::sync::Arc;

use serde_json::{json, Value};
use ss_link::{Link, LinkConfig};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};

fn csv(var: &str) -> Vec<String> {
    std::env::var(var)
        .unwrap_or_default()
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect()
}

fn addr_env(var: &str, default: &str) -> Result<SocketAddr, String> {
    std::env::var(var)
        .unwrap_or_else(|_| default.to_string())
        .parse()
        .map_err(|e| format!("{var}: {e}"))
}

async fn serve(stream: TcpStream, link: Arc<Link>, token: Option<String>) {
    let (rd, mut wr) = stream.into_split();
    let mut lines = BufReader::new(rd).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        let reply = match serde_json::from_str::<Value>(&line) {
            Err(e) => json!({"ok": false, "error": format!("bad request: {e}")}),
            Ok(req) => {
                let supplied = req.get("token").and_then(Value::as_str);
                if token.as_deref().is_some_and(|t| supplied != Some(t)) {
                    json!({"ok": false, "error": "unauthorized"})
                } else {
                    let op = req.get("op").and_then(Value::as_str).unwrap_or("");
                    let args = req.get("args").cloned().unwrap_or_else(|| json!({}));
                    match link.call(op, args).await {
                        Ok(v) => json!({"ok": true, "result": v}),
                        Err(e) => json!({"ok": false, "error": e}),
                    }
                }
            }
        };
        let mut out = reply.to_string();
        out.push('\n');
        if wr.write_all(out.as_bytes()).await.is_err() {
            break;
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), String> {
    tracing_subscriber::fmt()
        .with_writer(std::io::stderr) // stdout is reserved for the READY line
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "ss_link_agent=info,transferd_overlay=warn".into()),
        )
        .init();

    let state_dir = std::env::var("SS_LINK_STATE").map_err(|_| "SS_LINK_STATE is required")?;
    let control = addr_env("SS_LINK_CONTROL", "127.0.0.1:8127")?;
    if !control.ip().is_loopback() {
        return Err("SS_LINK_CONTROL must be a loopback address".into());
    }
    let listen = match std::env::var("SS_LINK_LISTEN") {
        Ok(s) if !s.trim().is_empty() => Some(s.parse().map_err(|e| format!("SS_LINK_LISTEN: {e}"))?),
        _ => None,
    };
    let cfg = LinkConfig {
        state_dir: state_dir.into(),
        name: std::env::var("SS_LINK_NAME").unwrap_or_else(|_| "SpendingSaver Desktop".into()),
        relays: csv("SS_LINK_RELAYS"),
        listen,
        direct: csv("SS_LINK_DIRECT"),
        hub: Some(addr_env("SS_LINK_HUB", "127.0.0.1:8126")?),
    };
    let token = std::env::var("SS_LINK_TOKEN").ok().filter(|t| !t.is_empty());

    let link = Arc::new(Link::start(cfg).await?);
    let listener = TcpListener::bind(control).await.map_err(|e| format!("control {control}: {e}"))?;
    tracing::info!(node_id = %link.node_id(), %control, "ss-link-agent ready");
    // The backend waits for this line before talking to the control channel.
    println!("READY {}", link.node_id());

    loop {
        tokio::select! {
            accepted = listener.accept() => {
                if let Ok((stream, _)) = accepted {
                    tokio::spawn(serve(stream, link.clone(), token.clone()));
                }
            }
            _ = tokio::signal::ctrl_c() => break,
        }
    }
    Ok(())
}
