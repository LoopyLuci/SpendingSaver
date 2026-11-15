# Handover

## Repository state

SpendingSaver is a multi-project repository. Android, browser, desktop/backend,
and MCP are separate deliverables and have different data stores. No cross-app
sync is implemented by the Android local-backup workflow.

The previous implementation milestone is commit `23f31dd` on `main`, which
moved Android to local-only data and added user-initiated JSON backup/restore.
The Android README and roadmap describe that behavior. Use `git status` and
`git log` for the current state; this file is a handoff note, not a substitute
for repository history.

## Useful starting points

1. [`README.md`](README.md) — project index and data boundaries.
2. [`AGENTS.md`](AGENTS.md) — contribution and data-safety guidance.
3. [`PLAN.md`](PLAN.md) and [`TODO.md`](TODO.md) — maintenance sequence and
   confirmed follow-ups.
4. The README and runbook for the project being changed.

## Verification context

At the Android local-first milestone:

- The Gradle 8.2 wrapper with JDK 17 ran `:app:assembleDebug` successfully.
- `:app:testDebugUnitTest` completed successfully but reported `NO-SOURCE`;
  there are no Android unit-test sources in the repository.
- The build emitted SDK tooling and AGP/compileSdk compatibility warnings and
  pre-existing Kotlin warnings.

These are historical results, not a claim that current code has been tested.
The desktop/backend, browser, and MCP projects were not verified as part of
that Android change.

## Important caveats

- Android Room database version 3 removes the former sync outbox and
  sync-metadata columns. Keep migrations data-preserving.
- Android JSON restore replaces active accounts, categories, transactions,
  and budgets. It does not include deleted rows or app settings.
- The MCP adapter can invoke write operations. Its active server and helper
  client currently have different HTTP/auth handling; review `TODO.md` before
  deploying it against a non-local backend.
- Desktop setup and release packaging depend on tools documented in
  `polyglot-app/docs/runbook.md`; backend, Go, and frontend services are not a
  single automatically managed installation in every workflow.

## Suggested next step

Select one follow-up from `TODO.md`, confirm it still applies, then inspect the
owning project's code and tests. Do not start cross-app synchronization or
combine data stores without an explicit product decision.
