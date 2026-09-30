use reqwest::Client;
use serde_json::Value;
use std::time::Duration;
use tauri::State;

#[derive(Clone)]
struct ApiClient {
    base: String,
    client: Client,
}

impl ApiClient {
    fn new(base: String) -> Self {
        let client = Client::builder()
            .timeout(Duration::from_millis(3000))
            .build()
            .expect("failed to build HTTP client");
        Self { base, client }
    }

    async fn get_json(&self, path: &str) -> Result<Value, String> {
        let url = format!("{}{}", self.base, path);
        self.client.get(&url).send().await.map_err(|e| e.to_string())?
            .json().await.map_err(|e| e.to_string())
    }

    async fn post_json(&self, path: &str, body: Value) -> Result<Value, String> {
        let url = format!("{}{}", self.base, path);
        self.client.post(&url).json(&body).send().await.map_err(|e| e.to_string())?
            .json().await.map_err(|e| e.to_string())
    }
}

#[tauri::command]
async fn health_check(client: State<'_, ApiClient>) -> Result<Value, String> {
    client.get_json("/health").await
}

#[tauri::command]
async fn check_python(client: State<'_, ApiClient>) -> Result<String, String> {
    match client.get_json("/health").await {
        Ok(_) => Ok("online".into()),
        Err(_) => Ok("offline".into()),
    }
}

#[tauri::command]
async fn check_go(_client: State<'_, ApiClient>) -> Result<String, String> {
    let go_client = ApiClient::new("http://127.0.0.1:8126".into());
    match go_client.get_json("/health").await {
        Ok(_) => Ok("online".into()),
        Err(_) => Ok("offline".into()),
    }
}

#[tauri::command]
async fn process_data(client: State<'_, ApiClient>, input: String) -> Result<String, String> {
    let payload = serde_json::json!({ "input": input });
    let result = client.post_json("/process", payload).await?;
    Ok(result.to_string())
}

#[tauri::command]
async fn open_file_dialog(_app: tauri::AppHandle) -> Result<Option<String>, String> {
    Ok(None)
}
