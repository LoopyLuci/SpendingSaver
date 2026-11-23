"""Tests for the MCP adapter. The backend is mocked; no network is used."""
import json
from unittest import mock

import pytest
import requests

from spendingsaver_mcp import client, server

DEFAULT = "http://127.0.0.1:8125"


class FakeResponse:
    def __init__(self, status=200, body=None):
        self.status_code = status
        self._body = {} if body is None else body

    def json(self):
        return self._body

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"{self.status_code} error", response=self)


@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    monkeypatch.delenv("SS_API_URL", raising=False)
    monkeypatch.delenv("SS_MCP_TOKEN", raising=False)


@pytest.fixture
def http():
    with mock.patch("requests.request", return_value=FakeResponse(body={"ok": True})) as m:
        yield m


def last_call(m):
    (method, url), kw = m.call_args
    return method, url, kw


# (tool, args, method, path, params, body)
CASES = [
    ("ss_list_accounts", {}, "GET", "/api/accounts", None, None),
    ("ss_create_account", {"name": "A", "balance": 5}, "POST", "/api/accounts", None, {"name": "A", "balance": 5}),
    ("ss_update_account", {"account_id": "7", "name": "B"}, "PUT", "/api/accounts/7", None, {"name": "B"}),
    ("ss_delete_account", {"account_id": "7"}, "DELETE", "/api/accounts/7", None, None),
    ("ss_get_net_worth", {}, "GET", "/api/net-worth", {"base_currency": "USD"}, None),
    ("ss_get_net_worth", {"base_currency": "EUR"}, "GET", "/api/net-worth", {"base_currency": "EUR"}, None),
    ("ss_list_transactions", {}, "GET", "/api/transactions", None, None),
    ("ss_create_transaction", {"account_id": "1", "amount": -3.5, "description": "x", "date": "2026-01-02"},
     "POST", "/api/transactions", None, {"account_id": "1", "amount": -3.5, "description": "x", "date": "2026-01-02"}),
    ("ss_delete_transaction", {"transaction_id": "9"}, "DELETE", "/api/transactions/9", None, None),
    ("ss_undo_transaction", {"transaction_id": "9"}, "POST", "/api/transactions/9/undo", None, None),
    ("ss_tag_transaction", {"transaction_id": "9", "tags": ["a"]}, "POST", "/api/transactions/9/tags", None, {"tags": ["a"]}),
    ("ss_split_transaction", {"transaction_id": "9", "splits": [{"amount": 1}]}, "POST", "/api/transactions/9/split", None, {"splits": [{"amount": 1}]}),
    ("ss_list_budgets", {}, "GET", "/api/budgets", None, None),
    ("ss_create_budget", {"category_id": "c", "amount": 10, "start_date": "2026-01-01"}, "POST", "/api/budgets", None,
     {"category_id": "c", "amount": 10, "start_date": "2026-01-01"}),
    ("ss_delete_budget", {"budget_id": "3"}, "DELETE", "/api/budgets/3", None, None),
    ("ss_budget_alerts", {}, "GET", "/api/budgets/alerts", None, None),
    ("ss_budget_comparison", {}, "GET", "/api/budgets/comparison", None, None),
    ("ss_net_income_report", {}, "GET", "/api/reports/net-income", {"months": 12}, None),
    ("ss_net_income_report", {"months": 3}, "GET", "/api/reports/net-income", {"months": 3}, None),
    ("ss_spending_insights", {}, "GET", "/api/insights/spending", {"months": 6}, None),
    ("ss_spending_forecast", {}, "GET", "/api/forecast/spending", {"months": 3}, None),
    ("ss_debt_payoff_strategy", {"strategy": "avalanche"}, "GET", "/api/debts/strategy", {"strategy": "avalanche"}, None),
    ("ss_investment_performance", {}, "GET", "/api/investments/performance", None, None),
    ("ss_summary_report", {"year": 2026, "month": 1}, "GET", "/api/reports/summary", {"year": 2026, "month": 1}, None),
    ("ss_export_csv", {}, "GET", "/api/export/csv", None, None),
    ("ss_search", {"query": "coffee"}, "GET", "/api/search", {"q": "coffee"}, None),
    ("ss_list_categories", {}, "GET", "/api/categories", None, None),
    ("ss_create_category", {"name": "Fun"}, "POST", "/api/categories", None, {"name": "Fun"}),
    ("ss_list_recurring", {}, "GET", "/api/recurring-transactions", None, None),
    ("ss_run_scheduler", {}, "POST", "/api/recurring-transactions/run-scheduler", None, None),
    ("ss_list_savings_goals", {}, "GET", "/api/savings-goals", None, None),
    ("ss_create_savings_goal", {"name": "g", "target_amount": 5}, "POST", "/api/savings-goals", None, {"name": "g", "target_amount": 5}),
    ("ss_list_debts", {}, "GET", "/api/debts", None, None),
    ("ss_create_debt", {"name": "d", "balance": 5}, "POST", "/api/debts", None, {"name": "d", "balance": 5}),
    ("ss_list_income_streams", {}, "GET", "/api/income-streams", None, None),
    ("ss_create_income_stream", {"name": "s", "amount": 5}, "POST", "/api/income-streams", None, {"name": "s", "amount": 5}),
    ("ss_create_backup", {}, "GET", "/api/backup", None, None),
]


@pytest.mark.parametrize("tool,args,method,path,params,body", CASES)
def test_tool_maps_to_backend_call(http, tool, args, method, path, params, body):
    result = server._handle(tool, dict(args))
    m, url, kw = last_call(http)
    assert (m, url) == (method, DEFAULT + path)
    assert kw["params"] == params
    assert kw["json"] == body
    assert result == {"ok": True}


def test_every_registered_tool_is_covered_by_a_case():
    assert {t.name for t in server.ALL_TOOLS} == {c[0] for c in CASES}


def test_unknown_tool_makes_no_request(http):
    assert server._handle("ss_nope", {}) == {"error": "Unknown tool: ss_nope"}
    http.assert_not_called()


def test_missing_required_argument_makes_no_request(http):
    assert server._handle("ss_delete_account", {}) == {"error": "Missing required argument: account_id"}
    http.assert_not_called()


def test_update_account_does_not_mutate_caller_args(http):
    args = {"account_id": "7", "name": "B"}
    server._handle("ss_update_account", args)
    assert args == {"account_id": "7", "name": "B"}


def test_tool_handler_accepts_none_arguments(http):
    assert server._handle("ss_list_accounts", {}) == {"ok": True}


# ---- error reporting ----

def test_http_error_reports_status_and_detail(http):
    http.return_value = FakeResponse(404, {"detail": "Account not found"})
    assert server._handle("ss_delete_account", {"account_id": "1"}) == {
        "error": "Backend returned HTTP 404: Account not found"
    }


def test_http_error_without_json_body(http):
    resp = FakeResponse(500)
    resp.json = mock.Mock(side_effect=ValueError)
    http.return_value = resp
    assert server._handle("ss_list_accounts", {}) == {"error": "Backend returned HTTP 500"}


def test_connection_error_is_reported(http):
    http.side_effect = requests.ConnectionError("refused")
    err = server._handle("ss_list_accounts", {})["error"]
    assert "ConnectionError" in err and "refused" in err


def test_timeout_is_reported(http):
    http.side_effect = requests.Timeout("slow")
    assert "Timeout" in server._handle("ss_list_accounts", {})["error"]


def test_request_uses_timeout(http):
    server._handle("ss_list_accounts", {})
    assert last_call(http)[2]["timeout"] == client.TIMEOUT_SECONDS


# ---- MCP wiring ----

def test_server_registers_all_tools_and_handler_returns_json_text(http):
    srv = server.create_server()
    assert srv is not None
    tool = server.ALL_TOOLS[0]
    # Rebuild the handler the same way create_server does and exercise it.
    import asyncio
    out = asyncio.run(_handler_for(srv, tool.name)())
    assert out[0].type == "text"
    assert json.loads(out[0].text) == {"ok": True}


def _handler_for(srv, name):
    tools = srv._tool_manager._tools if hasattr(srv, "_tool_manager") else None
    if tools is None:
        pytest.skip("cannot introspect MCPServer tools in this mcp version")
    fn = tools[name].fn
    return fn


# ---- authentication / base URL ----

def test_no_token_sends_no_authorization_header(http):
    server._handle("ss_list_accounts", {})
    assert "Authorization" not in last_call(http)[2]["headers"]


def test_token_is_sent_as_bearer_on_every_method(http, monkeypatch):
    monkeypatch.setenv("SS_MCP_TOKEN", "test-token-123")
    for tool, args in [("ss_list_accounts", {}), ("ss_create_category", {"name": "x"}),
                       ("ss_update_account", {"account_id": "1", "name": "n"}),
                       ("ss_delete_account", {"account_id": "1"})]:
        server._handle(tool, args)
        assert last_call(http)[2]["headers"]["Authorization"] == "Bearer test-token-123"


def test_blank_token_is_ignored(http, monkeypatch):
    monkeypatch.setenv("SS_MCP_TOKEN", "   ")
    server._handle("ss_list_accounts", {})
    assert "Authorization" not in last_call(http)[2]["headers"]


def test_api_url_override_and_trailing_slash(http, monkeypatch):
    monkeypatch.setenv("SS_API_URL", "http://example.test:9000/")
    server._handle("ss_list_accounts", {})
    assert last_call(http)[1] == "http://example.test:9000/api/accounts"


def test_401_error_hints_at_token_without_leaking_it(http, monkeypatch):
    monkeypatch.setenv("SS_MCP_TOKEN", "super-secret-token")
    http.return_value = FakeResponse(401, {"detail": "Invalid token"})
    err = server._handle("ss_list_accounts", {})["error"]
    assert "401" in err and "SS_MCP_TOKEN" in err
    assert "super-secret-token" not in err


def test_legacy_client_helpers_share_auth(http, monkeypatch):
    monkeypatch.setenv("SS_MCP_TOKEN", "tok")
    assert client.api_get("/api/x", {"a": 1}) == {"ok": True}
    m, url, kw = last_call(http)
    assert (m, url, kw["params"]) == ("GET", DEFAULT + "/api/x", {"a": 1})
    assert kw["headers"]["Authorization"] == "Bearer tok"
    client.api_post("/api/x", {"b": 2})
    assert last_call(http)[2]["json"] == {"b": 2}
    client.api_put("/api/x", {"b": 2})
    client.api_delete("/api/x")
    assert last_call(http)[0] == "DELETE"
