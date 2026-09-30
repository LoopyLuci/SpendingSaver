//! ADB integration: discover, install-to, and pair Android devices,
//! plus fetch the host connection key from the local backend.

use serde::Serialize;

/// A single device discovered via `adb devices -l`.
#[derive(Serialize)]
pub struct AndroidDevice {
    pub serial: String,
    pub model: String,
    pub state: String,
}

/// Absolute path to the adb binary. Prefers the standard Android SDK
/// locations, falling back to common platform-tools layouts.
fn adb_path() -> String {
    if let Ok(dir) = std::env::var("ANDROID_HOME") {
        if !dir.is_empty() {
            return format!("{}/platform-tools/adb.exe", dir);
        }
    }
    if let Ok(dir) = std::env::var("ANDROID_SDK_ROOT") {
        if !dir.is_empty() {
            return format!("{}/platform-tools/adb.exe", dir);
        }
    }
    "C:/Users/limpi/AppData/Local/Android/Sdk/platform-tools/adb.exe".to_string()
}

/// Absolute path to the debug APK produced by the SpendingSaver Android app.
fn apk_path() -> String {
    "Z:/Projects/SpendingSaver/SpendingSaverAndroid/app/build/outputs/apk/debug/app-debug.apk".to_string()
}

/// Run a command and capture its stdout. Returns Err on spawn failure or
/// non-zero exit code (including stderr in the message).
fn run(cmd: &mut std::process::Command) -> Result<String, String> {
    let output = cmd.output().map_err(|e| format!("failed to run adb: {}", e))?;
    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr).to_string();
        return Err(format!(
            "command failed ({}): {}{}",
            output.status,
            stdout.trim(),
            if stderr.trim().is_empty() { String::new() } else { format!(" | {}", stderr.trim()) }
        ));
    }
    Ok(stdout)
}

/// List connected Android devices by parsing `adb devices -l`.
#[tauri::command]
pub(crate) async fn list_android_devices() -> Result<Vec<AndroidDevice>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let adb = adb_path();
        let mut cmd = std::process::Command::new(&adb);
        cmd.arg("devices").arg("-l");
        let output = run(&mut cmd)?;

        let mut devices = Vec::new();
        for line in output.lines() {
            let line = line.trim();
            if line.is_empty()
                || line.starts_with("List of devices")
                || line.starts_with("* daemon")
            {
                continue;
            }
            let mut parts = line.split_whitespace();
            let serial = parts.next().unwrap_or("").to_string();
            let state = parts.next().unwrap_or("").to_string();
            if serial.is_empty() || state.is_empty() {
                continue;
            }
            let model = parts
                .find(|p| p.starts_with("model:"))
                .map(|p| p.trim_start_matches("model:").to_string())
                .unwrap_or_else(|| "Unknown".to_string());
            devices.push(AndroidDevice { serial, model, state });
        }
        Ok(devices)
    })
    .await
    .map_err(|e| e.to_string())?
}

/// Install the debug APK onto the given device (`adb -s <serial> install -r`).
#[tauri::command]
pub(crate) async fn install_apk(serial: String) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let adb = adb_path();
        let apk = apk_path();
        let mut cmd = std::process::Command::new(&adb);
        cmd.arg("-s").arg(&serial).arg("install").arg("-r").arg(&apk);
        let output = run(&mut cmd)?;
        Ok(format!("Install on {}:\n{}", serial, output.trim()))
    })
    .await
    .map_err(|e| e.to_string())?
}

/// Return the absolute path to the APK (used by "Send APK").
#[tauri::command]
pub(crate) fn send_apk() -> Result<String, String> {
    Ok(apk_path())
}

/// Ask the device to launch the SpendingSaver app with pairing args so it
/// can connect back to the PC's backend over LAN.
#[tauri::command]
pub(crate) async fn pair_android(
    serial: String,
    server_host: String,
    pair_key: String,
) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let adb = adb_path();
        let mut cmd = std::process::Command::new(&adb);
        cmd.arg("-s")
            .arg(&serial)
            .arg("shell")
            .arg("am")
            .arg("start")
            .arg("-n")
            .arg("com.spendingsaver/.MainActivity")
            .arg("--ez")
            .arg("launch_pair")
            .arg("true")
            .arg("--es")
            .arg("pair_host")
            .arg(&server_host)
            .arg("--es")
            .arg("pair_key")
            .arg(&pair_key);
        let output = run(&mut cmd)?;
        Ok(format!("Pairing requested on {}: {}", serial, output.trim()))
    })
    .await
    .map_err(|e| e.to_string())?
}

/// Fetch host connection info (LAN ip + pairing key) from the local backend.
#[tauri::command]
pub(crate) fn get_host_info() -> Result<String, String> {
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_millis(5000))
        .build()
        .map_err(|e| e.to_string())?;
    let resp = client
        .get("http://127.0.0.1:8125/api/host/info")
        .send()
        .map_err(|e| format!("GET /api/host/info failed: {}", e))?;
    if !resp.status().is_success() {
        return Err(format!("GET /api/host/info returned {}", resp.status()));
    }
    resp.text().map_err(|e| e.to_string())
}