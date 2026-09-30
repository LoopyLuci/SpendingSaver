use reqwest::Client;
use serde_json::Value;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::{AppHandle, State, Manager};

mod sidecar;
pub use sidecar::*;

mod android;

#[derive(Clone)]
struct SchedulerState(Arc<Mutex<SchedulerStatusInner>>);

#[derive(Clone)]
struct SchedulerStatusInner {
    last_run: Instant,
    running: bool,
}

impl SchedulerState {
    fn new() -> Self {
        Self(Arc::new(Mutex::new(SchedulerStatusInner { last_run: Instant::now(), running: false })))
    }
}

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
async fn get_scheduler_status(state: State<'_, SchedulerState>) -> Result<Value, String> {
    let guard = state.0.lock().unwrap();
    let now = Instant::now();
    let elapsed = now.duration_since(guard.last_run);
    let next_run = Duration::from_secs(60 * 60 * 6).saturating_sub(elapsed);
    Ok(serde_json::json!({
        "last_run_secs": elapsed.as_secs(),
        "next_run_secs": next_run.as_secs(),
        "running": guard.running,
    }))
}

#[tauri::command]
async fn run_recurring_scheduler(client: State<'_, ApiClient>, scheduler: State<'_, SchedulerState>) -> Result<Value, String> {
    {
        let mut guard = scheduler.0.lock().unwrap();
        guard.running = true;
    }
    let result = client.post_json("/api/recurring-transactions/run-scheduler", serde_json::json!({})).await;
    {
        let mut guard = scheduler.0.lock().unwrap();
        guard.last_run = Instant::now();
        guard.running = false;
    }
    result
}

#[tauri::command]
async fn frontend_log(message: String) -> Result<(), String> {
    log::info!("[frontend] {}", message);
    Ok(())
}

#[tauri::command]
async fn process_data(client: State<'_, ApiClient>, input: String) -> Result<String, String> {
    let payload = serde_json::json!({ "input": input });
    let result = client.post_json("/process", payload).await?;
    Ok(result.to_string())
}

#[tauri::command]
async fn open_file_dialog(app: AppHandle) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::DialogExt;
    let file_path = app.dialog().file().add_filter("Data files", &["txt", "csv", "json"]).blocking_pick_file();
    Ok(file_path.map(|p| p.to_string()))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let backend_handle = BackendHandle::new();

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_log::Builder::default().build())
        .plugin(tauri_plugin_updater::Builder::new().pubkey("").build())
        .invoke_handler(tauri::generate_handler![
            health_check,
            check_python,
            check_go,
            get_scheduler_status,
            run_recurring_scheduler,
            process_data,
            open_file_dialog,
            frontend_log,
            start_python_cmd,
            start_go_cmd,
            stop_python_cmd,
            stop_go_cmd,
            start_mcp_cmd,
            stop_mcp_cmd,
            backend_summary,
            android::list_android_devices,
            android::install_apk,
            android::send_apk,
            android::pair_android,
            android::get_host_info
        ])
        .manage(backend_handle)
        .manage(SchedulerState::new())
        .setup(|app| {
            let handle = app.state::<BackendHandle>();
            // Auto-start all backends — user never sees or manages these.
            let _ = start_python(handle.inner(), app.handle());
            let _ = start_go(handle.inner(), app.handle());
            let _ = start_mcp(handle.inner(), app.handle());
            // Auto-restart anything that dies.
            start_monitor(app.state::<BackendHandle>().inner(), app.handle());

            let api_client = ApiClient::new("http://127.0.0.1:8125".into());
            let scheduler_state = app.state::<SchedulerState>().0.clone();
            std::thread::spawn(move || {
                loop {
                    std::thread::sleep(std::time::Duration::from_secs(60 * 60 * 6));
                    let _ = api_client.post_json("/api/recurring-transactions/run-scheduler", serde_json::json!({}));
                    if let Ok(mut guard) = scheduler_state.lock() {
                        guard.last_run = Instant::now();
                    }
                }
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
