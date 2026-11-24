# SpendingSaver desktop (native)

A native Rust app built with [egui](https://github.com/emilk/egui). No web view, no Tauri, no helper
processes: one executable, one SQLite file. The window appears in about a tenth of a second because start-up is
only "open the database file and read it".

```powershell
cargo run --release        # or: cargo build --release, then run target/release/spendingsaver
cargo test --release       # 19 tests: storage, tax, calculations, sync hub
```

## What is in it

| Page | What it does |
| --- | --- |
| Dashboard | Net worth, this month's income and spending, spending by category, last six months, budgets, recent activity |
| Transactions | Fast scrolling list (rows are drawn only when visible), search, account filter, add / edit / delete, auto-categorise |
| Accounts, Budgets, Categories | Add, edit, delete, with dropdowns that also accept typed values |
| Tax | US federal estimate for 2025 and 2026 (same engine and numbers as the Android app); a planning aid, not tax advice |
| Import / Export | JSON backup (the same file the Android app makes), CSV export, CSV import |
| Phone Sync | Optional. Turn it on to pair a phone by QR code over your Wi-Fi |
| Settings | Light / dark / system theme, accent colour, default currency |

## Data

`%APPDATA%\SpendingSaver\spendingsaver-desktop.db` (override the folder with `SPENDINGSAVER_DATA`). It is SQLite
in WAL mode. Deleting something marks it deleted (a tombstone) so sync can carry the deletion. The data model matches
the Android JSON backup, so Export here → Restore there (and back) works with no server.

The app does not talk to the network unless you turn on Phone Sync, and then only on your local network.

## Layout

`src/main.rs` window start-up · `app.rs` pages and state · `db.rs` SQLite · `model.rs` records ·
`calc.rs` totals and budgets · `tax.rs` tax engine · `hub.rs` sync protocol v1 server ·
`sync_page.rs` pairing screen and QR · `ui.rs` theme and widgets.
