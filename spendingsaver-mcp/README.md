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

Both settings are read from the environment of the MCP server process each time
a tool is called:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SS_API_URL` | `http://127.0.0.1:8125` | Backend base URL. Every tool call, including writes, goes to this URL. |
| `SS_MCP_TOKEN` | unset | Optional bearer token. When set, it is sent as `Authorization: Bearer <token>` on every request. When unset or blank, no auth header is sent. |

All requests go through `src/spendingsaver_mcp/client.py` (`request`), which
`server.py` uses, so the URL and token behave identically for every tool. GET
tool arguments are sent as query parameters; write arguments as a JSON body.

Safety notes:

- The token is never logged or included in tool error text. A 401/403 from the
  backend is reported as "not authorized; check SS_MCP_TOKEN".
- Set the token in your MCP host's environment configuration, not in committed
  files. Do not put credentials in `SS_API_URL`.
- The token is only useful if the backend checks it. The backend's legacy
  routes (accounts, transactions, budgets, ...) that these tools call are
  currently mostly unauthenticated (see the root [`TODO.md`](../TODO.md)), so
  setting `SS_MCP_TOKEN` does not by itself protect your data. Only point
  `SS_API_URL` at a backend you trust, on a trusted machine or network.
- The MCP adapter does not use the backend's opt-in sync hub
  (`polyglot-app/docs/sync.md`).
- `src/spendingsaver_mcp/tools/` contains older, unused per-tool modules; the
  active tool list and routing live in `server.py`.

## Validation

Tests mock the backend HTTP layer (no backend needed). From this directory:

```powershell
python -m pip install -e ".[test]"
python -m pytest
```

They cover the tool-to-API mapping for every tool, argument handling, backend
error reporting, and bearer-token/base-URL behavior.
