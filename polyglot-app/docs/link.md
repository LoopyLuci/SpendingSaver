# Connect from anywhere (optional): sync over Transferd

Sync normally needs the phone and the PC on the same network. **Link** removes that limit: it
carries the *same* sync protocol ([sync.md](sync.md)) over
[Transferd](https://github.com/LoopyLuci/Transferd), an end-to-end encrypted, post-quantum
transfer protocol. The phone and the PC each make only *outbound* connections (to a relay, or
directly when a path exists), so there is no open port, port-forward or VPN, and it works behind
NAT/CGNAT, on mobile data, and across networks.

It is **opt-in on both sides** and changes nothing when off. Both apps stay fully usable alone, and
LAN sync keeps working exactly as before.

## How it fits together

```text
 Android app                                            desktop backend
 ───────────                                            ───────────────
 SyncEngine ── http://127.0.0.1:<port> ──┐         ┌── LinkGateway 127.0.0.1:8126 ── FastAPI
                                         ▼         ▲   (sync protocol only)
 libss_link.so (JNI) ◄═══ Transferd: encrypted, PQ-authenticated stream ═══► ss-link-agent
                          direct path, or blind relay (sees ciphertext only)
```

- `polyglot-app/link` (Rust, crate `spendingsaver-link`) wraps Transferd's overlay node. It builds
  as the desktop sidecar `ss-link-agent` and as `libss_link.so` for Android (JNI).
- The desktop publishes **one** overlay service, `spendingsaver-hub`, to *paired* Transferd devices.
  It points at the **sync gateway** (`backend/app/link_gateway.py`), not at the backend itself.
- On the phone, a hub stored as `link://<desktop link id>` is resolved at sync time to a stable
  `http://127.0.0.1:<port>` endpoint that Transferd carries to the desktop. The sync engine and
  protocol are unchanged.

## Security model (two independent gates)

1. **Transferd pairing.** Only a device that accepted a one-time, expiring, signed invite from this
   desktop may open the service. Identity is Ed25519 + ML-DSA-87; key exchange is X25519 + ML-KEM-768.
2. **Sync pairing.** Over the link the phone *still* needs the pairing key and receives the normal
   per-device bearer token. Holding a link invite is not enough to read or write data.

Hub-side rules that make this safe:

- The link delivers the phone to the backend as *loopback*, which the backend trusts for everything.
  The **gateway** therefore allows only `/api/sync/v1/*` and `/health` (everything else, notably
  `/api/host/*` with the pairing key and the whole user API, is a 404) and rewrites the caller as
  a non-loopback peer so the network guard and device tokens apply as on the LAN.
- The agent's control channel is loopback-only, token-protected, and allows only status, peers,
  invites and probing. It cannot publish other local services.
- The `/api/link/*` management endpoints are available from the hub machine only.
- Link state: desktop `backend/data/link.json` (enabled flag, relays, name) and `backend/data/link/`
  (identity seed, paired peers, audit log: keep private). Android `files/link/` plus the relay list in
  the sync prefs; both are excluded from Android backups. Unpairing the phone keeps its link
  identity; delete `files/link` (clear app data) to forget it.

## Setup

1. **Build the components** (needs Rust, and the Transferd checkout next to this repo:
   `H:\Projects\Transferd`; switch the path dependencies in `polyglot-app/link/Cargo.toml` to a pinned
   git dependency once Transferd's source is published):

   ```powershell
   cd polyglot-app/link; cargo build --release        # ss-link-agent, found automatically
   powershell -ExecutionPolicy Bypass -File scripts/build-link-android.ps1   # needs the Android NDK
   ```

   Then build the Android app as usual. The library is arm64 only; without it the app shows LAN
   sync only. Set `SS_LINK_AGENT` to use an agent binary elsewhere.
2. **A relay (needed to cross networks).** Transferd relays are blind and self-hosted:
   `docker compose -f Transferd/deploy/docker-compose.yml up -d` on any machine both devices can
   reach (UDP 5901), or use its Cloudflare Worker variant. See Transferd's `docs/DEPLOYMENT.md`.
   No relay: only a direct path works (same LAN, or set `SS_LINK_LISTEN` / `SS_LINK_DIRECT` to an
   address the phone can reach).
3. **Desktop:** Phone Sync → *Connect from anywhere* → enter the relay address → **Turn on** →
   **New phone invite**.
4. **Phone:** Settings → Desktop sync → **From anywhere**, paste the invite, enter the pairing key,
   **Pair and sync**. Automatic hourly sync (if enabled) uses the link too.

Environment (desktop agent): `SS_LINK_RELAYS`, `SS_LINK_LISTEN`, `SS_LINK_DIRECT`, `SS_LINK_NAME`,
`SS_LINK_HUB` (default `127.0.0.1:8126`), `SS_LINK_CONTROL` (default `127.0.0.1:8127`).
Backend: `SS_LINK_GATEWAY_PORT`, `SS_LINK_CONTROL_PORT`.

## What is and is not verified

- Verified: two Link nodes with no listener pair with a real invite and carry HTTP through a real
  `relayd` (`polyglot-app/link/tests/e2e.rs`); the gateway's allow-list and the loopback-only control
  plane (`backend/tests/test_link.py`); the Android link-aware API and URL handling (unit tests); the
  Android library cross-compiles.
- Verified on a Nokia 7.2 (arm64, debug build): pairing with a real invite through `libss_link.so`, a
  first sync (8 records received) and a later desktop-to-phone change arriving, with the desktop agent's
  audit log showing the phone opening `spendingsaver-hub`. The path used was a direct TCP route over
  `adb reverse`, because adb cannot forward UDP.
- Not verified yet: the phone reaching a relay over a real network, a relay on the public internet, and a
  phone-to-desktop push over the link (the same engine code, but not exercised there). Treat those as
  untested until you have run them.
