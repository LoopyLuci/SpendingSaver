"""Report and analytics tools for SpendingSaver MCP."""
import json
from mcp.server import Server
from mcp.types import Tool, TextContent
from spendingsaver_mcp.client import api_get, api_post

def register_report_tools(server: Server) -> None:
    @server.list_tools()
    async def list_report_tools() -> list[Tool]:
        return [
            Tool(
                name="ss_net_income_report",
                description="Get monthly income vs expenses vs savings rate.",
                inputSchema={
                    "type": "object",
                    "properties": {"months": {"type": "integer", "description": "Months to include", "default": 12}},
                },
            ),
            Tool(
                name="ss_spending_insights",
                description="Get spending trends, anomalies, and recommendations.",
                inputSchema={
                    "type": "object",
                    "properties": {"months": {"type": "integer", "default": 6}},
                },
            ),
            Tool(
                name="ss_spending_forecast",
                description="Project future spending based on history and recurring transactions.",
                inputSchema={
                    "type": "object",
                    "properties": {"months": {"type": "integer", "description": "Months to forecast", "default": 3}},
                },
            ),
            Tool(
                name="ss_debt_payoff_strategy",
                description="Calculate optimal debt payoff plan (snowball or avalanche).",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "strategy": {"type": "string", "description": "snowball or avalanche", "default": "snowball"},
                        "months": {"type": "integer", "default": 60},
                    },
                },
            ),
            Tool(
                name="ss_investment_performance",
                description="Get portfolio performance breakdown by type.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_credit_scores",
                description="List credit score history.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_summary_report",
                description="Get income/expense summary for a month.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "year": {"type": "integer"},
                        "month": {"type": "integer"},
                    },
                },
            ),
            Tool(
                name="ss_export_csv",
                description="Export all transactions as CSV.",
                inputSchema={"type": "object", "properties": {}},
            ),
            Tool(
                name="ss_send_report_email",
                description="Email a monthly report to a user.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "email": {"type": "string"},
                        "month": {"type": "integer"},
                        "year": {"type": "integer"},
                    },
                    "required": ["email"],
                },
            ),
        ]

    @server.call_tool()
    async def call_report_tool(name: str, arguments: dict) -> list[TextContent]:
        try:
            if name == "ss_net_income_report":
                data = api_get("/api/reports/net-income", {"months": arguments.get("months", 12)})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_spending_insights":
                data = api_get("/api/insights/spending", {"months": arguments.get("months", 6)})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_spending_forecast":
                data = api_get("/api/forecast/spending", {"months": arguments.get("months", 3)})
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_debt_payoff_strategy":
                data = api_get("/api/debts/strategy", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_investment_performance":
                data = api_get("/api/investments/performance")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_credit_scores":
                data = api_get("/api/credit-scores")
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_summary_report":
                data = api_get("/api/reports/summary", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            elif name == "ss_export_csv":
                data = api_get("/api/export/csv")
                return [TextContent(type="text", text=data.get("content", ""))]
            elif name == "ss_send_report_email":
                data = api_post("/api/reports/send-email", arguments)
                return [TextContent(type="text", text=json.dumps(data, indent=2))]
            return [TextContent(type="text", text=f"Unknown tool: {name}")]
        except Exception as e:
            return [TextContent(type="text", text=f"Error: {e}")]
