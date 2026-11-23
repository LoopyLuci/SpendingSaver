# Confirmed follow-ups

This list contains repository-level gaps observed in the checked-in projects.
It is not a complete feature wishlist. Recheck each item against current code
before starting work.

- [x] **Android instrumented tests.** `app/src/androidTest/.../RoomSyncAndBackupTest.kt`
  (in-memory Room: `RoomSyncStore` last-write-wins and tombstones, backup restore,
  invalid-file rejection, export/restore round trip): 6 tests, passed on a Nokia 7.2
  with `gradlew.bat :app:connectedDebugAndroidTest -PdebugAppIdSuffix=.debug`.
  The Compose UI and DAO migrations still have no tests.
- [x] **Add browser-app tests.** `spendingsaver-web` now has `npm test` (Vitest +
  fake-indexeddb) covering local persistence and CSV import/export. UI and
  `budget.ts` remain untested.
- [x] **Add MCP adapter tests.** Done: `spendingsaver-mcp/tests` (mocked
  backend) cover tool-to-API mapping, argument handling, error reporting and
  auth; run with `python -m pytest` there. Not yet in CI.
- [ ] **Desktop packaging is unverified.** `tauri dev` now runs Vite (config
  fixed) and `scripts/start.py` works with the Go service, but neither
  `tauri dev`/`tauri build` was run. The bundle expects
  `backend/dist-offline/backend-service` (no build script exists) and the
  Windows signing config is a placeholder. No CI covers Rust tests or
  packaging.
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
  JWT or `SS_SYNC_TOKEN`; loopback callers are unchanged. Remaining: routes
  are still not per-user authenticated for local callers, and the legacy
  `/api/sync/upsert`/`changes` routes still exist (nothing in this repo uses them
  except `scripts/verify_offline.py`); remove them once older clients are gone.
- [ ] **Sync follow-ups.** Not synced: category hierarchy (`parentId`), budget
  carry-over, recurring items and other desktop-only data. No background/
  automatic sync, no per-field merge, no TLS in the backend, no desktop UI for
  the pairing key or device list (use the loopback API), device token is not
  hardware-backed on Android, and the backend treats data as a single owner's.
- [ ] **Git history contains private data.** The initial commit tracked
  `polyglot-app/backend/data/` (a live SQLite database, WAL files, a backup and
  a pairing key). They are untracked now and the pairing key was rotated, but
  they remain in history. Decide whether to rewrite history and treat anything
  in that database as exposed if the repository was ever shared.
- [ ] **Review the Android product roadmap.** The existing roadmap contains a
  broad proposed feature set. Confirm priorities and update its baseline as
  Android capabilities change; do not treat planned features as implemented.
