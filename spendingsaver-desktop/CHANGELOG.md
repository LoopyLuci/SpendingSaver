# Changelog
All notable changes to this project will be documented in this file.

## [Unreleased]
### Added
- Opt-in sync hub for paired Android devices (`/api/sync/v1`): per-device tokens,
  change log, last-write-wins merge, device listing/revocation. See `docs/sync.md`.
- Alembic revision `a1c3e5f70b21` (adds `sync_log`, `sync_devices`).
- `SS_DB_PATH` / `SS_DATA_DIR` to relocate the database and pairing key (used by tests).

### Changed
- The pairing key is only returned to, and can only be rotated from, the hub machine.
- Backend metadata declares `sqlalchemy`, `alembic`, `httpx`, `requests`,
  `python-multipart` and `pyjwt`; `scripts/start.py` launches `app.api:app`.
- The live database, WAL files, backups and pairing key are no longer tracked by git.

## [0.1.0] - 2026-08-16
### Added
- Desktop app shell: Tauri/TypeScript UI + Python FastAPI + Go HTTP service
- Accounts, Categories, Transactions, Budgets, Recurring, Import/Export, Settings, Diagnostics pages
- Rust sidecar process management for Python/Go backends with status events
- CSV export, JSON backup export, CSV import with backend validation
- Recurring transactions with auto-creation scheduler
- Budget alerts, net worth over time chart, theme toggle
- Account/category deletion safeguards, transaction search/filtering
- Alembic migrations, retry resilience, release verification script

### Changed
- Product name unified as SpendingSaver
- Tauri updater endpoint and Windows signing metadata configured
