//! Two Link nodes with NO inbound listener reach each other through a real `relayd`, using the
//! real pairing flow (desktop creates an invite, phone accepts), and carry real HTTP bytes.
//! Needs the reference relay binary: set RELAYD_BIN, or build it in the sibling Transferd
//! checkout (`cargo build -p relayd`). The test is skipped, loudly, when it is missing.
#![allow(clippy::unwrap_used)]

use std::net::SocketAddr;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

use serde_json::json;
use ss_link::{Link, LinkConfig, HUB_SERVICE};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

fn relayd_bin() -> Option<std::path::PathBuf> {
    if let Ok(p) = std::env::var("RELAYD_BIN") {
        return Some(p.into());
    }
    let base = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../Transferd/target/debug");
    ["relayd.exe", "relayd"].iter().map(|n| base.join(n)).find(|p| p.exists())
}

struct Relay(Child);
impl Drop for Relay {
    fn drop(&mut self) {
        let _ = self.0.kill();
    }
}

fn free_udp_port() -> u16 {
    std::net::UdpSocket::bind("127.0.0.1:0").unwrap().local_addr().unwrap().port()
}

/// A stand-in for the sync gateway: answers any request with a fixed HTTP 200.
async fn fake_hub() -> SocketAddr {
    let l = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = l.local_addr().unwrap();
    tokio::spawn(async move {
        loop {
            let Ok((mut s, _)) = l.accept().await else { return };
            tokio::spawn(async move {
                let mut buf = [0u8; 1024];
                let _ = s.read(&mut buf).await;
                let body = "hub-says-hello";
                let _ = s
                    .write_all(
                        format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len())
                            .as_bytes(),
                    )
                    .await;
            });
        }
    });
    addr
}

fn cfg(dir: &std::path::Path, name: &str, relay: SocketAddr, hub: Option<SocketAddr>) -> LinkConfig {
    LinkConfig {
        state_dir: dir.to_path_buf(),
        name: name.into(),
        relays: vec![relay.to_string()],
        listen: None,
        direct: vec![],
        hub,
    }
}

#[tokio::test(flavor = "multi_thread", worker_threads = 8)]
async fn phone_reaches_desktop_hub_through_relay_with_no_open_port() {
    let Some(bin) = relayd_bin() else {
        eprintln!("SKIPPED: relayd binary not found (set RELAYD_BIN)");
        return;
    };
    let port = free_udp_port();
    let relay_addr: SocketAddr = format!("127.0.0.1:{port}").parse().unwrap();
    let _relay = Relay(
        Command::new(bin)
            .env("RELAYD_BIND", relay_addr.to_string())
            .env("RELAYD_DIFFICULTY", "10")
            .stdout(Stdio::null())
            .spawn()
            .unwrap(),
    );
    tokio::time::sleep(Duration::from_millis(500)).await;

    let tmp = std::env::temp_dir().join(format!("sslink-e2e-{}", std::process::id()));
    let hub = fake_hub().await;
    let desktop = Link::start(cfg(&tmp.join("desktop"), "Desktop", relay_addr, Some(hub))).await.unwrap();
    let phone = Link::start(cfg(&tmp.join("phone"), "Phone", relay_addr, None)).await.unwrap();

    // Unpaired devices must not get through.
    assert!(phone.call("link.connect_hub", json!({"node_id": desktop.node_id()})).await.is_err());

    // The real pairing flow.
    let invite = desktop.call("overlay.invite_create", json!({"ttl_s": 600})).await.unwrap();
    let invite = invite["invite"].as_str().unwrap().to_string();
    phone.call("overlay.invite_accept", json!({"invite": invite, "name": "Desktop"})).await.unwrap();

    let ep = phone.call("link.connect_hub", json!({"node_id": desktop.node_id()})).await.unwrap();
    assert_eq!(ep["route"], "relay", "no listener anywhere, so the relay carries it");
    let local: SocketAddr = ep["local_addr"].as_str().unwrap().parse().unwrap();

    let mut s = TcpStream::connect(local).await.unwrap();
    s.write_all(b"GET /health HTTP/1.1\r\nHost: x\r\n\r\n").await.unwrap();
    let mut out = String::new();
    tokio::time::timeout(Duration::from_secs(15), s.read_to_string(&mut out)).await.unwrap().unwrap();
    assert!(out.contains("hub-says-hello"), "got: {out}");

    // A controller cannot publish arbitrary local services.
    assert!(desktop.call("overlay.expose", json!({"service": "x", "local_addr": "127.0.0.1:1"})).await.is_err());
    let _ = HUB_SERVICE;
    let _ = std::fs::remove_dir_all(tmp);
}
