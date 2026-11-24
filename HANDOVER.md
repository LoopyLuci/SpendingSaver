# Handover

## Repository state

SpendingSaver is a multi-project repository. Android, browser, desktop/backend,
and MCP are separate deliverables and have different data stores. No cross-app
sync is implemented by the Android local-backup workflow.

The previous implementation milestone is commit `23f31dd` on `main`, which
moved Android to local-only data and added user-initiated JSON backup/restore.
The Android README and roadmap describe that behavior. Use `git status` and
`git log` for the current state; this file is a handoff note, not a substitute
for repository history.

## Useful starting points

1. [`README.md`](README.md) — project index and data boundaries.
2. [`AGENTS.md`](AGENTS.md) — contribution and data-safety guidance.
3. [`spec/SPEC.md`](spec/SPEC.md) — product requirements, implementation
   status, and unresolved cross-project decisions.
4. [`PLAN.md`](PLAN.md) and [`TODO.md`](TODO.md) — maintenance sequence and
   confirmed follow-ups.
5. The README and runbook for the project being changed.

## Verification context

Latest verification (Windows, JDK 17, Gradle 8.2 wrapper, Python 3.11 clean venv):

- Android `:app:assembleDebug` and `:app:testDebugUnitTest` pass; 49 JVM unit
  tests (sync engine/mapper/URL rules, backup parsing and restore planning, AI
  and tax repositories). There are no instrumented tests.
- Backend `pytest tests/test_api.py tests/test_sync_v1.py` passes (29 tests) in a
  fresh `pip install -e ".[dev]"` environment, against a throwaway database.
- Manual end to end: a debug build on a Nokia 7.2 (Android 11) paired with a
  scratch backend hub over `adb reverse`, synced both ways (desktop account and
  transaction to phone, phone account to desktop, a desktop edit back to the
  phone), and opened and worked with the hub stopped. Not tested: Wi-Fi/LAN
  pairing, multiple phones, HTTPS, conflicting edits on a device, the release
  build, and the browser, MCP and Go/Tauri projects.
- GitHub Actions cannot run for this repository (account billing), so
  `.github/workflows/ci.yml` documents the checks but is not a gate; run them
  locally (see `TODO.md`).

## Important caveats

- Android Room database version 3 removes the former sync outbox and
  sync-metadata columns. Keep migrations data-preserving. Sync reuses the
  existing `updatedAt`/`deleted` columns and needs no schema change.
- Android JSON restore replaces active accounts, categories, transactions,
  and budgets; absent rows become tombstones and restored rows are re-stamped.
  It does not include deleted rows or app settings. It is not sync.
- Sync is opt-in and documented in `spendingsaver-desktop/docs/sync.md`: backend
  `app/modules/sync_v1.py`, Android `com.spendingsaver.sync`. Last write wins per
  row by device clocks. Both apps must stay usable without it.
- The sync device token is in Android's app-private `spendingsaver_sync.xml`
  (excluded from Android backup). Local debug builds can use
  `-PdebugAppIdSuffix=.debug` to install beside an app signed with another key.
- Release signing uses the git-ignored `SpendingSaverAndroid/keystore.properties`.
- Backend runtime data (`spendingsaver-desktop/backend/data/`) is git-ignored; see the
  history note in `TODO.md`.
- The MCP adapter can invoke write operations. Its active server and helper
  client currently have different HTTP/auth handling; review `TODO.md` before
  deploying it against a non-local backend.
- Desktop setup and release packaging depend on tools documented in
  `spendingsaver-desktop/docs/runbook.md`; backend, Go, and frontend services are not a
  single automatically managed installation in every workflow.

## Suggested next step

Select one follow-up from `TODO.md`, confirm it still applies, then inspect the
owning project's code and tests. The browser app and MCP adapter are not part of
sync; do not extend sync to them or combine data stores without an explicit
product decision.
