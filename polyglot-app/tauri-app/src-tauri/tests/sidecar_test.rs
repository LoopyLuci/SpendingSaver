use std::sync::Arc;

#[test]
fn smoke_sidecar_handle_initial_state() {
    let handle = tauri_app::BackendHandle::new();
    let python = handle.python_pid.lock().unwrap();
    assert!(*python == None);

    let go = handle.go_pid.lock().unwrap();
    assert!(*go == None);
}
