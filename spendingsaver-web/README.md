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

## Current checks

The package defines `dev`, `build`, and `preview` scripts. It does not
currently define a test script; see the root [`TODO.md`](../TODO.md).
