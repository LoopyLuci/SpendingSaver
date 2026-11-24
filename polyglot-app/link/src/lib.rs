//! SpendingSaver Link: a thin, opt-in wrapper over the Transferd overlay node.
//!
//! The sync protocol (`polyglot-app/docs/sync.md`) is unchanged. Link only changes *how bytes
//! travel*: instead of a LAN address, the phone talks to a local endpoint that Transferd carries
//! to the desktop over a relay or a direct path, end-to-end encrypted.

use std::net::SocketAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use serde_json::{json, Value};
use transferd_overlay::node::{OverlayConfig, OverlayNode};
use transferd_overlay::NodeIdentity;

pub mod code;
#[cfg(target_os = "android")]
mod android;

/// The overlay service name the desktop publishes. The phone connects to this.
pub const HUB_SERVICE: &str = "spendingsaver-hub";

/// How a Link node is set up.
#[derive(Clone, Debug)]
pub struct LinkConfig {
    /// Where the identity seed, paired peers and audit trail are kept. Must be app-private.
    pub state_dir: PathBuf,
    /// The name this device is known by when it pairs.
    pub name: String,
    /// Relays to register on (`host:port`, UDP). Empty means direct-only.
    pub relays: Vec<String>,
    /// Optional direct listener (desktop: lets peers on the same network skip the relay).
    pub listen: Option<SocketAddr>,
    /// Extra addresses to publish in invites for direct dialling.
    pub direct: Vec<String>,
    /// Desktop only: the local address of the sync-only gateway to publish as [`HUB_SERVICE`].
    pub hub: Option<SocketAddr>,
}

/// A running Link node.
pub struct Link {
    node: Arc<OverlayNode>,
    cfg: LinkConfig,
}

fn load_or_create_seed(dir: &Path) -> Result<[u8; 64], String> {
    std::fs::create_dir_all(dir).map_err(|e| format!("cannot create {}: {e}", dir.display()))?;
    let path = dir.join("identity.seed");
    if let Ok(bytes) = std::fs::read(&path) {
        if let Ok(seed) = <[u8; 64]>::try_from(bytes.as_slice()) {
            return Ok(seed);
        }
        return Err(format!("{} is damaged; remove it to create a new identity", path.display()));
    }
    let mut seed = [0u8; 64];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut seed);
    std::fs::write(&path, seed).map_err(|e| format!("cannot write {}: {e}", path.display()))?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600));
    }
    Ok(seed)
}

/// Operations a controller may run. `overlay.expose` is deliberately absent: a controller must
/// never be able to publish an arbitrary local service to peers.
const ALLOWED_OVERLAY_OPS: &[&str] = &[
    "overlay.status",
    "overlay.peers",
    "overlay.invite_create",
    "overlay.invite_accept",
    "overlay.probe",
    "overlay.events",
    "overlay.audit",
];

impl Link {
    /// Start the node. Nothing listens for inbound connections unless `cfg.listen` is set.
    pub async fn start(cfg: LinkConfig) -> Result<Self, String> {
        let seed = load_or_create_seed(&cfg.state_dir)?;
        let identity = NodeIdentity::from_seed(&seed, cfg.name.clone());
        let mut oc = OverlayConfig::local(identity);
        oc.relays = cfg.relays.clone();
        oc.direct = cfg.direct.clone();
        oc.listen = cfg.listen;
        oc.state_path = Some(cfg.state_dir.join("peers.json"));
        oc.audit_path = Some(cfg.state_dir.join("audit.log"));
        let node = OverlayNode::start(oc).await.map_err(|e| e.to_string())?;
        if let Some(hub) = cfg.hub {
            // Only paired devices (those that accepted this node's invite) may open it.
            node.expose(HUB_SERVICE, hub, vec!["*paired".to_string()], "SpendingSaver sync gateway")
                .map_err(|e| e.to_string())?;
        }
        Ok(Self { node, cfg })
    }

    /// This node's id.
    pub fn node_id(&self) -> String {
        self.node.node_id()
    }

    /// Run one control operation and return its JSON result.
    pub async fn call(&self, op: &str, args: Value) -> Result<Value, String> {
        match op {
            "link.info" => Ok(json!({
                "node_id": self.node.node_id(),
                "name": self.cfg.name,
                "relays": self.cfg.relays,
                "direct": self.node.published_direct(),
                "hub_published": self.cfg.hub.is_some(),
                "peers": self.node.peer_list(),
            })),
            "link.make_code" => {
                let invite = args.get("invite").and_then(Value::as_str).ok_or("link.make_code needs invite")?;
                let key = args.get("key").and_then(Value::as_str).ok_or("link.make_code needs key")?;
                Ok(json!({"code": code::pack(invite, key)?}))
            }
            "link.decode_code" => {
                let c = args.get("code").and_then(Value::as_str).ok_or("link.decode_code needs code")?;
                let (invite, key) = code::unpack(c)?;
                Ok(json!({"invite": invite, "key": key}))
            }
            "link.accept_invite" => {
                // Verifies the invite (signature, expiry, key fingerprint) and pairs. The reply
                // carries the relays the inviter registers on: a node only registers on the relays
                // it was started with, so the caller restarts with those added.
                let invite = args.get("invite").and_then(Value::as_str).ok_or("link.accept_invite needs invite")?;
                let name = args.get("name").and_then(Value::as_str).map(str::to_string);
                let record = self.node.invite_accept(invite, name).map_err(|e| e.to_string())?;
                let reach = transferd_overlay::identity::invite_reach(invite);
                Ok(json!({
                    "node_id": record.node_id,
                    "name": record.name,
                    "relays": reach.relays,
                    "direct": reach.direct,
                }))
            }
            "link.connect_hub" => {
                let target = args
                    .get("node_id")
                    .and_then(Value::as_str)
                    .ok_or("link.connect_hub needs node_id")?;
                let (addr, route) = self
                    .node
                    .connect(target, HUB_SERVICE)
                    .await
                    .map_err(|e| e.to_string())?;
                Ok(json!({"local_addr": addr.to_string(), "route": route.as_str()}))
            }
            op if ALLOWED_OVERLAY_OPS.contains(&op) => {
                transferd_overlay::ops::call(&self.node, op, args)
                    .await
                    .map_err(|e| e.to_string())
            }
            other => Err(format!("operation {other:?} is not available")),
        }
    }
}
