# Contributing

Thanks for helping. The projects in this repository are independent (Android, browser, native desktop, backend,
MCP); work in the one that owns the behaviour. Android and desktop must each stay fully usable on their own; the
only link is the optional sync protocol in [`spendingsaver-desktop/docs/sync.md`](spendingsaver-desktop/docs/sync.md).

1. Read the project's README and look at its tests first.
2. Keep changes small and update the docs for any change to behaviour, setup or data.
3. Preserve user data: Android Room changes need a migration, backend schema changes need an Alembic migration,
   and the desktop database only ever adds.
4. Run the checks before sending a change:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/check.ps1
   ```

   (or one project with `-Only backend|mcp|web|desktop|android|link`).
5. Never commit secrets, local configuration, build output or real financial data.

By contributing you agree your work is released under the [MIT license](LICENSE).
