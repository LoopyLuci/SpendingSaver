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
   per-device bearer token. A bare Transferd invite is not enough to read or write data. The QR /
   pairing code the desktop shows does carry both (so one scan pairs), which makes the code itself a
   credential: it is shown only in the desktop's own window, its invite expires in 10 minutes and
   works once, and without a live invite a leaked key cannot reach the hub over the link at all.

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

1. **Build the components** (needs Rust; Transferd is fetched from its public repository, pinned to a commit in
   `polyglot-app/link/Cargo.toml`. The end-to-end test also needs a `relayd` binary: set `RELAYD_BIN`, or it is skipped):

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
   **New phone invite**. A QR code appears (a "Copy code" fallback sits beneath it).
4. **Phone:** Settings → Desktop sync → **From anywhere** → **Scan QR code**. Scanning pairs and syncs
   in one step; nothing is typed. If the camera is unavailable, paste the copied code instead. Automatic
   hourly sync (if enabled) uses the link too.

The pairing code (`ss1:…`, about 270 characters) is the signed Transferd invite and the sync pairing
key packed in binary (`polyglot-app/link/src/code.rs`), less than half the size of the raw invite so the
QR stays sparse and quick to scan. Nothing is re-signed: the phone rebuilds the original invite and
Transferd verifies its signature as usual. The scanner is ZXing (offline, no Google services); the camera
permission is requested only when you tap *Scan QR code*.

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
- Verified on the same phone: a real `ss1:` code pasted into the app paired and synced in one step with no
  key typed (10 records received). The QR scanner opens in portrait with a live camera preview; decoding a
  QR shown on a screen was not exercised end to end (it uses the ZXing library unchanged).
- Not verified yet: the phone reaching a relay over a real network, a relay on the public internet, and a
  phone-to-desktop push over the link (the same engine code, but not exercised there). Treat those as
  untested until you have run them.
