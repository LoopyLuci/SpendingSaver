//! JNI surface for the Android app (`com.spendingsaver.link.NativeLink`).
//!
//! Three calls, all string-in / JSON-string-out so Kotlin needs no native types:
//! `nativeStart`, `nativeCall`, `nativeStop`.

use std::sync::{Mutex, OnceLock};

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde_json::{json, Value};
use tokio::runtime::Runtime;

use crate::{Link, LinkConfig};

fn runtime() -> &'static Runtime {
    static RT: OnceLock<Runtime> = OnceLock::new();
    RT.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("tokio runtime")
    })
}

static LINK: Mutex<Option<std::sync::Arc<Link>>> = Mutex::new(None);

fn s(env: &mut JNIEnv, v: &JString) -> String {
    env.get_string(v).map(|x| x.into()).unwrap_or_default()
}

fn reply(env: &mut JNIEnv, v: Value) -> jstring {
    env.new_string(v.to_string()).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_spendingsaver_link_NativeLink_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    state_dir: JString,
    name: JString,
    relays_csv: JString,
) -> jstring {
    let cfg = LinkConfig {
        state_dir: s(&mut env, &state_dir).into(),
        name: s(&mut env, &name),
        relays: s(&mut env, &relays_csv)
            .split(',')
            .map(|r| r.trim().to_string())
            .filter(|r| !r.is_empty())
            .collect(),
        listen: None,
        direct: Vec::new(),
        hub: None,
    };
    let out = std::panic::catch_unwind(|| runtime().block_on(Link::start(cfg)));
    let v = match out {
        Ok(Ok(link)) => {
            let id = link.node_id();
            if let Ok(mut slot) = LINK.lock() {
                *slot = Some(std::sync::Arc::new(link));
            }
            json!({"ok": true, "result": {"node_id": id}})
        }
        Ok(Err(e)) => json!({"ok": false, "error": e}),
        Err(_) => json!({"ok": false, "error": "link crashed while starting"}),
    };
    reply(&mut env, v)
}

#[no_mangle]
pub extern "system" fn Java_com_spendingsaver_link_NativeLink_nativeCall(
    mut env: JNIEnv,
    _class: JClass,
    op: JString,
    args_json: JString,
) -> jstring {
    let op = s(&mut env, &op);
    let args: Value = serde_json::from_str(&s(&mut env, &args_json)).unwrap_or_else(|_| json!({}));
    let link = LINK.lock().ok().and_then(|g| g.clone());
    let v = match link {
        None => json!({"ok": false, "error": "link is not running"}),
        Some(link) => match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            runtime().block_on(link.call(&op, args))
        })) {
            Ok(Ok(r)) => json!({"ok": true, "result": r}),
            Ok(Err(e)) => json!({"ok": false, "error": e}),
            Err(_) => json!({"ok": false, "error": "link crashed"}),
        },
    };
    reply(&mut env, v)
}

#[no_mangle]
pub extern "system" fn Java_com_spendingsaver_link_NativeLink_nativeStop(_env: JNIEnv, _class: JClass) {
    if let Ok(mut slot) = LINK.lock() {
        *slot = None;
    }
}
