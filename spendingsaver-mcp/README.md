# SpendingSaver MCP Server

This package exposes SpendingSaver finance operations as MCP tools. The MCP
server communicates over stdio and calls the HTTP API of the separate
`polyglot-app/backend` service. It is not a database and does not connect to
the Android or standalone browser app.

The tool set includes read and write operations for accounts, transactions,
budgets, categories, reports, recurring transactions, goals, debts, and
backups. Treat it as a privileged integration: tool calls can change financial
records.

## Requirements

- Python 3.11 or newer
- A running, trusted SpendingSaver backend (default API URL:
  `http://127.0.0.1:8125`)

## Install

From this directory:

```powershell
python -m pip install -e .
```

The package installs the `spendingsaver-mcp` command. Configure your MCP host
to launch that command and set `SS_API_URL` if the backend is not listening at
the default address. Keep the MCP server and backend on a trusted machine or
network; do not expose the development backend publicly.

## Configuration and authentication

- `SS_API_URL` selects the backend base URL; its default is
  `http://127.0.0.1:8125`.
- The active stdio server implementation is in `src/spendingsaver_mcp/server.py`
  and makes direct HTTP requests. It does not currently attach the bearer token
  configured by `SS_MCP_TOKEN` in `client.py`. Do not assume MCP calls are
  authenticated by that variable; see the root [`TODO.md`](../TODO.md).
- Never place credentials or private financial data in committed config files.

## Validation

There is no checked-in MCP test suite or test command yet. Add tests for the
tool/API mapping and error cases before changing these operations.
