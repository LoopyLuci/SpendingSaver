"""Transaction management tools for SpendingSaver MCP."""
import json
from mcp.server import Server
from mcp.types import Tool, TextContent
from spendingsaver_mcp.client import api_get, api_post, api_put, api_delete

def register_transaction_tools(server: Server) -> None:
    @server.list_tools()
    async def list_transaction_tools() -> list[Tool]:
        return [
            Tool(
                name="ss_list_transactions",
                description="List recent transactions.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_transaction",
                description="Record a new transaction.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "account_id": {"type": "string", "description": "Account ID"},
                        "amount": {"type": "number", "description": "Amount (negative for expense)"},
                        "description": {"type": "string", "description": "Transaction description"},
                        "date": {"type": "string", "description": "Date (YYYY-MM-DD)"},
                        "category_id": {"type": "string", "description": "Category ID (optional)"},
                        "notes": {"type": "string", "description": "Notes (optional)"},
                    },
                    "required": ["account_id", "amount", "description", "date"],
                },
            ),
            Tool(
                name="ss_delete_transaction",
                description="Soft-delete a transaction (recoverable).",
                inputSchema={
                    "type": "object",
                    "properties": {"transaction_id": {"type": "string"}},
                    "required": ["transaction_id"],
                },
            ),
            Tool(
                name="ss_undo_transaction",
                description="Restore a soft-deleted transaction.",
                inputSchema={
                    "type": "object",
                    "properties": {"transaction_id": {"type": "string"}},
                    "required": ["transaction_id"],
                },
            ),
            Tool(
                name="ss_tag_transaction",
                description="Add tags to a transaction.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "transaction_id": {"type": "string"},
                        "tags": {"type": "array", "items": {"type": "string"}, "description": "Tags to add"},
                    },
                    "required": ["transaction_id", "tags"],
                },
            ),
            Tool(
                name="ss_split_transaction",
                description="Split a transaction across categories.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "transaction_id": {"type": "string"},
                        "splits": {"type": "array", "items": {"type": "object", "properties": {"amount": {"type": "number"}, "category_id": {"type": "string"}}, "required": ["amount"]}},
                    },
                    "required": ["transaction_id", "splits"],
                },
            ),
            Tool(
                name="ss_get_transactions_by_tag",
                description="Find all transactions with a specific tag.",
                inputSchema={
                    "type": "object",
                    "properties": {"tag": {"type": "string", "description": "Tag to search for"}},
                    "required": ["tag"],
                },
            ),
        ]

    @server.call_tool()
    async def call_transaction_tool(name: str, arguments: dict) -> list[TextContent]:
        try:
            if name == "ss_list_transactions":
                data = api_get("/api/transactions")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_transaction":
                data = api_post("/api/transactions", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_delete_transaction":
                data = api_delete(f"/api/transactions/{arguments['transaction_id']}")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_undo_transaction":
                data = api_post(f"/api/transactions/{arguments['transaction_id']}/undo")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_tag_transaction":
                data = api_post(f"/api/transactions/{arguments['transaction_id']}/tags", {"tags": arguments["tags"]})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_split_transaction":
                data = api_post(f"/api/transactions/{arguments['transaction_id']}/split", {"splits": arguments["splits"]})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_get_transactions_by_tag":
                data = api_get(f"/api/transactions/tags/{arguments['tag']}")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            return [TextContent(type="text", text=f"Unknown tool: {name}")]
        except Exception as e:
            return [TextContent(type="text", text=f"Error: {e}")]
