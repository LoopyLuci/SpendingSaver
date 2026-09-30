# Changelog
All notable changes to this project will be documented in this file.

## [0.1.0] - 2026-08-16
### Added
- Polyglot desktop app shell: Tauri/TypeScript UI + Python FastAPI + Go HTTP service
- Accounts, Categories, Transactions, Budgets, Recurring, Import/Export, Settings, Diagnostics pages
- Rust sidecar process management for Python/Go backends with status events
- CSV export, JSON backup export, CSV import with backend validation
- Recurring transactions with auto-creation scheduler
- Budget alerts, net worth over time chart, theme toggle
- Account/category deletion safeguards, transaction search/filtering
- Alembic migrations, retry resilience, release verification script

### Changed
- Product name: PolyglotApp -> SpendingSaver
- Tauri updater endpoint and Windows signing metadata configured
