# Confirmed follow-ups

This list contains repository-level gaps observed in the checked-in projects.
It is not a complete feature wishlist. Recheck each item against current code
before starting work.

- [x] **Android instrumented tests.** `app/src/androidTest/.../RoomSyncAndBackupTest.kt`
  (in-memory Room: `RoomSyncStore` last-write-wins and tombstones, backup restore,
  invalid-file rejection, export/restore round trip): 6 tests, passed on a Nokia 7.2
  with `gradlew.bat :app:connectedDebugAndroidTest -PdebugAppIdSuffix=.debug`.
  The Compose UI and DAO migrations still have no tests.
- [x] **Browser app rebuilt as an installable offline PWA.** `spendingsaver-web` has 85 Vitest tests
  (money, budgets, recurring, CSV, backup round trip, assistant, tax parity, storage, build/precache
  consistency); React UI is verified by hand in Edge only, with no component tests. Sync with the hub is not
  implemented (records carry id/updatedAt/deleted for it).
- [x] **Add MCP adapter tests.** Done: `spendingsaver-mcp/tests` (mocked
  backend) cover tool-to-API mapping, argument handling, error reporting and
  auth; run with `python -m pytest` there. Not yet in CI.
- [ ] **Desktop packaging is unverified.** The native desktop app builds with `cargo build --release` and starts in
  about 0.1 s, but there is no installer, no code signing, and it has only been run on Windows.
- [x] **Desktop parity with the old Tauri app.** Done natively: recurring items, goals, debts, the on-device
  assistant and the optional SimpleFIN bank link. Connect from anywhere (Transferd link) and transaction attachments (receipts) are now
  in the native app too, but only unit tested plus a real agent start and a phone code: the link has not carried a
  desktop sync from a phone on another network, and attachments have not been tried through the file picker. Recurring items, goals and debts are desktop-only and are not in
  the Android backup or phone sync. The SimpleFIN link has unit tests for parsing and import but has not been run
  against a live bridge account.
- [ ] **Android release APK is signed but untested on a phone.** A release key exists locally (outside the repo) and
  the signed APK builds with a baseline profile. Installing it over a debug build needs an uninstall, so jank and
  start-up have only been measured on the debug build (3.0 s cold start, 62% janky frames while navigating, which
  is expected for a debug build).
- [x] **Unify MCP HTTP client/auth behavior.** Done: `server.py` now calls the
  backend through `client.py`, which sends `SS_MCP_TOKEN` as a bearer token
  when set (also fixes GET arguments, previously sent as a JSON body instead
  of query parameters). The backend's legacy routes still do not check it; see
  the next items.
- [ ] **Extend CI.** The root `.github/workflows/ci.yml` runs the Android
  debug build and unit tests and the backend tests. It also runs backend migrations, Go tests and the desktop frontend. It does not cover the
  browser app, MCP adapter, Rust shell or packaging. GitHub Actions cannot
  run for this repository (the account's billing is not going to be fixed), so
  the workflow documents the checks and they are run locally: Android
  `./gradlew :app:testDebugUnitTest`, backend `pytest`, `spendingsaver-web`
  `npm test`, `spendingsaver-mcp` `python -m pytest`.
- [x] **Secure the legacy backend routes.** Done as a network guard
  (`app/security.py`, `docs/sync.md`): callers from other machines need a user
  JWT or `SS_SYNC_TOKEN`; loopback callers are unchanged. The legacy
  `/api/sync/upsert`/`changes` routes were removed. Remaining: routes are still not
  per-user authenticated for callers on the hub machine itself.
- [ ] **Sync follow-ups.** Category hierarchy, the desktop "Phone Sync" page and an
  opt-in hourly Android background sync now exist. The background worker has only
  been compiled and unit-tested around, not observed running on a device. Not
  synced: budget carry-over, recurring items and other desktop-only data. No
  per-field merge, no TLS in the backend, device token is not hardware-backed on
  Android, and the backend treats data as a single owner's.
- [ ] **Git history contains private data.** The initial commit tracked
  `spendingsaver-desktop/backend/data/` (a live SQLite database, WAL files, a backup and
  a pairing key). They are untracked now and the pairing key was rotated, but
  they remain in history. Decide whether to rewrite history and treat anything
  in that database as exposed if the repository was ever shared.
- [ ] **Review the Android product roadmap.** The existing roadmap contains a
  broad proposed feature set. Confirm priorities and update its baseline as
  Android capabilities change; do not treat planned features as implemented.

- [ ] **Legacy flavor coverage.** Only a launch on an Android 5.1 tablet is verified. Try
  CSV import/export (commons-logging lint check is suppressed), backup/restore, LAN sync
  and the updater on a legacy device.
- [ ] **Updater against a flavored release.** The asset picking is unit tested; it has not
  yet run end to end against the published 1.1.3 release.
