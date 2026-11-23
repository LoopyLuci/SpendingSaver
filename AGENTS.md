# Repository contribution guidance

## Project boundaries

- This repository contains separate Android, browser, desktop/backend, and MCP
  projects. Work in the project that owns the requested behavior; do not assume
  they share storage or automatically synchronize.
- Android is local-first and must stay fully usable without the desktop
  backend, and vice versa. Its JSON backup is user-initiated and is not sync.
  The only cross-app path is the opt-in sync protocol in
  `polyglot-app/docs/sync.md`; keep it optional and never make a feature depend
  on it.
- The MCP adapter calls the `polyglot-app/backend` HTTP API. MCP tools can
  change financial records; treat tool inputs and backend access accordingly.

## Before changing code

1. Read the relevant project README and inspect the implementation and tests.
2. Check `git status` and preserve unrelated user changes.
3. Keep changes scoped; update the documentation for any changed behavior,
   setup, configuration, or data migration.
4. Never commit credentials, local configuration, private account data,
   generated build output, or local logs.

## Validation

- Run the narrowest relevant checks documented by the project. The root
  README links to each project guide; desktop commands are also collected in
  `polyglot-app/docs/runbook.md`.
- Report checks that were not run and distinguish warnings from failures.
- Do not claim a test suite exists when a project has no tests.

## Data and migrations

- Preserve existing user data. Android Room schema changes require a migration;
  do not replace user databases with destructive migrations.
- Backend schema changes belong in Alembic migrations; document safe upgrade
  and backup implications.
- Document which store a feature reads or writes. Never describe a local
  export/import as automatic sync.

## Documentation

- Keep the root README focused on repository orientation and link to
  project-specific instructions rather than duplicating them.
- Keep commands copyable from their stated working directory and prefer
  checked-in package manifests/lockfiles and wrappers.
- Keep `PLAN.md`, `TODO.md`, and `HANDOVER.md` factual and distinguish current
  behavior from proposed work.
