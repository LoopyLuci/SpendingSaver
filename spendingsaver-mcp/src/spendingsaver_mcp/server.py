"""SpendingSaver MCP Server - exposes personal finance tools for Hermes Agent."""
import json
import os

import requests

from mcp.server.mcpserver import MCPServer
from mcp.types import TextContent, Tool

API_BASE = os.environ.get("SS_API_URL", "http://127.0.0.1:8125")

ALL_TOOLS = [
    # Accounts
    Tool(name="ss_list_accounts", description="List all accounts with balances.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_account", description="Create a new account.", inputSchema={"type": "object", "properties": {"name": {"type": "string"}, "type": {"type": "string", "default": "checking"}, "balance": {"type": "number", "default": 0}, "currency": {"type": "string", "default": "USD"}}, "required": ["name"]}),
    Tool(name="ss_update_account", description="Update an account.", inputSchema={"type": "object", "properties": {"account_id": {"type": "string"}, "name": {"type": "string"}, "balance": {"type": "number"}}, "required": ["account_id"]}),
    Tool(name="ss_delete_account", description="Delete an account.", inputSchema={"type": "object", "properties": {"account_id": {"type": "string"}}, "required": ["account_id"]}),
    Tool(name="ss_get_net_worth", description="Get total net worth.", inputSchema={"type": "object", "properties": {"base_currency": {"type": "string", "default": "USD"}}}),
    # Transactions
    Tool(name="ss_list_transactions", description="List recent transactions.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_transaction", description="Record a transaction.", inputSchema={"type": "object", "properties": {"account_id": {"type": "string"}, "amount": {"type": "number"}, "description": {"type": "string"}, "date": {"type": "string"}, "category_id": {"type": "string"}, "notes": {"type": "string"}}, "required": ["account_id", "amount", "description", "date"]}),
    Tool(name="ss_delete_transaction", description="Soft-delete a transaction.", inputSchema={"type": "object", "properties": {"transaction_id": {"type": "string"}}, "required": ["transaction_id"]}),
    Tool(name="ss_undo_transaction", description="Restore a soft-deleted transaction.", inputSchema={"type": "object", "properties": {"transaction_id": {"type": "string"}}, "required": ["transaction_id"]}),
    Tool(name="ss_tag_transaction", description="Add tags to a transaction.", inputSchema={"type": "object", "properties": {"transaction_id": {"type": "string"}, "tags": {"type": "array", "items": {"type": "string"}}}, "required": ["transaction_id", "tags"]}),
    Tool(name="ss_split_transaction", description="Split a transaction across categories.", inputSchema={"type": "object", "properties": {"transaction_id": {"type": "string"}, "splits": {"type": "array", "items": {"type": "object"}}}, "required": ["transaction_id", "splits"]}),
    # Budgets
    Tool(name="ss_list_budgets", description="List all budgets.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_budget", description="Create a budget.", inputSchema={"type": "object", "properties": {"category_id": {"type": "string"}, "amount": {"type": "number"}, "period": {"type": "string", "default": "monthly"}, "start_date": {"type": "string"}, "carry_over": {"type": "number", "default": 0}}, "required": ["category_id", "amount", "start_date"]}),
    Tool(name="ss_delete_budget", description="Delete a budget.", inputSchema={"type": "object", "properties": {"budget_id": {"type": "string"}}, "required": ["budget_id"]}),
    Tool(name="ss_budget_alerts", description="Get budget alerts.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_budget_comparison", description="Budget vs actual comparison.", inputSchema={"type": "object", "properties": {}}),
    # Reports
    Tool(name="ss_net_income_report", description="Monthly income vs expenses.", inputSchema={"type": "object", "properties": {"months": {"type": "integer", "default": 12}}}),
    Tool(name="ss_spending_insights", description="Spending trends and anomalies.", inputSchema={"type": "object", "properties": {"months": {"type": "integer", "default": 6}}}),
    Tool(name="ss_spending_forecast", description="Future spending projection.", inputSchema={"type": "object", "properties": {"months": {"type": "integer", "default": 3}}}),
    Tool(name="ss_debt_payoff_strategy", description="Snowball/avalanche calculator.", inputSchema={"type": "object", "properties": {"strategy": {"type": "string", "default": "snowball"}, "months": {"type": "integer", "default": 60}}}),
    Tool(name="ss_investment_performance", description="Portfolio performance.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_summary_report", description="Monthly summary.", inputSchema={"type": "object", "properties": {"year": {"type": "integer"}, "month": {"type": "integer"}}}),
    Tool(name="ss_export_csv", description="Export transactions as CSV.", inputSchema={"type": "object", "properties": {}}),
    # Search & Utilities
    Tool(name="ss_search", description="Search transactions, accounts, categories.", inputSchema={"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}),
    Tool(name="ss_list_categories", description="List all categories.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_category", description="Create a category.", inputSchema={"type": "object", "properties": {"name": {"type": "string"}, "color": {"type": "string"}}, "required": ["name"]}),
    Tool(name="ss_list_recurring", description="List recurring transactions.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_run_scheduler", description="Trigger recurring scheduler.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_list_savings_goals", description="List savings goals.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_savings_goal", description="Create a savings goal.", inputSchema={"type": "object", "properties": {"name": {"type": "string"}, "target_amount": {"type": "number"}, "current_amount": {"type": "number", "default": 0}}, "required": ["name", "target_amount"]}),
    Tool(name="ss_list_debts", description="List debts.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_debt", description="Add a debt.", inputSchema={"type": "object", "properties": {"name": {"type": "string"}, "balance": {"type": "number"}, "interest_rate": {"type": "number"}, "minimum_payment": {"type": "number"}}, "required": ["name", "balance"]}),
    Tool(name="ss_list_income_streams", description="List income streams.", inputSchema={"type": "object", "properties": {}}),
    Tool(name="ss_create_income_stream", description="Add an income stream.", inputSchema={"type": "object", "properties": {"name": {"type": "string"}, "amount": {"type": "number"}, "frequency": {"type": "string", "default": "monthly"}}, "required": ["name", "amount"]}),
    Tool(name="ss_create_backup", description="Create database backup.", inputSchema={"type": "object", "properties": {}}),
]


def _api(method, path, data=None):
    url = f"{API_BASE}{path}"
    try:
        r = requests.request(method, url, json=data, timeout=10)
        r.raise_for_status()
        return r.json()
    except Exception as e:
        return {"error": str(e)}


def _handle(name, args):
    try:
        if name == "ss_list_accounts": return _api("GET", "/api/accounts")
        elif name == "ss_create_account": return _api("POST", "/api/accounts", args)
        elif name == "ss_update_account":
            aid = args.pop("account_id")
            return _api("PUT", f"/api/accounts/{aid}", args)
        elif name == "ss_delete_account": return _api("DELETE", f"/api/accounts/{args['account_id']}")
        elif name == "ss_get_net_worth": return _api("GET", "/api/net-worth", {"base_currency": args.get("base_currency", "USD")})
        elif name == "ss_list_transactions": return _api("GET", "/api/transactions")
        elif name == "ss_create_transaction": return _api("POST", "/api/transactions", args)
        elif name == "ss_delete_transaction": return _api("DELETE", f"/api/transactions/{args['transaction_id']}")
        elif name == "ss_undo_transaction": return _api("POST", f"/api/transactions/{args['transaction_id']}/undo")
        elif name == "ss_tag_transaction": return _api("POST", f"/api/transactions/{args['transaction_id']}/tags", {"tags": args["tags"]})
        elif name == "ss_split_transaction": return _api("POST", f"/api/transactions/{args['transaction_id']}/split", {"splits": args["splits"]})
        elif name == "ss_list_budgets": return _api("GET", "/api/budgets")
        elif name == "ss_create_budget": return _api("POST", "/api/budgets", args)
        elif name == "ss_delete_budget": return _api("DELETE", f"/api/budgets/{args['budget_id']}")
        elif name == "ss_budget_alerts": return _api("GET", "/api/budgets/alerts")
        elif name == "ss_budget_comparison": return _api("GET", "/api/budgets/comparison")
        elif name == "ss_net_income_report": return _api("GET", "/api/reports/net-income", {"months": args.get("months", 12)})
        elif name == "ss_spending_insights": return _api("GET", "/api/insights/spending", {"months": args.get("months", 6)})
        elif name == "ss_spending_forecast": return _api("GET", "/api/forecast/spending", {"months": args.get("months", 3)})
        elif name == "ss_debt_payoff_strategy": return _api("GET", "/api/debts/strategy", args)
        elif name == "ss_investment_performance": return _api("GET", "/api/investments/performance")
        elif name == "ss_summary_report": return _api("GET", "/api/reports/summary", args)
        elif name == "ss_export_csv": return _api("GET", "/api/export/csv")
        elif name == "ss_search": return _api("GET", "/api/search", {"q": args["query"]})
        elif name == "ss_list_categories": return _api("GET", "/api/categories")
        elif name == "ss_create_category": return _api("POST", "/api/categories", args)
        elif name == "ss_list_recurring": return _api("GET", "/api/recurring-transactions")
        elif name == "ss_run_scheduler": return _api("POST", "/api/recurring-transactions/run-scheduler")
        elif name == "ss_list_savings_goals": return _api("GET", "/api/savings-goals")
        elif name == "ss_create_savings_goal": return _api("POST", "/api/savings-goals", args)
        elif name == "ss_list_debts": return _api("GET", "/api/debts")
        elif name == "ss_create_debt": return _api("POST", "/api/debts", args)
        elif name == "ss_list_income_streams": return _api("GET", "/api/income-streams")
        elif name == "ss_create_income_stream": return _api("POST", "/api/income-streams", args)
        elif name == "ss_create_backup": return _api("GET", "/api/backup")
        return {"error": f"Unknown tool: {name}"}
    except Exception as e:
        return {"error": str(e)}


def create_server() -> MCPServer:
    server = MCPServer("spendingsaver", version="0.1.0")

    for tool in ALL_TOOLS:
        name = tool.name

        def make_handler(tool_name=name):
            async def handler(arguments=None):
                result = _handle(tool_name, arguments or {})
                return [TextContent(type="text", text=json.dumps(result, indent=2, default=str))]
            return handler

        handler = make_handler()
        server.add_tool(handler, name=name, description=tool.description)

    return server


def main() -> None:
    import asyncio
    server = create_server()
    asyncio.run(server.run_stdio_async())


if __name__ == "__main__":
    main()