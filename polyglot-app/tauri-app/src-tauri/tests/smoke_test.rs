use std::time::Duration;

#[test]
fn smoke_api_client_base_uses_env_or_default() {
    let base =
        std::env::var("POLYGLOT_TEST_API_BASE").unwrap_or_else(|_| "http://127.0.0.1:8125".into());
    assert!(base.starts_with("http://"));
}

#[test]
fn smoke_payload_shape() {
    let payload = serde_json::json!({ "input": "hello" });
    assert_eq!(payload.get("input").and_then(|v| v.as_str()), Some("hello"));
}
