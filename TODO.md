# Confirmed follow-ups

This list contains repository-level gaps observed in the checked-in projects.
It is not a complete feature wishlist. Recheck each item against current code
before starting work.

- [ ] **Add Android instrumented tests.** JVM unit tests now cover sync, backup
  parsing/planning, AI and tax repositories, but nothing exercises Room
  (DAO queries, `RoomSyncStore`, `DataBackupRepository.restore` in a real
  transaction) or the Compose UI. Add `androidTest` (or Robolectric) coverage.
- [ ] **Add browser-app tests.** `spendingsaver-web` has no test script or test
  files. Start with tests for local persistence and CSV import/export behavior.
- [x] **Add MCP adapter tests.** Done: `spendingsaver-mcp/tests` (mocked
  backend) cover tool-to-API mapping, argument handling, error reporting and
  auth; run with `python -m pytest` there. Not yet in CI.
- [ ] **Verify and align Tauri development startup.** The Tauri config declares
  a Vite `devUrl` but its `beforeDevCommand` runs a production build; verify
  whether `tauri dev` starts the expected Vite server and align the command
  and documentation if not.
- [ ] **Verify the desktop service startup helper end to end.** `scripts/start.py`
  now launches `app.api:app` and `requests` is declared, but the helper has not
  been run together with the Go service and frontend. Run it from a clean
  checkout and document the result.
- [ ] **Finish backend setup reproducibility.** `pip install -e ".[dev]"` from
  `polyglot-app/backend` works in a clean venv and the backend tests pass, but
  the nested `polyglot-app/.github/workflows/*.yml` still reference a
  non-existent `backend/requirements.txt`. Remove or fix them (the root
  `.github/workflows/ci.yml` now covers Android and backend).
- [x] **Unify MCP HTTP client/auth behavior.** Done: `server.py` now calls the
  backend through `client.py`, which sends `SS_MCP_TOKEN` as a bearer token
  when set (also fixes GET arguments, previously sent as a JSON body instead
  of query parameters). The backend's legacy routes still do not check it; see
  the next items.
- [ ] **Extend CI.** The root `.github/workflows/ci.yml` runs the Android
  debug build and unit tests and the backend tests. It does not cover the
  browser app, MCP adapter, Go service, Tauri shell or packaging; it has not yet
  been run on GitHub.
- [ ] **Secure the legacy backend routes.** `/api/sync/upsert`,
  `/api/sync/changes`, `/api/host/pair` and nearly all CRUD/financial routes are
  unauthenticated, and the legacy sync routes can write 21 tables. Sync v1 is
  authenticated per device, but anyone who can reach the backend port can still
  use the rest of the API. Keep the backend on loopback/trusted LAN until
  these are protected; remove the legacy sync routes if nothing uses them.
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
