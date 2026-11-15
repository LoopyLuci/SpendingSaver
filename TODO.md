# Confirmed follow-ups

This list contains repository-level gaps observed in the checked-in projects.
It is not a complete feature wishlist. Recheck each item against current code
before starting work.

- [ ] **Test Android backup/restore behavior.** The app has no checked-in
  `test` or `androidTest` sources. Add focused tests for backup serialization,
  restore replacement, and invalid input before treating backup restore as
  regression-covered.
- [ ] **Add browser-app tests.** `spendingsaver-web` has no test script or test
  files. Start with tests for local persistence and CSV import/export behavior.
- [ ] **Add MCP adapter tests.** `spendingsaver-mcp` has no test suite. Cover
  tool-to-API mapping, argument handling, and backend error reporting.
- [ ] **Verify and align Tauri development startup.** The Tauri config declares
  a Vite `devUrl` but its `beforeDevCommand` runs a production build; verify
  whether `tauri dev` starts the expected Vite server and align the command
  and documentation if not.
- [ ] **Repair the desktop service startup path.** `scripts/start.py` launches
  Uvicorn as `app:app`, while the FastAPI application is exposed as
  `app.api:app`; it also imports `requests` without declaring it in the backend
  project metadata. Verify and correct the helper before recommending it as
  the normal startup command.
- [ ] **Make backend dependency setup reproducible.** There is no
  `backend/requirements.txt`, although the nested package workflow refers to
  one, and `backend/pyproject.toml` omits runtime imports such as SQLAlchemy
  and Alembic. Define and verify a clean-environment install before documenting
  a one-command backend setup.
- [ ] **Unify MCP HTTP client/auth behavior.** The active MCP server requests
  the backend directly in `server.py`; the separate `client.py` helper adds a
  bearer token from `SS_MCP_TOKEN`. Decide which path is intended, then test
  the authentication behavior and document it.
- [ ] **Make CI workflows discoverable and complete.** Workflow files are
  nested under `polyglot-app/.github/workflows`; GitHub Actions only discovers
  workflows under the repository-root `.github/workflows`. There is no
  root-level workflow covering the projects, and the nested package workflow
  refers to the missing backend requirements file. Choose which checks should
  run in CI and verify them from a clean checkout.
- [ ] **Review the Android product roadmap.** The existing roadmap contains a
  broad proposed feature set. Confirm priorities and update its baseline as
  Android capabilities change; do not treat planned features as implemented.
