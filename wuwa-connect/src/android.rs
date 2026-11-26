//! Android-only glue (JNI + Root/Shizuku + sing-box core/external bridges).

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Method {
    Root = 0,
    Shizuku = 1,
}

impl Method {
    pub fn from_i32(v: i32) -> Self {
        match v {
            1 => Self::Shizuku,
            _ => Self::Root,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::Root => "Root",
            Self::Shizuku => "Shizuku",
        }
    }
}

#[derive(Debug, Clone)]
pub struct CmdResult {
    pub code: i32,
    pub out: String,
    pub err: String,
}

#[cfg(target_os = "android")]
mod imp {
    use super::{CmdResult, Method};
    use jni::objects::{GlobalRef, JClass, JObject, JObjectArray, JString, JValue};
    use jni::{JNIEnv, JavaVM};
    use std::ffi::CString;
    use std::sync::OnceLock;

    static HELPER_CLASS: OnceLock<GlobalRef> = OnceLock::new();

    /// Compatibility shim for prebuilt Skia (`skia-bindings`) compiled against a newer
    /// NDK libc++ that references `std::__ndk1::__hash_memory(const void*, size_t)`.
    #[no_mangle]
    pub unsafe extern "C" fn _ZNSt6__ndk113__hash_memoryEPKvm(
        ptr: *const u8,
        len: usize,
    ) -> usize {
        if ptr.is_null() || len == 0 {
            return 0;
        }
        let bytes = std::slice::from_raw_parts(ptr, len);
        let mut h: u64 = 0xcbf29ce484222325;
        for &b in bytes {
            h ^= b as u64;
            h = h.wrapping_mul(0x100000001b3);
        }
        h as usize
    }

    extern "C" {
        fn __android_log_write(
            prio: std::os::raw::c_int,
            tag: *const std::os::raw::c_char,
            text: *const std::os::raw::c_char,
        ) -> std::os::raw::c_int;
    }

    pub fn log_error(msg: &str) {
        if let (Ok(tag), Ok(text)) = (CString::new("WuWaConnect"), CString::new(msg)) {
            unsafe {
                __android_log_write(6, tag.as_ptr(), text.as_ptr());
            }
        }
    }

    fn with_env<T>(
        f: impl FnOnce(&mut JNIEnv, &JObject) -> Result<T, String>,
    ) -> Result<T, String> {
        let ctx = ndk_context::android_context();
        let vm = unsafe { JavaVM::from_raw(ctx.vm().cast()) }.map_err(|e| e.to_string())?;
        let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
        let act = unsafe { JObject::from_raw(ctx.context().cast()) };
        f(&mut env, &act)
    }

    fn take_exc(env: &mut JNIEnv) -> String {
        if env.exception_check().unwrap_or(false) {
            if let Ok(exc) = env.exception_occurred() {
                let _ = env.exception_clear();
                if let Ok(msg) = env
                    .call_method(&exc, "toString", "()Ljava/lang/String;", &[])
                    .and_then(|v| v.l())
                {
                    if let Ok(s) = env.get_string(&JString::from(msg)) {
                        return s.into();
                    }
                }
            } else {
                let _ = env.exception_clear();
            }
            return "Java exception".into();
        }
        String::new()
    }

    fn helper_class<'a, 'b>(env: &mut JNIEnv<'a>, act: &JObject<'b>) -> Result<JClass<'a>, String> {
        if let Some(g) = HELPER_CLASS.get() {
            let local = env.new_local_ref(g.as_obj()).map_err(|e| e.to_string())?;
            return Ok(JClass::from(local));
        }
        let loader = env
            .call_method(act, "getClassLoader", "()Ljava/lang/ClassLoader;", &[])
            .map_err(|e| {
                let ex = take_exc(env);
                if ex.is_empty() { e.to_string() } else { ex }
            })?
            .l()
            .map_err(|e| e.to_string())?;
        let name = env
            .new_string("id.local.wuwaconnect.PrivilegedHelper")
            .map_err(|e| e.to_string())?;
        let cls_obj = env
            .call_method(
                &loader,
                "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                &[JValue::Object(&name)],
            )
            .map_err(|e| {
                let ex = take_exc(env);
                if ex.is_empty() { e.to_string() } else { ex }
            })?
            .l()
            .map_err(|e| e.to_string())?;
        let g = env.new_global_ref(&cls_obj).map_err(|e| e.to_string())?;
        let _ = HELPER_CLASS.set(g);
        Ok(JClass::from(cls_obj))
    }

    fn get_jstring(env: &mut JNIEnv, obj: JObject) -> String {
        if obj.is_null() {
            return String::new();
        }
        env.get_string(&JString::from(obj))
            .map(String::from)
            .unwrap_or_default()
    }

    fn parse_ok_err(raw: String) -> Result<String, String> {
        if let Some(rest) = raw.strip_prefix("OK:") {
            Ok(rest.to_string())
        } else if let Some(rest) = raw.strip_prefix("ERR:") {
            Err(rest.to_string())
        } else {
            Ok(raw)
        }
    }

    pub fn init_helper() {
        let _ = with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let _ = env.call_static_method(
                &cls,
                "init",
                "(Landroid/content/Context;)V",
                &[JValue::Object(act)],
            );
            let _ = take_exc(env);
            Ok(())
        });
    }

    pub fn check_method(method: Method, request_permission: bool) -> (i32, String) {
        let res = with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let s = match method {
                Method::Root => env
                    .call_static_method(&cls, "checkRoot", "()Ljava/lang/String;", &[])
                    .map_err(|e| {
                        let ex = take_exc(env);
                        if ex.is_empty() { e.to_string() } else { ex }
                    })?
                    .l()
                    .map_err(|e| e.to_string())?,
                Method::Shizuku => env
                    .call_static_method(
                        &cls,
                        "checkShizuku",
                        "(Z)Ljava/lang/String;",
                        &[JValue::Bool(if request_permission { 1 } else { 0 })],
                    )
                    .map_err(|e| {
                        let ex = take_exc(env);
                        if ex.is_empty() { e.to_string() } else { ex }
                    })?
                    .l()
                    .map_err(|e| e.to_string())?,
            };
            Ok(get_jstring(env, s))
        });

        match res {
            Ok(raw) => {
                if let Some(rest) = raw.strip_prefix("OK:") {
                    (2, rest.to_string())
                } else if let Some(rest) = raw.strip_prefix("ERR:") {
                    (3, rest.to_string())
                } else if raw.is_empty() {
                    (3, "Unknown status".into())
                } else {
                    (2, raw)
                }
            }
            Err(e) => (3, e),
        }
    }

    pub fn exec_privileged(method: Method, script: &str) -> Result<CmdResult, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jscript = env.new_string(script).map_err(|e| e.to_string())?;
            let method_name = match method {
                Method::Root => "execRoot",
                Method::Shizuku => "execShizuku",
            };
            let arr_obj = env
                .call_static_method(
                    &cls,
                    method_name,
                    "(Ljava/lang/String;)[Ljava/lang/String;",
                    &[JValue::Object(&jscript)],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;

            if arr_obj.is_null() {
                return Err("Helper returned null".into());
            }
            let arr = JObjectArray::from(arr_obj);
            let el0 = env.get_object_array_element(&arr, 0).map_err(|e| e.to_string())?;
            let code_s = get_jstring(env, el0);
            let el1 = env.get_object_array_element(&arr, 1).map_err(|e| e.to_string())?;
            let out = get_jstring(env, el1);
            let el2 = env.get_object_array_element(&arr, 2).map_err(|e| e.to_string())?;
            let err = get_jstring(env, el2);
            let code = code_s.trim().parse::<i32>().unwrap_or(-1);
            Ok(CmdResult { code, out, err })
        })
    }

    pub fn get_app_uid(pkg: &str) -> Result<u32, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jpkg = env.new_string(pkg).map_err(|e| e.to_string())?;
            let uid = env
                .call_static_method(
                    &cls,
                    "getAppUid",
                    "(Landroid/content/Context;Ljava/lang/String;)I",
                    &[JValue::Object(act), JValue::Object(&jpkg)],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .i()
                .map_err(|e| e.to_string())?;
            if uid <= 0 {
                Err(format!("Package not installed or invalid UID: {pkg}"))
            } else {
                Ok(uid as u32)
            }
        })
    }

    pub fn http_get(url: &str) -> Result<String, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jurl = env.new_string(url).map_err(|e| e.to_string())?;
            let arr_obj = env
                .call_static_method(
                    &cls,
                    "httpGet",
                    "(Ljava/lang/String;)[Ljava/lang/String;",
                    &[JValue::Object(&jurl)],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;
            let arr = JObjectArray::from(arr_obj);
            let el0 = env.get_object_array_element(&arr, 0).map_err(|e| e.to_string())?;
            let status = get_jstring(env, el0);
            let el1 = env.get_object_array_element(&arr, 1).map_err(|e| e.to_string())?;
            let body = get_jstring(env, el1);
            if status == "OK" {
                Ok(body)
            } else {
                Err(body)
            }
        })
    }

    pub fn start_core(config_json: &str, use_root: bool) -> Result<String, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jcfg = env.new_string(config_json).map_err(|e| e.to_string())?;
            let s = env
                .call_static_method(
                    &cls,
                    "startCore",
                    "(Landroid/content/Context;Ljava/lang/String;Z)Ljava/lang/String;",
                    &[
                        JValue::Object(act),
                        JValue::Object(&jcfg),
                        JValue::Bool(if use_root { 1 } else { 0 }),
                    ],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;
            parse_ok_err(get_jstring(env, s))
        })
    }

    pub fn stop_core(try_root: bool) -> Result<String, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let s = env
                .call_static_method(
                    &cls,
                    "stopCore",
                    "(Landroid/content/Context;Z)Ljava/lang/String;",
                    &[JValue::Object(act), JValue::Bool(if try_root { 1 } else { 0 })],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;
            parse_ok_err(get_jstring(env, s))
        })
    }

    #[allow(dead_code)]
    pub fn import_external(client_id: &str, config_json: &str, raw_uri: &str) -> Result<String, String> {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jid = env.new_string(client_id).map_err(|e| e.to_string())?;
            let jcfg = env.new_string(config_json).map_err(|e| e.to_string())?;
            let juri = env.new_string(raw_uri).map_err(|e| e.to_string())?;
            let s = env
                .call_static_method(
                    &cls,
                    "importExternal",
                    "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    &[
                        JValue::Object(act),
                        JValue::Object(&jid),
                        JValue::Object(&jcfg),
                        JValue::Object(&juri),
                    ],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;
            parse_ok_err(get_jstring(env, s))
        })
    }

    pub fn copy_text(text: &str) {
        let _ = with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jtxt = env.new_string(text).map_err(|e| e.to_string())?;
            let _ = env.call_static_method(
                &cls,
                "copyText",
                "(Landroid/content/Context;Ljava/lang/String;)V",
                &[JValue::Object(act), JValue::Object(&jtxt)],
            );
            let _ = take_exc(env);
            Ok(())
        });
    }

    pub fn paste_text() -> String {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let s = env
                .call_static_method(
                    &cls,
                    "pasteText",
                    "(Landroid/content/Context;)Ljava/lang/String;",
                    &[JValue::Object(act)],
                )
                .map_err(|e| {
                    let _ = take_exc(env);
                    e.to_string()
                })?
                .l()
                .map_err(|e| e.to_string())?;
            Ok(get_jstring(env, s))
        })
        .unwrap_or_default()
    }

    pub fn load_data(name: &str) -> String {
        with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jname = env.new_string(name).map_err(|e| e.to_string())?;
            let s = env
                .call_static_method(
                    &cls,
                    "loadData",
                    "(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;",
                    &[JValue::Object(act), JValue::Object(&jname)],
                )
                .map_err(|e| {
                    let _ = take_exc(env);
                    e.to_string()
                })?
                .l()
                .map_err(|e| e.to_string())?;
            Ok(get_jstring(env, s))
        })
        .unwrap_or_default()
    }

    pub fn save_data(name: &str, content: &str) {
        let _ = with_env(|env, act| {
            let cls = helper_class(env, act)?;
            let jname = env.new_string(name).map_err(|e| e.to_string())?;
            let jcontent = env.new_string(content).map_err(|e| e.to_string())?;
            let _ = env.call_static_method(
                &cls,
                "saveData",
                "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(act), JValue::Object(&jname), JValue::Object(&jcontent)],
            );
            let _ = take_exc(env);
            Ok(())
        });
    }

    pub fn set_proxy(method: Method, value: &str) -> Result<(), String> {
        let escaped = value.replace('\'', "'\\''");
        let cmd = format!("settings put global http_proxy '{escaped}'");
        match exec_privileged(method, &cmd) {
            Ok(r) if r.code == 0 => Ok(()),
            Ok(r) => {
                if set_proxy_jni(value).is_ok() {
                    return Ok(());
                }
                let detail = if !r.err.trim().is_empty() {
                    r.err.trim().to_string()
                } else {
                    format!("exit code {}", r.code)
                };
                Err(format!("Failed to set proxy via {}: {detail}", method.label()))
            }
            Err(e) => {
                if set_proxy_jni(value).is_ok() {
                    return Ok(());
                }
                Err(e)
            }
        }
    }

    fn set_proxy_jni(value: &str) -> Result<(), String> {
        with_env(|env, act| {
            let resolver = env
                .call_method(act, "getContentResolver", "()Landroid/content/ContentResolver;", &[])
                .map_err(|e| e.to_string())?
                .l()
                .map_err(|e| e.to_string())?;
            let k = env.new_string("http_proxy").map_err(|e| e.to_string())?;
            let v = env.new_string(value).map_err(|e| e.to_string())?;
            let ok = env
                .call_static_method(
                    "android/provider/Settings$Global",
                    "putString",
                    "(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z",
                    &[JValue::Object(&resolver), JValue::Object(&k), JValue::Object(&v)],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .z()
                .map_err(|e| e.to_string())?;
            if ok { Ok(()) } else { Err("Settings.Global.putString returned false".into()) }
        })
    }

    pub fn launch(pkg: &str) -> Result<(), String> {
        with_env(|env, act| {
            let pm = env
                .call_method(act, "getPackageManager", "()Landroid/content/pm/PackageManager;", &[])
                .map_err(|e| e.to_string())?
                .l()
                .map_err(|e| e.to_string())?;
            let p = env.new_string(pkg).map_err(|e| e.to_string())?;
            let intent = env
                .call_method(
                    &pm,
                    "getLaunchIntentForPackage",
                    "(Ljava/lang/String;)Landroid/content/Intent;",
                    &[JValue::Object(&p)],
                )
                .map_err(|e| {
                    let ex = take_exc(env);
                    if ex.is_empty() { e.to_string() } else { ex }
                })?
                .l()
                .map_err(|e| e.to_string())?;
            if intent.is_null() {
                return Err(format!("Package not installed: {pkg}"));
            }
            let _ = env.call_method(
                &intent,
                "addFlags",
                "(I)Landroid/content/Intent;",
                &[JValue::Int(0x10000000)],
            );
            env.call_method(
                act,
                "startActivity",
                "(Landroid/content/Intent;)V",
                &[JValue::Object(&intent)],
            )
            .map_err(|e| {
                let ex = take_exc(env);
                if ex.is_empty() { e.to_string() } else { ex }
            })?;
            Ok(())
        })
    }
}

#[cfg(not(target_os = "android"))]
mod imp {
    use super::{CmdResult, Method};

    pub fn log_error(_: &str) {}
    pub fn init_helper() {}
    pub fn check_method(method: Method, _: bool) -> (i32, String) {
        (3, format!("{} requires Android", method.label()))
    }
    pub fn exec_privileged(_: Method, _: &str) -> Result<CmdResult, String> {
        Err("android only".into())
    }
    pub fn get_app_uid(_: &str) -> Result<u32, String> {
        Err("android only".into())
    }
    pub fn http_get(_: &str) -> Result<String, String> {
        Err("android only".into())
    }
    pub fn start_core(_: &str, _: bool) -> Result<String, String> {
        Err("android only".into())
    }
    pub fn stop_core(_: bool) -> Result<String, String> {
        Err("android only".into())
    }
    pub fn import_external(_: &str, _: &str, _: &str) -> Result<String, String> {
        Err("android only".into())
    }
    pub fn copy_text(_: &str) {}
    pub fn paste_text() -> String { String::new() }
    pub fn load_data(_: &str) -> String { String::new() }
    pub fn save_data(_: &str, _: &str) {}
    pub fn set_proxy(_: Method, _: &str) -> Result<(), String> {
        Err("android only".into())
    }
    pub fn launch(_: &str) -> Result<(), String> {
        Err("android only".into())
    }
}

pub use imp::*;
