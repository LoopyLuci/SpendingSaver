use std::os::windows::process::CommandExt;
use std::{sync::Arc, thread, time::Duration};
use tauri::Emitter;

const CREATE_NO_WINDOW: u32 = 0x08000000;
const PYTHON_PORT: u16 = 8125;
const GO_PORT: u16 = 8126;

/// Find a python.exe that has the backend's packages installed.
/// Order: SPENDINGSAVER_PYTHON, the project's virtual environments, then `python`/`py` on PATH.
fn which_python(root: &std::path::Path) -> Option<String> {
    let mut candidates: Vec<String> = Vec::new();
    if let Ok(p) = std::env::var("SPENDINGSAVER_PYTHON") {
        candidates.push(p);
    }
    for venv in [root.join("backend").join(".venv"), root.join(".venv"), root.join("..").join(".venv"), root.join("..").join(".venv-check")] {
        let exe = venv.join("Scripts").join("python.exe");
        if exe.exists() {
            candidates.push(exe.to_string_lossy().to_string());
        }
    }
    for c in ["python.exe", "python3.exe", "py.exe"] {
        candidates.push(c.to_string());
    }
    for c in candidates {
        let output = std::process::Command::new(&c)
            .creation_flags(CREATE_NO_WINDOW)
            .arg("-c")
            .arg("import sys, sqlalchemy, fastapi, uvicorn; print(sys.executable)")
            .output();
        if let Ok(o) = output {
            if o.status.success() {
                let path = String::from_utf8_lossy(&o.stdout).trim().to_string();
                if path.ends_with(".exe") {
                    return Some(path);
                }
            }
        }
    }
    None
}

/// Kill any process currently listening on the given TCP port on Windows
/// by parsing `netstat -ano`. This frees the port before we bind.
fn free_port(port: u16) {
    if !cfg!(target_os = "windows") {
        return;
    }
    let output = std::process::Command::new("netstat")
        .creation_flags(CREATE_NO_WINDOW)
        .args(["-ano", "-p", "tcp"])
        .output()
        .ok();
    if let Some(output) = output {
        let text = String::from_utf8_lossy(&output.stdout);
        let needle = format!(":{}", port);
        for line in text.lines() {
            if line.contains(&needle) && (line.contains("LISTENING") || line.contains("LISTEN")) {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if let Some(pid_str) = parts.last() {
                    if let Ok(pid) = pid_str.parse::<u32>() {
                        // Don't kill our own process tree; anything on this port is stale.
                        let _ = std::process::Command::new("taskkill")
                            .creation_flags(CREATE_NO_WINDOW)
                            .args(["/F", "/T", "/PID", &pid.to_string()])
                            .output();
                    }
                }
            }
        }
    }
}

/// Wait until the HTTP server on the given port responds to /health,
/// or return after the timeout.
fn wait_ready(port: u16, timeout_secs: u64) -> bool {
    let deadline = Duration::from_secs(timeout_secs);
    let start = std::time::Instant::now();
    while start.elapsed() < deadline {
        let ok = std::process::Command::new("curl")
            .creation_flags(CREATE_NO_WINDOW)
            .args(["-s", "-o", "NUL", "-w", "%{http_code}", &format!("http://127.0.0.1:{}/health", port)])
            .output();
        if let Ok(o) = ok {
            let code = String::from_utf8_lossy(&o.stdout).trim().to_string();
            if code.starts_with("2") || code.starts_with("3") {
                return true;
            }
        }
        thread::sleep(Duration::from_millis(500));
    }
    false
}

#[derive(Clone)]
pub struct BackendHandle {
    pub python_pid: Arc<std::sync::Mutex<Option<u32>>>,
    pub go_pid: Arc<std::sync::Mutex<Option<u32>>>,
    pub mcp_pid: Arc<std::sync::Mutex<Option<u32>>>,
}

impl BackendHandle {
    pub fn new() -> Self {
        Self {
            python_pid: Arc::new(std::sync::Mutex::new(None)),
            go_pid: Arc::new(std::sync::Mutex::new(None)),
            mcp_pid: Arc::new(std::sync::Mutex::new(None)),
        }
    }
}

fn kill_pid(pid: Option<u32>) -> Result<String, String> {
    let pid = pid.ok_or_else(|| "not running".to_string())?;
    if cfg!(target_os = "windows") {
        let output = std::process::Command::new("taskkill")
            .creation_flags(CREATE_NO_WINDOW)
            .arg("/F")
            .arg("/T")
            .arg("/PID")
            .arg(pid.to_string())
            .output()
            .map_err(|e| format!("taskkill failed: {}", e))?;
        if output.status.success() {
            Ok(format!("terminated pid={}", pid))
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            Err(format!("taskkill error: {}", stderr))
        }
    } else {
        let output = std::process::Command::new("kill")
            .arg("-9")
            .arg(pid.to_string())
            .output()
            .map_err(|e| format!("kill failed: {}", e))?;
        if output.status.success() {
            Ok(format!("terminated pid={}", pid))
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            Err(format!("kill error: {}", stderr))
        }
    }
}

/// Resolve the project root from the executable location.
/// exe: <root>/tauri-app/src-tauri/target/release/spendingsaver.exe
fn project_root() -> std::path::PathBuf {
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|p| p.to_path_buf()))
        .unwrap_or_else(|| std::path::PathBuf::from("."));
    // Release: up 4 dirs. Debug: also up 4 dirs to root.
    let mut dir = exe_dir.clone();
    for _ in 0..4 {
        if let Some(parent) = dir.parent() {
            dir = parent.to_path_buf();
        }
    }
    let canonical = dir.canonicalize().unwrap_or(dir);
    canonical
}

/// Resolve a bundled resource directory from the tauri::Manager resource dir if
/// available, else fall back to the dev-facing project tree.
fn backend_base(app: &tauri::AppHandle) -> std::path::PathBuf {
    // 1) Bundled resource: <resource_dir>/backend-service/
    let resource_dir = tauri::Manager::path(app)
        .resource_dir()
        .unwrap_or_else(|_| std::path::PathBuf::from("."));
    let bundled_backend = resource_dir.join("backend-service");
    if bundled_backend.exists() {
        return bundled_backend;
    }
    // 2) Bundled standalone exe next to the app binary
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|p| p.to_path_buf()))
        .unwrap_or_else(|| std::path::PathBuf::from("."));
    for cand in ["backend-service.exe", "backend_service.exe"] {
        let p = exe_dir.join(cand);
        if p.exists() {
            return exe_dir;
        }
    }
    // 3) Dev tree: <root>/backend
    project_root().join("backend")
}

/// Resolve the go-service binary/bundle dir.
fn go_base(app: &tauri::AppHandle) -> std::path::PathBuf {
    let resource_dir = tauri::Manager::path(app)
        .resource_dir()
        .unwrap_or_else(|_| std::path::PathBuf::from("."));
    let bundled_go = resource_dir.join("go-service");
    if bundled_go.exists() {
        return bundled_go;
    }
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|p| p.to_path_buf()))
        .unwrap_or_else(|| std::path::PathBuf::from("."));
    for cand in ["go-service.exe", "go_service.exe"] {
        let p = exe_dir.join(cand);
        if p.exists() {
            return exe_dir;
        }
    }
    project_root().join("go-service")
}

/// Find a bundled standalone python backend exe (PyInstaller one-file or COLLECT dir).
fn bundled_python_exe(app: &tauri::AppHandle) -> Option<std::path::PathBuf> {
    let dirs = vec![
        tauri::Manager::path(app)
            .resource_dir()
            .unwrap_or_else(|_| std::path::PathBuf::from(".")),
        std::env::current_exe()
            .ok()
            .and_then(|p| p.parent().map(|p| p.to_path_buf()))
            .unwrap_or_default(),
    ];
    // Names in both a flat layout (exe_dir/backend-service.exe) and the
    // PyInstaller COLLECT layout (exe_dir/backend-service/backend-service.exe).
    for dir in &dirs {
        for candidate in [
            dir.join("backend-service.exe"),
            dir.join("backend_service.exe"),
            dir.join("spendingsaver-backend.exe"),
            dir.join("backend-service").join("backend-service.exe"),
            dir.join("backend-service").join("backend_service.exe"),
        ] {
            if candidate.exists() {
                return Some(candidate);
            }
        }
    }
    None
}

pub fn start_python(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    // Free the port first so a stale listener never crashes us.
    free_port(PYTHON_PORT);

    let mut guard = handle.python_pid.lock().unwrap();
    if guard.is_some() {
        return Ok("already running".into());
    }
    // Prefer a bundled standalone backend exe (PyInstaller) for offline mode.
    if let Some(bundled) = bundled_python_exe(app) {
        let mut cmd = std::process::Command::new(&bundled);
        cmd.creation_flags(CREATE_NO_WINDOW)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::inherit())
            .arg("--host")
            .arg("127.0.0.1")
            .arg("--port")
            .arg(PYTHON_PORT.to_string());
        match cmd.spawn() {
            Ok(mut child) => {
                let pid = child.id();
                *guard = Some(pid);
                thread::spawn(move || { let _ = child.wait(); });
                let _ = app.emit("python-status", serde_json::json!({"status": "online"}));
                return Ok(format!("started bundled backend pid={}", pid));
            }
            Err(e) => {
                // fall through to python -m uvicorn path
                let _ = e;
            }
        }
    }
    let root = project_root();
    let python_dir = root.join("backend");
    let python_exe = which_python(&root).unwrap_or_else(|| "python".to_string());
    let mut cmd = std::process::Command::new(&python_exe);
    cmd.creation_flags(CREATE_NO_WINDOW)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::inherit())
        .current_dir(&python_dir)
        .arg("-m")
        .arg("uvicorn")
        .arg("app.api:app")
        .arg("--host")
        .arg("127.0.0.1")
        .arg("--port")
        .arg(PYTHON_PORT.to_string());
    match cmd.spawn() {
        Ok(mut child) => {
            let pid = child.id();
            *guard = Some(pid);
            thread::spawn(move || {
                let _ = child.wait();
            });
            let _ = app.emit("python-status", serde_json::json!({"status": "online"}));
            Ok(format!("started pid={}", pid))
        }
        Err(e) => Err(format!("failed to start python backend: {}", e)),
    }
}

pub fn stop_python(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    let mut guard = handle.python_pid.lock().unwrap();
    let result = kill_pid(*guard)?;
    *guard = None;
    let _ = app.emit("python-status", serde_json::json!({"status": "offline"}));
    Ok(result)
}

pub fn start_go(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    free_port(GO_PORT);
    let mut guard = handle.go_pid.lock().unwrap();
    if guard.is_some() {
        return Ok("already running".into());
    }
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|p| p.to_path_buf()))
        .unwrap_or_else(|| std::path::PathBuf::from("."));
    let resource_dir = tauri::Manager::path(app)
        .resource_dir()
        .unwrap_or_else(|_| std::path::PathBuf::from("."));
    // Prefer the binary right next to the app exe (installed layout), then the
    // Tauri bundled resource dir, then the dev-facing project tree.
    let mut go_root = exe_dir.join("go-service");
    let mut go_bin = go_root.join("go-service.exe");
    if resource_dir.join("go-service.exe").exists() {
        go_bin = resource_dir.join("go-service.exe");
        go_root = resource_dir.clone();
    } else if !go_bin.exists() && resource_dir.join("go-service").join("go-service.exe").exists() {
        go_bin = resource_dir.join("go-service").join("go-service.exe");
        go_root = resource_dir.join("go-service");
    }
    if !go_bin.exists() {
        go_root = project_root().join("go-service");
        go_bin = go_root.join("go-service.exe");
    }
    let mut cmd;
    if go_bin.exists() {
        cmd = std::process::Command::new(&go_bin);
        cmd.current_dir(&go_root);
    } else {
        cmd = std::process::Command::new("go.exe");
        cmd.current_dir(&go_root).arg("run").arg(".");
    }
    cmd.creation_flags(CREATE_NO_WINDOW)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::inherit());
    match cmd.spawn() {
        Ok(mut child) => {
            let pid = child.id();
            *guard = Some(pid);
            thread::spawn(move || {
                let _ = child.wait();
            });
            let _ = app.emit("go-status", serde_json::json!({"status": "online"}));
            Ok(format!("started pid={}", pid))
        }
        Err(e) => Err(format!("failed to start go service: {}", e)),
    }
}

pub fn stop_go(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    let mut guard = handle.go_pid.lock().unwrap();
    let result = kill_pid(*guard)?;
    *guard = None;
    let _ = app.emit("go-status", serde_json::json!({"status": "offline"}));
    Ok(result)
}

/// Start the Python MCP server as a managed subprocess.
pub fn start_mcp(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    let mut guard = handle.mcp_pid.lock().unwrap();
    if guard.is_some() {
        return Ok("already running".into());
    }
    let python_exe = which_python(&project_root()).unwrap_or_else(|| "python".to_string());
    let mcp_module = if std::path::Path::new("spendingsaver_mcp").exists() {
        "spendingsaver_mcp.server"
    } else {
        // fall back to -m form
        "spendingsaver_mcp.server"
    };
    let mut cmd = std::process::Command::new(&python_exe);
    cmd.creation_flags(CREATE_NO_WINDOW)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::inherit())
        .arg("-m")
        .arg(mcp_module);
    match cmd.spawn() {
        Ok(mut child) => {
            let pid = child.id();
            *guard = Some(pid);
            thread::spawn(move || {
                let _ = child.wait();
            });
            let _ = app.emit("mcp-status", serde_json::json!({"status": "online"}));
            Ok(format!("started pid={}", pid))
        }
        Err(e) => Err(format!("failed to start mcp server: {}", e)),
    }
}

pub fn stop_mcp(handle: &BackendHandle, app: &tauri::AppHandle) -> Result<String, String> {
    let mut guard = handle.mcp_pid.lock().unwrap();
    let result = kill_pid(*guard)?;
    *guard = None;
    let _ = app.emit("mcp-status", serde_json::json!({"status": "offline"}));
    Ok(result)
}

/// Monitor backends and auto-restart anything that dies.
pub fn start_monitor(handle: &BackendHandle, app: &tauri::AppHandle) {
    let handle = handle.clone();
    let app = app.clone();
    thread::spawn(move || {
        let mut last_python_check = std::time::Instant::now();
        let mut last_go_check = std::time::Instant::now();
        loop {
            thread::sleep(Duration::from_secs(5));
            {
                let guard = handle.python_pid.lock().unwrap();
                let running = guard.is_some();
                let ready = wait_ready(PYTHON_PORT, 1);
                if running && !ready && last_python_check.elapsed().as_secs() > 15 {
                    drop(guard);
                    // backend died; restart it
                    if let Some(pid) = *handle.python_pid.lock().unwrap() {
                        let _ = kill_pid(Some(pid));
                    }
                    {
                        let mut g = handle.python_pid.lock().unwrap();
                        *g = None;
                    }
                    thread::sleep(Duration::from_millis(500));
                    let _ = start_python(&handle, &app);
                    last_python_check = std::time::Instant::now();
                }
            }
            {
                let guard = handle.go_pid.lock().unwrap();
                let running = guard.is_some();
                let ready = wait_ready(GO_PORT, 1);
                if running && !ready && last_go_check.elapsed().as_secs() > 15 {
                    drop(guard);
                    if let Some(pid) = *handle.go_pid.lock().unwrap() {
                        let _ = kill_pid(Some(pid));
                    }
                    {
                        let mut g = handle.go_pid.lock().unwrap();
                        *g = None;
                    }
                    thread::sleep(Duration::from_millis(500));
                    let _ = start_go(&handle, &app);
                    last_go_check = std::time::Instant::now();
                }
            }
        }
    });
}

#[tauri::command]
pub fn start_python_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    start_python(&handle, &app)
}

#[tauri::command]
pub fn stop_python_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    stop_python(&handle, &app)
}

#[tauri::command]
pub fn start_go_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    start_go(&handle, &app)
}

#[tauri::command]
pub fn stop_go_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    stop_go(&handle, &app)
}

#[tauri::command]
pub fn start_mcp_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    start_mcp(&handle, &app)
}

#[tauri::command]
pub fn stop_mcp_cmd(handle: tauri::State<'_, BackendHandle>, app: tauri::AppHandle) -> Result<String, String> {
    stop_mcp(&handle, &app)
}

#[tauri::command]
pub fn backend_summary(handle: tauri::State<'_, BackendHandle>) -> serde_json::Value {
    let python = handle.python_pid.lock().unwrap().is_some();
    let go = handle.go_pid.lock().unwrap().is_some();
    let mcp = handle.mcp_pid.lock().unwrap().is_some();
    serde_json::json!({
        "python": python,
        "go": go,
        "mcp": mcp,
    })
}
