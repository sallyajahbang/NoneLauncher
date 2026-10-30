//! JNI boundary between the Android Kotlin application and Rust game core.

use jni::objects::JObject;
use jni::sys::{jint, jlong, jintArray, jstring};
use jni::JNIEnv;
use rustcraft_core::generate_surface_map;
use std::ptr;

#[no_mangle]
pub extern "system" fn Java_com_rustcraft_NativeBridge_nativeEngineVersion(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env.new_string("RustCraft native core 0.1.0") {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_rustcraft_NativeBridge_nativeGenerateSurfaceMap(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    width: jint,
    depth: jint,
    seed: jlong,
) -> jintArray {
    let width = width.clamp(1, 128) as usize;
    let depth = depth.clamp(1, 128) as usize;
    let data = generate_surface_map(width, depth, seed as u64)
        .into_iter()
        .map(|value| value as jint)
        .collect::<Vec<_>>();

    let array = match env.new_int_array(data.len() as jint) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };

    if env.set_int_array_region(&array, 0, &data).is_err() {
        return ptr::null_mut();
    }

    array.into_raw()
}
