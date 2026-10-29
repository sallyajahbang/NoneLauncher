//! JNI exports called from `com.sally.nonelauncher.core.NativeBridge` (Kotlin).
//!
//! Every function returns a JSON string. On failure it returns `{"error": "..."}`,
//! so Kotlin only has to parse one shape.

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

use mc_core::auth;
use mc_core::download;
use mc_core::launch::{self, LaunchConfig};
use mc_core::manifest::MANIFEST_URL;
use mc_core::version::VersionJson;
use mc_core::{Error, Result};

fn read(env: &mut JNIEnv, s: &JString) -> Result<String> {
    let java = env
        .get_string(s)
        .map_err(|e| Error::Other(format!("invalid Java string: {e}")))?;
    Ok(java.into())
}

fn into_json_or_error(res: Result<String>) -> String {
    match res {
        Ok(s) => s,
        Err(e) => serde_json::json!({ "error": e.to_string() }).to_string(),
    }
}

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(j) => j.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_fetchText(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    let out = into_json_or_error(read(&mut env, &url).and_then(|u| download::fetch_text(&u)));
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_fetchManifest(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let out = into_json_or_error(download::fetch_text(MANIFEST_URL));
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_offlineUuid(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
) -> jstring {
    let out = into_json_or_error(read(&mut env, &name).map(|n| auth::offline_uuid(&n)));
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_buildCommand(
    mut env: JNIEnv,
    _class: JClass,
    version_json: JString,
    config_json: JString,
) -> jstring {
    let mut run = || -> Result<String> {
        let version: VersionJson = serde_json::from_str(&read(&mut env, &version_json)?)?;
        let cfg: LaunchConfig = serde_json::from_str(&read(&mut env, &config_json)?)?;
        let cmd = launch::build(&version, &cfg)?;
        Ok(serde_json::json!({ "program": cmd.program, "args": cmd.args }).to_string())
    };
    let out = into_json_or_error(run());
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_downloadTasks(
    mut env: JNIEnv,
    _class: JClass,
    version_json: JString,
    config_json: JString,
) -> jstring {
    let mut run = || -> Result<String> {
        let version: VersionJson = serde_json::from_str(&read(&mut env, &version_json)?)?;
        let cfg: LaunchConfig = serde_json::from_str(&read(&mut env, &config_json)?)?;
        Ok(serde_json::to_string(&download::plan(&version, &cfg))?)
    };
    let out = into_json_or_error(run());
    to_jstring(&mut env, &out)
}
