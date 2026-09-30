"""Budget management tools for SpendingSaver MCP."""
import json
from mcp.server import Server
from mcp.types import Tool, TextContent
from spendingsaver_mcp.client import api_get, api_post, api_delete

def register_budget_tools(server: Server) -> None:
    @server.list_tools()
    async def list_budget_tools() -> list[Tool]:
        return [
            Tool(
                name="ss_list_budgets",
                description="List all budgets with carry-over amounts.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_budget",
                description="Create a new budget for a category.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "category_id": {"type": "string", "description": "Category ID"},
                        "amount": {"type": "number", "description": "Budget amount"},
                        "period": {"type": "string", "description": "monthly/yearly", "default": "monthly"},
                        "start_date": {"type": "string", "description": "Start date (YYYY-MM-DD)"},
                        "carry_over": {"type": "number", "description": "Carry-over from previous period", "default": 0},
                    },
                    "required": ["category_id", "amount", "start_date"],
                },
            ),
            Tool(
                name="ss_delete_budget",
                description="Delete a budget.",
                inputSchema={
                    "type": "object",
                    "properties": {"budget_id": {"type": "string"}},
                    "required": ["budget_id"],
                },
            ),
            Tool(
                name="ss_budget_alerts",
                description="Get budget alerts (over/warning status).",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_budget_comparison",
                description="Compare budgeted vs actual spending for current month.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_spending_limits",
                description="List spending limits per category with usage status.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_spending_limit",
                description="Set a spending limit for a category.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "category_id": {"type": "string"},
                        "limit_amount": {"type": "number"},
                        "period": {"type": "string", "default": "monthly"},
                        "alert_threshold": {"type": "number", "description": "Alert at this percentage (0-1)", "default": 0.8},
                    },
                    "required": ["category_id", "limit_amount"],
                },
            ),
        ]

    @server.call_tool()
    async def call_budget_tool(name: str, arguments: dict) -> list[TextContent]:
        try:
            if name == "ss_list_budgets":
                data = api_get("/api/budgets")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_budget":
                data = api_post("/api/budgets", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_delete_budget":
                data = api_delete(f"/api/budgets/{arguments['budget_id']}")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_budget_alerts":
                data = api_get("/api/budgets/alerts")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_budget_comparison":
                data = api_get("/api/budgets/comparison")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_spending_limits":
                data = api_get("/api/spending-limits")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_spending_limit":
                data = api_post("/api/spending-limits", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            return [TextContent(type="text", text=f"Unknown tool: {name}")]
        except Exception as e:
            return [TextContent(type="text", text=f"Error: {e}")]
