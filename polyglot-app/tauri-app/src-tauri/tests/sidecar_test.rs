use spendingsaver::BackendHandle;

#[test]
fn smoke_sidecar_handle_initial_state() {
    let handle = BackendHandle::new();
    assert_eq!(*handle.python_pid.lock().unwrap(), None);
    assert_eq!(*handle.go_pid.lock().unwrap(), None);
    assert_eq!(*handle.mcp_pid.lock().unwrap(), None);
}
