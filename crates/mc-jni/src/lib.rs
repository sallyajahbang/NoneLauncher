//! JNI exports called from `com.sally.nonelauncher.core.NativeBridge` (Kotlin).
//!
//! Every function returns a JSON string. On failure it returns `{"error": "..."}`,
//! so Kotlin only has to parse one shape.

use std::path::Path;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

use mc_core::auth;
use mc_core::download::{self, AssetIndex, DownloadTask};
use mc_core::launch::{self, LaunchConfig};
use mc_core::manifest::MANIFEST_URL;
use mc_core::version::VersionJson;
use mc_core::{Error, Result};

mod jvm;

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

/// `indexJson` is the asset index file. Returns a JSON array of download tasks.
#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_assetTasks(
    mut env: JNIEnv,
    _class: JClass,
    index_json: JString,
    assets_dir: JString,
) -> jstring {
    let mut run = || -> Result<String> {
        let index: AssetIndex = serde_json::from_str(&read(&mut env, &index_json)?)?;
        let dir = read(&mut env, &assets_dir)?;
        Ok(serde_json::to_string(&download::plan_assets(
            &index,
            Path::new(&dir),
        ))?)
    };
    let out = into_json_or_error(run());
    to_jstring(&mut env, &out)
}

/// Downloads one task (as produced by `downloadTasks` or `assetTasks`).
#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_downloadFile(
    mut env: JNIEnv,
    _class: JClass,
    task_json: JString,
) -> jstring {
    let mut run = || -> Result<String> {
        let task: DownloadTask = serde_json::from_str(&read(&mut env, &task_json)?)?;
        download::download(&task)?;
        Ok(r#"{"ok":true}"#.to_string())
    };
    let out = into_json_or_error(run());
    to_jstring(&mut env, &out)
}

/// Starts the JVM and runs `mainClass.main`. `argsJson` is the full argument
/// list from `buildCommand` (JVM options, main class, game arguments).
/// Blocks until the game exits, so call it from a background thread.
#[no_mangle]
pub extern "system" fn Java_com_sally_nonelauncher_core_NativeBridge_launchJvm(
    mut env: JNIEnv,
    _class: JClass,
    libjvm_path: JString,
    main_class: JString,
    args_json: JString,
) -> jstring {
    let mut run = || -> Result<String> {
        let libjvm = read(&mut env, &libjvm_path)?;
        let main = read(&mut env, &main_class)?;
        let all: Vec<String> = serde_json::from_str(&read(&mut env, &args_json)?)?;
        let idx = all
            .iter()
            .position(|a| *a == main)
            .ok_or_else(|| Error::Other("main class tidak ada di argumen".into()))?;
        let (jvm_args, rest) = all.split_at(idx);
        jvm::run_main(&libjvm, jvm_args, &main, &rest[1..])?;
        Ok(r#"{"ok":true}"#.to_string())
    };
    let out = into_json_or_error(run());
    to_jstring(&mut env, &out)
}
