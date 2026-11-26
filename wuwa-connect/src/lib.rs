mod deb_extract;
mod ini;
mod local_gateway;
mod net;
mod player_db;
mod singbox;

use ini::Change;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use serde_json::json;
use singbox::{
    build_config_json, build_local_vpn_config_json, looks_like_subscription_url, parse_profile,
    ConfigTarget,
};
use std::path::Path;

const PORT: u16 = 8080;

const PATCHES: &[(&str, &str)] = &[
    ("/Script/Engine.NetworkSettings", "bVerifyPeer"),
    ("/Script/Engine.NetworkSettings", "n.VerifyPeer"),
    ("HTTP", "bVerifyPeer"),
];

fn jstr_to_rust(env: &mut JNIEnv, s: JString) -> String {
    env.get_string(&s)
        .map(|js| js.to_string_lossy().into_owned())
        .unwrap_or_default()
}

fn rust_to_jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_parseProfile(
    mut env: JNIEnv,
    _cls: JClass,
    raw: JString,
) -> jstring {
    let input = jstr_to_rust(&mut env, raw);
    let trimmed = input.trim();

    if trimmed.is_empty() {
        let out = json!({
            "ok": false,
            "badge": "",
            "summary": "Empty",
            "displayName": "",
            "host": "",
            "port": 0,
            "primaryTcp": false,
            "autoCore": false,
            "subscription": false,
            "error": "Target is empty"
        });
        return rust_to_jstr(&mut env, &out.to_string());
    }

    if looks_like_subscription_url(trimmed) {
        let out = json!({
            "ok": true,
            "badge": "Subscription",
            "summary": "Subscription URL",
            "displayName": trimmed,
            "host": "",
            "port": 0,
            "primaryTcp": false,
            "autoCore": true,
            "subscription": true,
            "error": ""
        });
        return rust_to_jstr(&mut env, &out.to_string());
    }

    match parse_profile(trimmed, PORT) {
        Ok(p) => {
            let (host, port) = p.primary_addr.clone().unwrap_or_else(|| ("".into(), 0));
            let out = json!({
                "ok": true,
                "badge": p.proto_badge,
                "summary": p.summary,
                "displayName": p.display_name,
                "host": host,
                "port": port,
                "primaryTcp": p.primary_tcp,
                "autoCore": p.auto_core,
                "subscription": false,
                "error": ""
            });
            rust_to_jstr(&mut env, &out.to_string())
        }
        Err(e) => {
            let out = json!({
                "ok": false,
                "badge": "Invalid",
                "summary": e,
                "displayName": "",
                "host": "",
                "port": 0,
                "primaryTcp": false,
                "autoCore": false,
                "subscription": false,
                "error": e
            });
            rust_to_jstr(&mut env, &out.to_string())
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_buildConfig(
    mut env: JNIEnv,
    _cls: JClass,
    raw: JString,
    mixed_port: jint,
) -> jstring {
    let input = jstr_to_rust(&mut env, raw);
    let port = if mixed_port > 0 && mixed_port <= 65535 {
        mixed_port as u16
    } else {
        10808
    };
    let res = match parse_profile(&input, PORT) {
        Ok(mut parsed) => {
            if parsed.outbounds.len() == 1
                && parsed.endpoints.is_empty()
                && parsed.raw_full_config.is_none()
                && parsed.outbounds[0].get("type").and_then(|v| v.as_str()) == Some("http")
                && parsed.outbounds[0].get("tls").is_none()
            {
                let host = parsed.outbounds[0]
                    .get("server")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                let up_port = parsed.outbounds[0]
                    .get("server_port")
                    .and_then(|v| v.as_u64())
                    .unwrap_or(PORT as u64) as u16;
                let user = parsed.outbounds[0]
                    .get("username")
                    .and_then(|v| v.as_str())
                    .map(ToString::to_string);
                let pass = parsed.outbounds[0]
                    .get("password")
                    .and_then(|v| v.as_str())
                    .map(ToString::to_string);
                if !host.is_empty() && up_port > 0 {
                    if let Ok(relay_port) = local_gateway::start_http_relay(host, up_port, user, pass) {
                        if let Some(obj) = parsed.outbounds[0].as_object_mut() {
                            obj.insert("server".into(), json!("127.0.0.1"));
                            obj.insert("server_port".into(), json!(relay_port));
                            obj.remove("username");
                            obj.remove("password");
                        }
                    }
                }
            }
            match build_config_json(&parsed, ConfigTarget::Mixed { port }) {
                Ok(cfg) => format!("OK:{cfg}"),
                Err(e) => format!("ERR:{e}"),
            }
        }
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_patchIni(
    mut env: JNIEnv,
    _cls: JClass,
    existing: JString,
    existed: jni::sys::jboolean,
) -> jstring {
    let mut text = jstr_to_rust(&mut env, existing);
    let mut dirty = existed == 0;
    let mut logs = Vec::new();
    for (section, key) in PATCHES {
        let (new, change) = ini::set_key(&text, section, key, "False");
        let note = match change {
            Change::Ok => "ok".to_string(),
            Change::Added => {
                dirty = true;
                "added".into()
            }
            Change::Changed(old) => {
                dirty = true;
                format!("{old} -> False")
            }
        };
        text = new;
        logs.push(format!("[{section}] {key}=False ({note})"));
    }
    let out = json!({
        "dirty": dirty,
        "content": text,
        "logs": logs
    });
    rust_to_jstr(&mut env, &out.to_string())
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_checkTcp(
    mut env: JNIEnv,
    _cls: JClass,
    host: JString,
    port: jint,
) -> jstring {
    let h = jstr_to_rust(&mut env, host);
    let p = port.clamp(0, 65535) as u16;
    let res = match net::check(&h, p) {
        Ok(()) => "OK".to_string(),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_buildLocalVpnConfig(
    mut env: JNIEnv,
    _cls: JClass,
    mixed_port: jint,
    proxy_port: jint,
) -> jstring {
    let mp = if mixed_port > 0 && mixed_port <= 65535 {
        mixed_port as u16
    } else {
        10808
    };
    let pp = if proxy_port > 0 && proxy_port <= 65535 {
        proxy_port as u16
    } else {
        8080
    };
    let res = match build_local_vpn_config_json(mp, pp) {
        Ok(cfg) => format!("OK:{cfg}"),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_startLocalGateway(
    mut env: JNIEnv,
    _cls: JClass,
    host_override: JString,
    login_port: jint,
    proxy_port: jint,
    gateway_port: jint,
    state_dir: JString,
    proxy_dir: JString,
) -> jstring {
    let h = jstr_to_rust(&mut env, host_override);
    let sd = jstr_to_rust(&mut env, state_dir);
    let pd = jstr_to_rust(&mut env, proxy_dir);
    let lp = login_port.clamp(1, 65535) as u16;
    let pp = proxy_port.clamp(1, 65535) as u16;
    let gp = gateway_port.clamp(1, 65535) as u16;
    let res = match local_gateway::start_gateway(&h, lp, pp, gp, &sd, &pd) {
        Ok(()) => format!("OK:Gateway listening :{lp} / :{pp}"),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_stopLocalGateway(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    local_gateway::stop_gateway();
    rust_to_jstr(&mut env, "OK")
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_stopHttpProxyRelay(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    local_gateway::stop_http_relay();
    rust_to_jstr(&mut env, "OK")
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_resolveDebPackages(
    mut env: JNIEnv,
    _cls: JClass,
    packages_index: JString,
    roots_csv: JString,
) -> jstring {
    let idx = jstr_to_rust(&mut env, packages_index);
    let roots_s = jstr_to_rust(&mut env, roots_csv);
    let roots: Vec<&str> = roots_s
        .split(',')
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .collect();
    let list = deb_extract::parse_apt_packages_and_resolve(&idx, &roots);
    rust_to_jstr(&mut env, &serde_json::Value::Array(list).to_string())
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_extractDeb(
    mut env: JNIEnv,
    _cls: JClass,
    deb_path: JString,
    dest_dir: JString,
    runtime_prefix: JString,
) -> jstring {
    let dp = jstr_to_rust(&mut env, deb_path);
    let dd = jstr_to_rust(&mut env, dest_dir);
    let rp = jstr_to_rust(&mut env, runtime_prefix);
    let res = match deb_extract::extract_deb_to_dir(Path::new(&dp), Path::new(&dd), &rp) {
        Ok(cnt) => format!("OK:{cnt}"),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_sha256File(
    mut env: JNIEnv,
    _cls: JClass,
    file_path: JString,
) -> jstring {
    let fp = jstr_to_rust(&mut env, file_path);
    let res = match deb_extract::sha256_file_hex(Path::new(&fp)) {
        Ok(h) => format!("OK:{h}"),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_playerDbList(
    mut env: JNIEnv,
    _cls: JClass,
    db_path: JString,
) -> jstring {
    let dp = jstr_to_rust(&mut env, db_path);
    let out = player_db::list_players(Path::new(&dp));
    rust_to_jstr(&mut env, &out.to_string())
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_playerDbUpsert(
    mut env: JNIEnv,
    _cls: JClass,
    db_path: JString,
    player_json: JString,
) -> jstring {
    let dp = jstr_to_rust(&mut env, db_path);
    let pj = jstr_to_rust(&mut env, player_json);
    let res = match player_db::upsert_player(Path::new(&dp), &pj) {
        Ok(pid) => format!("OK:{pid}"),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_playerDbDelete(
    mut env: JNIEnv,
    _cls: JClass,
    db_path: JString,
    player_id: jlong,
) -> jstring {
    let dp = jstr_to_rust(&mut env, db_path);
    let res = match player_db::delete_player(Path::new(&dp), player_id) {
        Ok(()) => "OK".to_string(),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}

#[no_mangle]
pub extern "system" fn Java_id_local_wuwaconnect_RustCore_playerDbCopy(
    mut env: JNIEnv,
    _cls: JClass,
    src_path: JString,
    dst_path: JString,
) -> jstring {
    let sp = jstr_to_rust(&mut env, src_path);
    let dp = jstr_to_rust(&mut env, dst_path);
    let res = match player_db::checkpoint_and_copy(Path::new(&sp), Path::new(&dp)) {
        Ok(()) => "OK".to_string(),
        Err(e) => format!("ERR:{e}"),
    };
    rust_to_jstr(&mut env, &res)
}
