"""Account management tools for SpendingSaver MCP."""
import json
from mcp.server import Server
from mcp.types import Tool, TextContent
from spendingsaver_mcp.client import api_get, api_post, api_put, api_delete

def register_account_tools(server: Server) -> None:
    @server.list_tools()
    async def list_account_tools() -> list[Tool]:
        return [
            Tool(
                name="ss_list_accounts",
                description="List all accounts with balances.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_account",
                description="Create a new account.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "name": {"type": "string", "description": "Account name"},
                        "type": {"type": "string", "description": "Account type (checking/savings/credit/investment)", "default": "checking"},
                        "balance": {"type": "number", "description": "Initial balance", "default": 0},
                        "currency": {"type": "string", "description": "Currency code", "default": "USD"},
                    },
                    "required": ["name"],
                },
            ),
            Tool(
                name="ss_update_account",
                description="Update an account's details.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "account_id": {"type": "string", "description": "Account ID"},
                        "name": {"type": "string", "description": "New name"},
                        "balance": {"type": "number", "description": "New balance"},
                    },
                    "required": ["account_id"],
                },
            ),
            Tool(
                name="ss_delete_account",
                description="Delete an account (must have no transactions).",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "account_id": {"type": "string", "description": "Account ID to delete"},
                    },
                    "required": ["account_id"],
                },
            ),
            Tool(
                name="ss_get_net_worth",
                description="Get total net worth across all accounts.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "base_currency": {"type": "string", "description": "Currency for total", "default": "USD"},
                    },
                },
            ),
        ]

    @server.call_tool()
    async def call_account_tool(name: str, arguments: dict) -> list[TextContent]:
        try:
            if name == "ss_list_accounts":
                data = api_get("/api/accounts")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_account":
                data = api_post("/api/accounts", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_update_account":
                aid = arguments.pop("account_id")
                data = api_put(f"/api/accounts/{aid}", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_delete_account":
                data = api_delete(f"/api/accounts/{arguments['account_id']}")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_get_net_worth":
                data = api_get("/api/net-worth", {"base_currency": arguments.get("base_currency", "USD")})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            return [TextContent(type="text", text=f"Unknown tool: {name}")]
        except Exception as e:
            return [TextContent(type="text", text=f"Error: {e}")]
