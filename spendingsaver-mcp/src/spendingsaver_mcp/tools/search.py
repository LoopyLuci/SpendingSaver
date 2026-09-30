"""Search and utility tools for SpendingSaver MCP."""
import json
from mcp.server import Server
from mcp.types import Tool, TextContent
from spendingsaver_mcp.client import api_get, api_post

def register_search_tools(server: Server) -> None:
    @server.list_tools()
    async def list_search_tools() -> list[Tool]:
        return [
            Tool(
                name="ss_search",
                description="Search across transactions, accounts, and categories.",
                inputSchema={
                    "type": "object",
                    "properties": {"query": {"type": "string", "description": "Search query"}},
                    "required": ["query"],
                },
            ),
            Tool(
                name="ss_category_suggestions",
                description="Get category suggestions for a transaction description.",
                inputSchema={
                    "type": "object",
                    "properties": {"description": {"type": "string"}},
                    "required": ["description"],
                },
            ),
            Tool(
                name="ss_list_categories",
                description="List all categories.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_category",
                description="Create a new category.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "color": {"type": "string", "description": "Hex color (optional)"},
                    },
                    "required": ["name"],
                },
            ),
            Tool(
                name="ss_list_recurring",
                description="List recurring transactions.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_run_scheduler",
                description="Manually trigger the recurring transaction scheduler.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_list_savings_goals",
                description="List savings goals with progress.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_savings_goal",
                description="Create a savings goal.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "target_amount": {"type": "number"},
                        "current_amount": {"type": "number", "default": 0},
                        "deadline": {"type": "string", "description": "Optional deadline (YYYY-MM-DD)"},
                    },
                    "required": ["name", "target_amount"],
                },
            ),
            Tool(
                name="ss_list_debts",
                description="List all debts.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_debt",
                description="Add a debt for payoff tracking.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "creditor": {"type": "string"},
                        "balance": {"type": "number"},
                        "interest_rate": {"type": "number", "description": "APR percentage"},
                        "minimum_payment": {"type": "number"},
                        "debt_type": {"type": "string", "default": "credit_card"},
                    },
                    "required": ["name", "balance"],
                },
            ),
            Tool(
                name="ss_list_income_streams",
                description="List recurring income streams.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_create_income_stream",
                description="Add a recurring income stream.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "amount": {"type": "number"},
                        "frequency": {"type": "string", "default": "monthly"},
                    },
                    "required": ["name", "amount"],
                },
            ),
            Tool(
                name="ss_create_backup",
                description="Create a database backup.",
                inputSchema={"type": "object", "properties": {}},
            ),
        ]

    @server.call_tool()
    async def call_search_tool(name: str, arguments: dict) -> list[TextContent]:
        try:
            if name == "ss_search":
                data = api_get("/api/search", {"q": arguments["query"]})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_category_suggestions":
                data = api_get("/api/transactions/category-suggestions", {"description": arguments["description"]})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_list_categories":
                data = api_get("/api/categories")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_category":
                data = api_post("/api/categories", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_list_recurring":
                data = api_get("/api/recurring-transactions")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_run_scheduler":
                data = api_post("/api/recurring-transactions/run-scheduler")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_list_savings_goals":
                data = api_get("/api/savings-goals")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_savings_goal":
                data = api_post("/api/savings-goals", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_list_debts":
                data = api_get("/api/debts")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_debt":
                data = api_post("/api/debts", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_list_income_streams":
                data = api_get("/api/income-streams")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_income_stream":
                data = api_post("/api/income-streams", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_create_backup":
                data = api_get("/api/backup")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            return [TextContent(type="text", text=f"Unknown tool: {name}")]
        except Exception as e:
            return [TextContent(type="text", text=f"Error: {e}")]
