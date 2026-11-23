# SpendingSaver Web

This directory is a standalone browser application built with React,
TypeScript, Vite, and Dexie (IndexedDB). It is separate from the Tauri desktop
frontend in `polyglot-app/tauri-app`; installing or running one does not start
the other.

## Requirements

- Node.js and npm

## Develop

From this directory:

```powershell
npm ci
npm run dev
```

Vite prints the local URL after startup. For a production build:

```powershell
npm run build
npm run preview
```

`npm run build` runs the TypeScript compiler before Vite builds the app.

## Data

The app's database is implemented in `src/db/db.ts` with Dexie and is stored
in the browser's IndexedDB. Browser storage is specific to the browser profile
and origin. Do not assume it is shared with the Android app or desktop backend.
The Import/Export page imports transactions from CSV into a selected account
and exports transactions to CSV; it is not a full-database backup.

## Tests

```powershell
npm ci
npm test
```

`npm test` runs Vitest once (Node environment, `fake-indexeddb` standing in for
IndexedDB). Coverage:

- `src/db/db.test.ts`: Dexie persistence (seeding, CRUD, indexed queries,
  data surviving close/reopen).
- `src/services/csv.test.ts`: CSV import (amount cleanup, auto-categorizing,
  quoting, CRLF, blank/malformed/missing-column rows) and export (escaping,
  Uncategorized fallback, export-then-import round trip).

Not covered: React pages/components, the browser file download in
`ImportExport.tsx`, `budget.ts`, and real-browser IndexedDB behavior. `npm run
build` type-checks the test files too.
