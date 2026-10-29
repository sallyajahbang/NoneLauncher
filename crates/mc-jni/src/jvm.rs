//! Starts a JVM from a runtime's `libjvm.so` (loaded with dlopen) and runs
//! `main(String[])` of the given class.

use std::ffi::{c_void, CString};
use std::os::raw::c_char;
use std::ptr;

use jni::objects::{JObject, JValue};
use jni::sys::{jint, jsize, JavaVMInitArgs, JavaVMOption, JNI_OK, JNI_VERSION_1_8};
use jni::{JNIEnv, JavaVM};
use libloading::{Library, Symbol};
use mc_core::{Error, Result};

type CreateJavaVm =
    unsafe extern "system" fn(*mut *mut jni::sys::JavaVM, *mut *mut c_void, *mut c_void) -> jint;

fn err<E: std::fmt::Display>(e: E) -> Error {
    Error::Other(e.to_string())
}

/// `-cp X` / `-classpath X` are command-line flags. The JNI invocation API
/// only accepts them as properties, so they become `-Djava.class.path=X`.
fn to_jvm_options(args: &[String]) -> Vec<String> {
    let mut out = Vec::new();
    let mut it = args.iter();
    while let Some(arg) = it.next() {
        if arg == "-cp" || arg == "-classpath" {
            if let Some(value) = it.next() {
                out.push(format!("-Djava.class.path={value}"));
            }
        } else {
            out.push(arg.clone());
        }
    }
    out
}

/// Runs `main_class.main(args)` in a new JVM. Blocks until main returns.
///
/// The `libjvm.so` handle is intentionally leaked: unloading it while the JVM
/// is alive would crash the process.
pub fn run_main(
    libjvm_path: &str,
    jvm_args: &[String],
    main_class: &str,
    args: &[String],
) -> Result<()> {
    let options = to_jvm_options(jvm_args);
    let c_options: Vec<CString> = options
        .iter()
        .map(|o| CString::new(o.as_str()).map_err(err))
        .collect::<Result<_>>()?;
    let mut opts: Vec<JavaVMOption> = c_options
        .iter()
        .map(|c| JavaVMOption {
            optionString: c.as_ptr() as *mut c_char,
            extraInfo: ptr::null_mut(),
        })
        .collect();
    let mut init = JavaVMInitArgs {
        version: JNI_VERSION_1_8,
        nOptions: opts.len() as jint,
        options: opts.as_mut_ptr(),
        ignoreUnrecognized: 1,
    };

    let lib = unsafe { Library::new(libjvm_path) }.map_err(err)?;
    let create: Symbol<CreateJavaVm> = unsafe { lib.get(b"JNI_CreateJavaVM\0") }.map_err(err)?;

    let mut vm: *mut jni::sys::JavaVM = ptr::null_mut();
    let mut env_ptr: *mut c_void = ptr::null_mut();
    let rc = unsafe {
        create(
            &mut vm,
            &mut env_ptr,
            &mut init as *mut JavaVMInitArgs as *mut c_void,
        )
    };
    drop(create);
    std::mem::forget(lib);
    if rc != JNI_OK {
        return Err(Error::Other(format!("JNI_CreateJavaVM gagal (kode {rc})")));
    }

    let jvm = unsafe { JavaVM::from_raw(vm) }.map_err(err)?;
    let mut env = jvm.attach_current_thread().map_err(err)?;
    if let Err(e) = call_main(&mut env, main_class, args) {
        let _ = env.exception_describe();
        return Err(e);
    }
    Ok(())
}

fn call_main(env: &mut JNIEnv, main_class: &str, args: &[String]) -> Result<()> {
    let class = env.find_class(main_class.replace('.', "/")).map_err(err)?;
    let string_cls = env.find_class("java/lang/String").map_err(err)?;
    let arr = env
        .new_object_array(args.len() as jsize, string_cls, JObject::null())
        .map_err(err)?;
    for (i, a) in args.iter().enumerate() {
        let s = env.new_string(a).map_err(err)?;
        env.set_object_array_element(&arr, i as jsize, JObject::from(s))
            .map_err(err)?;
    }
    env.call_static_method(
        class,
        "main",
        "([Ljava/lang/String;)V",
        &[JValue::Object(&arr)],
    )
    .map_err(err)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classpath_flag_becomes_property() {
        let args = vec![
            "-Xmx2G".to_string(),
            "-cp".to_string(),
            "/a.jar:/b.jar".to_string(),
        ];
        assert_eq!(
            to_jvm_options(&args),
            vec!["-Xmx2G", "-Djava.class.path=/a.jar:/b.jar"]
        );
    }
}
