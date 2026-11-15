# SpendingSaver

SpendingSaver is a collection of personal-finance applications and integrations
in one repository. The applications are separate projects, not one shared
runtime: in particular, the Android app stores data locally and does not sync
with the desktop backend.

## Projects

| Project | Location | Data and runtime |
| --- | --- | --- |
| Android app | [`SpendingSaverAndroid/`](SpendingSaverAndroid/README.md) | Kotlin, Jetpack Compose, Room. Local-first; user-initiated JSON backup and restore. |
| Browser app | [`spendingsaver-web/`](spendingsaver-web/README.md) | React, TypeScript, Vite, Dexie/IndexedDB. Runs in the browser. |
| Desktop app and backend | [`polyglot-app/`](polyglot-app/README.md) | Tauri desktop shell and React UI, Python FastAPI backend, and Go service. |
| MCP integration | [`spendingsaver-mcp/`](spendingsaver-mcp/README.md) | Model Context Protocol tools that call the desktop backend's HTTP API. |

Screenshots are in [`SpendingSaverScreenshots/`](SpendingSaverScreenshots/).
The product requirements and implementation status are in the
[SpendingSaver specification](spec/SPEC.md).
The Android feature roadmap is in
[`SpendingSaverAndroid/.hermes/plans/2026-10-22_100200-spendingsaver-roadmap.md`](SpendingSaverAndroid/.hermes/plans/2026-10-22_100200-spendingsaver-roadmap.md).

## Quick start

Each project has its own dependencies and setup. For example, from the
repository root:

```powershell
# Browser app
Set-Location spendingsaver-web
npm ci
npm run dev
```

```powershell
# Android debug build (JDK 17 and Android SDK platform 34 required)
Set-Location SpendingSaverAndroid
.\gradlew.bat :app:assembleDebug
```

For desktop development, backend/API operations, tests, and packaging, see the
[`polyglot-app` README](polyglot-app/README.md) and
[operator runbook](polyglot-app/docs/runbook.md).

## Repository guidance

- Read [`AGENTS.md`](AGENTS.md) before making changes.
- Current cross-project work and known follow-ups are tracked in
  [`PLAN.md`](PLAN.md) and [`TODO.md`](TODO.md).
- [`HANDOVER.md`](HANDOVER.md) summarizes current project boundaries and
  verification context for the next contributor.
- Treat project-specific build files and READMEs as authoritative for commands
  and dependencies. There is no root-level build that builds every project.

## Privacy and data boundaries

The browser app, Android app, and desktop/backend stack are separate data
stores. Do not assume that data entered into one app is available in another.
The MCP server can call mutating backend operations; only point it at a backend
you trust. Do not expose a development backend to an untrusted network.
