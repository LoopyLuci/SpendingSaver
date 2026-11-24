# Repository plan

This is a maintenance plan for the repository as it exists, not a feature
promise or a claim that the separate apps share data.

Product requirements and unresolved decisions are recorded in the
[SpendingSaver specification](spec/SPEC.md).

## Project map

- **Android:** standalone local-first app; see
  [`SpendingSaverAndroid/README.md`](SpendingSaverAndroid/README.md).
- **Browser:** standalone React app using browser storage; see
  [`spendingsaver-web/README.md`](spendingsaver-web/README.md).
- **Desktop/backend:** Tauri UI with Python and Go services; see
  [`polyglot-app/README.md`](polyglot-app/README.md) and its
  [runbook](polyglot-app/docs/runbook.md).
- **MCP:** adapter to the desktop backend API; see
  [`spendingsaver-mcp/README.md`](spendingsaver-mcp/README.md).

## Work sequence

1. **Keep the project documentation accurate.** Update a project's README and
   operator instructions when its build, data flow, or user-visible behavior
   changes. Avoid copying one app's behavior onto another.
2. **Establish project-level verification.** Run each target's existing build
   and tests independently; add focused tests when changing untested behavior.
   Record missing test coverage rather than treating an empty test task as
   coverage.
3. **Resolve confirmed integration and setup gaps.** Verify the desktop
   development startup path, MCP API authentication behavior, and packaging
   prerequisites before relying on them. See the actionable entries in
   [`TODO.md`](TODO.md).
4. **Keep sync optional.** Android and the desktop backend must each remain fully
   usable alone; their opt-in sync is specified in
   [`polyglot-app/docs/sync.md`](polyglot-app/docs/sync.md). Extending sync to the
   browser app or MCP adapter, adding shared accounts, or a common release
   process still needs an explicit product decision.

## Completion criteria

- Project boundaries and data flow are documented accurately.
- Documented commands match checked-in scripts/manifests and identify their
  working directory and prerequisites.
- Relevant tests/builds pass, or limitations are clearly recorded.
- Database and backup changes preserve user data and have appropriate
  migration/restore coverage.
