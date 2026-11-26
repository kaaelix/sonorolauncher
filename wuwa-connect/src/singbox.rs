use base64::{engine::general_purpose::{STANDARD, STANDARD_NO_PAD, URL_SAFE, URL_SAFE_NO_PAD}, Engine as _};
use serde_json::{json, Map, Value};
use std::collections::HashMap;
use url::Url;

#[allow(dead_code)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ConnectMode {
    DirectProxy = 0,
    SingboxMixed = 1,
    SingboxTun = 2,
    SingboxExternal = 3,
}

#[allow(dead_code)]
impl ConnectMode {
    pub fn from_i32(v: i32) -> Self {
        match v {
            1 => Self::SingboxMixed,
            2 => Self::SingboxTun,
            3 => Self::SingboxExternal,
            _ => Self::DirectProxy,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::DirectProxy => "Direct Proxy",
            Self::SingboxMixed => "sing-box Core (Proxy)",
            Self::SingboxTun => "sing-box Core (Root TUN)",
            Self::SingboxExternal => "sing-box External / Box",
        }
    }
}

#[allow(dead_code)]
#[derive(Clone, Debug)]
pub struct ParsedProfile {
    pub summary: String,
    pub proto_badge: String,
    pub display_name: String,
    pub primary_addr: Option<(String, u16)>,
    pub primary_tcp: bool,
    pub auto_core: bool,
    pub outbounds: Vec<Value>,
    pub endpoints: Vec<Value>,
    pub raw_full_config: Option<Value>,
    pub primary_uri: Option<String>,
}

#[allow(dead_code)]
pub enum ConfigTarget<'a> {
    Mixed { port: u16 },
    Tun { per_app_pkg: Option<&'a str> },
    External { per_app_pkg: Option<&'a str> },
}

pub fn pct_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let (Some(h), Some(l)) = (
                (bytes[i + 1] as char).to_digit(16),
                (bytes[i + 2] as char).to_digit(16),
            ) {
                out.push(((h << 4) | l) as u8);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn decode_b64_flex(s: &str) -> Result<Vec<u8>, String> {
    let cleaned: String = s.trim().chars().filter(|c| !c.is_ascii_whitespace()).collect();
    for engine in [&STANDARD, &STANDARD_NO_PAD, &URL_SAFE, &URL_SAFE_NO_PAD] {
        if let Ok(bytes) = engine.decode(&cleaned) {
            return Ok(bytes);
        }
    }
    // Try adding padding if length % 4 != 0
    let rem = cleaned.len() % 4;
    if rem != 0 {
        let padded = format!("{cleaned}{}", "=".repeat(4 - rem));
        for engine in [&STANDARD, &URL_SAFE] {
            if let Ok(bytes) = engine.decode(&padded) {
                return Ok(bytes);
            }
        }
    }
    Err("Invalid base64 payload".into())
}

pub fn looks_like_subscription_url(raw: &str) -> bool {
    let s = raw.trim();
    if !(s.starts_with("http://") || s.starts_with("https://")) {
        return false;
    }
    if let Ok(u) = Url::parse(s) {
        let has_path = u.path().len() > 1;
        let has_query = u.query().is_some();
        let has_auth = !u.username().is_empty();
        // A plain proxy URL usually has no path/query (or has user:pass@host:port)
        !has_auth && (has_path || has_query || s.starts_with("https://"))
    } else {
        false
    }
}

pub fn parse_host_port_str(s: &str, default_port: u16) -> Result<(String, u16), String> {
    let trimmed = s.trim();
    if trimmed.is_empty() {
        return Err("Empty address".into());
    }
    if trimmed.starts_with('[') {
        if let Some((host, rest)) = trimmed.split_once(']') {
            let h = host.trim_start_matches('[').to_string();
            let p = rest
                .strip_prefix(':')
                .and_then(|p| p.parse::<u16>().ok())
                .unwrap_or(default_port);
            return Ok((h, p));
        }
    }
    if let Some((h, p)) = trimmed.rsplit_once(':') {
        if !h.contains(':') {
            let port = p
                .parse::<u16>()
                .map_err(|_| format!("Invalid port in '{trimmed}'"))?;
            return Ok((h.to_string(), port));
        }
    }
    Ok((trimmed.to_string(), default_port))
}

fn extract_fragment_name(raw: &str) -> Option<String> {
    raw.rsplit_once('#')
        .map(|(_, f)| pct_decode(f).trim().to_string())
        .filter(|s| !s.is_empty())
}

fn is_tcp_outbound(ob: &Value) -> bool {
    let ty = ob.get("type").and_then(|v| v.as_str()).unwrap_or("");
    matches!(ty, "http" | "socks" | "vless" | "vmess" | "trojan" | "shadowsocks")
}

fn proto_label(ob: &Value) -> String {
    match ob.get("type").and_then(|v| v.as_str()).unwrap_or("") {
        "http" => {
            if ob.get("tls").and_then(|t| t.get("enabled")).and_then(|v| v.as_bool()) == Some(true) {
                "HTTPS".into()
            } else {
                "HTTP".into()
            }
        }
        "socks" => format!("SOCKS{}", ob.get("version").and_then(|v| v.as_str()).unwrap_or("5")),
        "vless" => {
            if ob.get("tls").and_then(|t| t.get("reality")).is_some() {
                "VLESS · Reality".into()
            } else if ob.get("tls").is_some() {
                "VLESS · TLS".into()
            } else {
                "VLESS".into()
            }
        }
        "vmess" => "VMess".into(),
        "trojan" => "Trojan".into(),
        "shadowsocks" => "Shadowsocks".into(),
        "hysteria" => "Hysteria".into(),
        "hysteria2" => "Hysteria2".into(),
        "tuic" => "TUIC".into(),
        "wireguard" => "WireGuard".into(),
        other if !other.is_empty() => other.to_uppercase(),
        _ => "sing-box".into(),
    }
}

pub fn parse_profile(raw: &str, default_http_port: u16) -> Result<ParsedProfile, String> {
    let s = raw.trim();
    if s.is_empty() {
        return Err("Target/config is empty".into());
    }

    // Raw JSON (full sing-box config or single outbound)
    if s.starts_with('{') {
        let val: Value =
            serde_json::from_str(s).map_err(|e| format!("Invalid JSON config: {e}"))?;
        if let Some(arr) = val.get("outbounds").and_then(|v| v.as_array()) {
            let primary_addr = extract_first_server(arr).or_else(|| {
                val.get("endpoints")
                    .and_then(|v| v.as_array())
                    .and_then(|e| extract_first_endpoint_server(e))
            });
            let count = arr.len();
            let badge = arr
                .iter()
                .find(|o| !matches!(o.get("type").and_then(|t| t.as_str()), Some("direct" | "block" | "dns" | "selector" | "urltest")))
                .map(proto_label)
                .unwrap_or_else(|| "sing-box JSON".into());
            let display_name = match &primary_addr {
                Some((h, p)) => format!("{h}:{p}"),
                None => format!("sing-box JSON ({count})"),
            };
            return Ok(ParsedProfile {
                summary: format!("{badge} ({count} outbounds)"),
                proto_badge: badge,
                display_name,
                primary_addr,
                primary_tcp: false,
                auto_core: true,
                outbounds: arr.clone(),
                endpoints: val
                    .get("endpoints")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default(),
                raw_full_config: Some(val),
                primary_uri: None,
            });
        }
        if let Some(ty) = val.get("type").and_then(|v| v.as_str()) {
            let mut ob = val.clone();
            if ob.get("tag").is_none() {
                ob["tag"] = json!("proxy");
            }
            let addr = ob
                .get("server")
                .and_then(|v| v.as_str())
                .and_then(|h| {
                    ob.get("server_port")
                        .and_then(|p| p.as_u64())
                        .map(|p| (h.to_string(), p as u16))
                });
            let badge = proto_label(&ob);
            let display_name = match &addr {
                Some((h, p)) => format!("{h}:{p}"),
                None => ty.to_uppercase(),
            };
            let summary = match &addr {
                Some((h, p)) => format!("{badge} · {h}:{p}"),
                None => format!("{badge} JSON"),
            };
            let tcp = is_tcp_outbound(&ob);
            return Ok(ParsedProfile {
                summary,
                proto_badge: badge,
                display_name,
                primary_addr: addr,
                primary_tcp: tcp,
                auto_core: true,
                outbounds: vec![ob],
                endpoints: vec![],
                raw_full_config: None,
                primary_uri: None,
            });
        }
        return Err("JSON must contain 'outbounds' array or outbound 'type'".into());
    }

    // Decode base64 subscription content if present
    let text = if !s.contains("://") && !s.contains('\n') && s.len() > 24 && !s.contains('.') {
        match decode_b64_flex(s).and_then(|b| String::from_utf8(b).map_err(|e| e.to_string())) {
            Ok(decoded) if decoded.contains("://") => decoded,
            _ => s.to_string(),
        }
    } else if !s.contains("://") && !s.contains('\n') && s.len() > 40 {
        match decode_b64_flex(s).and_then(|b| String::from_utf8(b).map_err(|e| e.to_string())) {
            Ok(decoded) if decoded.contains("://") => decoded,
            _ => s.to_string(),
        }
    } else {
        s.to_string()
    };

    // Multi-line or single URI / host:port
    let lines: Vec<&str> = text
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .collect();

    if lines.len() > 1 {
        let mut outbounds = Vec::new();
        let mut endpoints = Vec::new();
        let mut first_addr = None;
        let mut first_uri = None;
        for (idx, line) in lines.iter().enumerate() {
            if let Ok(item) = parse_single_uri_or_host(line, default_http_port, &format!("node-{}", idx + 1)) {
                if first_addr.is_none() {
                    first_addr = item.primary_addr.clone();
                    first_uri = item.primary_uri.clone();
                }
                outbounds.extend(item.outbounds);
                endpoints.extend(item.endpoints);
            }
        }
        if outbounds.is_empty() && endpoints.is_empty() {
            return Err("No valid proxy/sing-box entries found in list".into());
        }
        let total = outbounds.len() + endpoints.len();
        return Ok(ParsedProfile {
            summary: format!("sing-box · {total} nodes"),
            proto_badge: format!("List ({total})"),
            display_name: format!("{total} nodes"),
            primary_addr: first_addr,
            primary_tcp: false,
            auto_core: true,
            outbounds,
            endpoints,
            raw_full_config: None,
            primary_uri: first_uri,
        });
    }

    parse_single_uri_or_host(lines[0], default_http_port, "proxy")
}

fn extract_first_server(arr: &[Value]) -> Option<(String, u16)> {
    for ob in arr {
        if let (Some(h), Some(p)) = (
            ob.get("server").and_then(|v| v.as_str()),
            ob.get("server_port").and_then(|v| v.as_u64()),
        ) {
            return Some((h.to_string(), p as u16));
        }
    }
    None
}

fn extract_first_endpoint_server(arr: &[Value]) -> Option<(String, u16)> {
    for ep in arr {
        if let Some(peers) = ep.get("peers").and_then(|v| v.as_array()) {
            for peer in peers {
                if let (Some(h), Some(p)) = (
                    peer.get("address").and_then(|v| v.as_str()),
                    peer.get("port").and_then(|v| v.as_u64()),
                ) {
                    return Some((h.to_string(), p as u16));
                }
            }
        }
    }
    None
}

fn parse_single_uri_or_host(
    raw: &str,
    default_http_port: u16,
    tag: &str,
) -> Result<ParsedProfile, String> {
    if !raw.contains("://") {
        let (host, port) = parse_host_port_str(raw, default_http_port)?;
        let ob = json!({
            "type": "http",
            "tag": tag,
            "server": host,
            "server_port": port
        });
        let display_name = format!("{host}:{port}");
        return Ok(ParsedProfile {
            summary: format!("HTTP · {display_name}"),
            proto_badge: "HTTP".into(),
            display_name,
            primary_addr: Some((host, port)),
            primary_tcp: true,
            auto_core: false,
            outbounds: vec![ob],
            endpoints: vec![],
            raw_full_config: None,
            primary_uri: Some(format!("http://{raw}")),
        });
    }

    let scheme = raw
        .split_once("://")
        .map(|(s, _)| s.to_ascii_lowercase())
        .ok_or_else(|| "Invalid URI".to_string())?;

    match scheme.as_str() {
        "http" | "https" => parse_http(&scheme, raw, tag),
        "socks" | "socks5" | "socks4" | "socks4a" => parse_socks(&scheme, raw, tag),
        "vless" => parse_vless(raw, tag),
        "vmess" => parse_vmess(raw, tag),
        "trojan" => parse_trojan(raw, tag),
        "ss" => parse_shadowsocks(raw, tag),
        "hysteria" | "hy" => parse_hysteria_v1(raw, tag),
        "hysteria2" | "hy2" => parse_hysteria2(raw, tag),
        "hyperia" => {
            let rest = raw.split_once("://").map(|(_, r)| r).unwrap_or("");
            if rest.contains('@') && !rest.contains("auth=") {
                parse_hysteria2(&format!("hysteria2://{rest}"), tag)
            } else {
                parse_hysteria_v1(&format!("hysteria://{rest}"), tag)
            }
        }
        "tuic" => parse_tuic(raw, tag),
        "wg" | "wireguard" => parse_wireguard(raw, tag),
        other => Err(format!("Unsupported scheme: {other}://")),
    }
}

fn query_map(u: &Url) -> HashMap<String, String> {
    u.query_pairs()
        .map(|(k, v)| (k.to_ascii_lowercase(), v.into_owned()))
        .collect()
}

fn is_truthy(q: &HashMap<String, String>, keys: &[&str]) -> bool {
    for k in keys {
        if let Some(v) = q.get(*k) {
            if matches!(v.to_ascii_lowercase().as_str(), "1" | "true" | "yes") {
                return true;
            }
        }
    }
    false
}

fn build_tls_and_transport(
    ob: &mut Map<String, Value>,
    q: &HashMap<String, String>,
    default_tls: bool,
    host: &str,
) {
    let sec = q
        .get("security")
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_else(|| if default_tls { "tls".into() } else { "none".into() });

    if sec == "tls" || sec == "reality" {
        let mut tls = Map::new();
        tls.insert("enabled".into(), json!(true));
        let sni = q
            .get("sni")
            .or_else(|| q.get("peer"))
            .filter(|s| !s.is_empty())
            .cloned()
            .unwrap_or_else(|| host.to_string());
        tls.insert("server_name".into(), json!(sni));

        if is_truthy(q, &["allowinsecure", "insecure", "allow_insecure"]) {
            tls.insert("insecure".into(), json!(true));
        }
        if let Some(alpn) = q.get("alpn").filter(|s| !s.is_empty()) {
            let list: Vec<&str> = alpn.split(',').map(str::trim).filter(|s| !s.is_empty()).collect();
            if !list.is_empty() {
                tls.insert("alpn".into(), json!(list));
            }
        }
        if let Some(fp) = q.get("fp").filter(|s| !s.is_empty()) {
            tls.insert("utls".into(), json!({ "enabled": true, "fingerprint": fp }));
        }
        if sec == "reality" {
            let mut reality = Map::new();
            reality.insert("enabled".into(), json!(true));
            if let Some(pbk) = q.get("pbk") {
                reality.insert("public_key".into(), json!(pbk));
            }
            if let Some(sid) = q.get("sid") {
                reality.insert("short_id".into(), json!(sid));
            }
            tls.insert("reality".into(), Value::Object(reality));
            if !tls.contains_key("utls") {
                tls.insert("utls".into(), json!({ "enabled": true, "fingerprint": "chrome" }));
            }
        }
        ob.insert("tls".into(), Value::Object(tls));
    }

    let net_ty = q
        .get("type")
        .or_else(|| q.get("net"))
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_default();

    match net_ty.as_str() {
        "ws" => {
            let mut tr = Map::new();
            tr.insert("type".into(), json!("ws"));
            if let Some(p) = q.get("path").filter(|s| !s.is_empty()) {
                tr.insert("path".into(), json!(p));
            }
            if let Some(h) = q.get("host").filter(|s| !s.is_empty()) {
                tr.insert("headers".into(), json!({ "Host": h }));
            }
            ob.insert("transport".into(), Value::Object(tr));
        }
        "grpc" => {
            let mut tr = Map::new();
            tr.insert("type".into(), json!("grpc"));
            if let Some(sn) = q.get("servicename").or_else(|| q.get("service_name")) {
                tr.insert("service_name".into(), json!(sn));
            }
            ob.insert("transport".into(), Value::Object(tr));
        }
        "httpupgrade" => {
            let mut tr = Map::new();
            tr.insert("type".into(), json!("httpupgrade"));
            if let Some(p) = q.get("path").filter(|s| !s.is_empty()) {
                tr.insert("path".into(), json!(p));
            }
            if let Some(h) = q.get("host").filter(|s| !s.is_empty()) {
                tr.insert("host".into(), json!(h));
            }
            ob.insert("transport".into(), Value::Object(tr));
        }
        "http" | "h2" => {
            let mut tr = Map::new();
            tr.insert("type".into(), json!("http"));
            if let Some(p) = q.get("path").filter(|s| !s.is_empty()) {
                tr.insert("path".into(), json!(p));
            }
            if let Some(h) = q.get("host").filter(|s| !s.is_empty()) {
                let hosts: Vec<&str> = h.split(',').map(str::trim).filter(|s| !s.is_empty()).collect();
                tr.insert("host".into(), json!(hosts));
            }
            ob.insert("transport".into(), Value::Object(tr));
        }
        _ => {}
    }
}

fn parse_http(scheme: &str, raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let u = Url::parse(raw).map_err(|e| format!("Invalid HTTP proxy URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let default_port = if scheme == "https" { 443 } else { 8080 };
    let port = u.port().unwrap_or(default_port);
    let q = query_map(&u);
    let has_auth = !u.username().is_empty();

    let mut ob = Map::new();
    ob.insert("type".into(), json!("http"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    if has_auth {
        ob.insert("username".into(), json!(pct_decode(u.username())));
    }
    if let Some(pw) = u.password() {
        ob.insert("password".into(), json!(pct_decode(pw)));
    }
    if scheme == "https" {
        build_tls_and_transport(&mut ob, &q, true, &host);
    }
    let badge = scheme.to_ascii_uppercase();
    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("{badge} · {host}:{port}"),
        proto_badge: badge,
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: scheme == "https" || has_auth,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_socks(scheme: &str, raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let u = Url::parse(raw).map_err(|e| format!("Invalid SOCKS URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(1080);
    let version = match scheme {
        "socks4" => "4",
        "socks4a" => "4a",
        _ => "5",
    };

    let mut ob = Map::new();
    ob.insert("type".into(), json!("socks"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    ob.insert("version".into(), json!(version));

    if !u.username().is_empty() {
        let user_raw = pct_decode(u.username());
        if u.password().is_none() {
            if let Ok(decoded) = decode_b64_flex(&user_raw)
                .and_then(|b| String::from_utf8(b).map_err(|e| e.to_string()))
            {
                if let Some((user, pass)) = decoded.split_once(':') {
                    ob.insert("username".into(), json!(user));
                    ob.insert("password".into(), json!(pass));
                } else {
                    ob.insert("username".into(), json!(user_raw));
                }
            } else {
                ob.insert("username".into(), json!(user_raw));
            }
        } else {
            ob.insert("username".into(), json!(user_raw));
            if let Some(pw) = u.password() {
                ob.insert("password".into(), json!(pct_decode(pw)));
            }
        }
    }

    let badge = format!("SOCKS{version}");
    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("{badge} · {host}:{port}"),
        proto_badge: badge,
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_vless(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let u = Url::parse(raw).map_err(|e| format!("Invalid VLESS URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let uuid = pct_decode(u.username());
    if uuid.is_empty() {
        return Err("VLESS missing UUID".into());
    }
    let q = query_map(&u);

    let mut ob = Map::new();
    ob.insert("type".into(), json!("vless"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    ob.insert("uuid".into(), json!(uuid));
    if let Some(flow) = q.get("flow").filter(|s| !s.is_empty()) {
        ob.insert("flow".into(), json!(flow));
    }
    build_tls_and_transport(&mut ob, &q, false, &host);

    let sec = q.get("security").map(|s| s.to_ascii_lowercase()).unwrap_or_default();
    let badge = match sec.as_str() {
        "reality" => "VLESS · Reality".to_string(),
        "tls" => "VLESS · TLS".to_string(),
        _ => "VLESS".to_string(),
    };
    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));

    Ok(ParsedProfile {
        summary: format!("{badge} · {host}:{port}"),
        proto_badge: badge,
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_vmess(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let rest = raw.strip_prefix("vmess://").ok_or("Invalid VMess URI")?;
    let rest_no_frag = rest.split('#').next().unwrap_or(rest).trim();

    // Try classic base64 JSON format first
    if !rest_no_frag.contains('@') {
        let bytes = decode_b64_flex(rest_no_frag)?;
        let v: Value = serde_json::from_slice(&bytes)
            .map_err(|e| format!("Invalid VMess JSON payload: {e}"))?;
        let host = v
            .get("add")
            .and_then(|x| x.as_str())
            .ok_or("VMess missing 'add'")?
            .to_string();
        let port = match v.get("port") {
            Some(Value::Number(n)) => n.as_u64().unwrap_or(443) as u16,
            Some(Value::String(s)) => s.parse::<u16>().unwrap_or(443),
            _ => 443,
        };
        let uuid = v
            .get("id")
            .and_then(|x| x.as_str())
            .ok_or("VMess missing 'id'")?
            .to_string();
        let alter_id = match v.get("aid") {
            Some(Value::Number(n)) => n.as_u64().unwrap_or(0),
            Some(Value::String(s)) => s.parse::<u64>().unwrap_or(0),
            _ => 0,
        };
        let security = v
            .get("scy")
            .or_else(|| v.get("cipher"))
            .and_then(|x| x.as_str())
            .filter(|s| !s.is_empty())
            .unwrap_or("auto");

        let mut ob = Map::new();
        ob.insert("type".into(), json!("vmess"));
        ob.insert("tag".into(), json!(tag));
        ob.insert("server".into(), json!(host));
        ob.insert("server_port".into(), json!(port));
        ob.insert("uuid".into(), json!(uuid));
        ob.insert("alter_id".into(), json!(alter_id));
        ob.insert("security".into(), json!(security));

        let mut q = HashMap::new();
        if let Some(net) = v.get("net").and_then(|x| x.as_str()) {
            q.insert("type".into(), net.to_string());
        }
        if let Some(h) = v.get("host").and_then(|x| x.as_str()) {
            q.insert("host".into(), h.to_string());
        }
        if let Some(p) = v.get("path").and_then(|x| x.as_str()) {
            q.insert("path".into(), p.to_string());
            q.insert("servicename".into(), p.to_string());
        }
        let tls_val = v.get("tls").and_then(|x| x.as_str()).unwrap_or("");
        if tls_val.eq_ignore_ascii_case("tls") {
            q.insert("security".into(), "tls".into());
        }
        if let Some(sni) = v.get("sni").and_then(|x| x.as_str()).filter(|s| !s.is_empty()) {
            q.insert("sni".into(), sni.to_string());
        }
        if let Some(fp) = v.get("fp").and_then(|x| x.as_str()).filter(|s| !s.is_empty()) {
            q.insert("fp".into(), fp.to_string());
        }
        if let Some(alpn) = v.get("alpn").and_then(|x| x.as_str()).filter(|s| !s.is_empty()) {
            q.insert("alpn".into(), alpn.to_string());
        }
        build_tls_and_transport(&mut ob, &q, false, &host);

        let display_name = v
            .get("ps")
            .and_then(|x| x.as_str())
            .filter(|s| !s.is_empty())
            .map(ToString::to_string)
            .unwrap_or_else(|| format!("{host}:{port}"));

        return Ok(ParsedProfile {
            summary: format!("VMess · {host}:{port}"),
            proto_badge: "VMess".into(),
            display_name,
            primary_addr: Some((host, port)),
            primary_tcp: true,
            auto_core: true,
            outbounds: vec![Value::Object(ob)],
            endpoints: vec![],
            raw_full_config: None,
            primary_uri: Some(raw.to_string()),
        });
    }

    // Standard URI format vmess://uuid@host:port?...
    let u = Url::parse(raw).map_err(|e| format!("Invalid VMess URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let uuid = pct_decode(u.username());
    let q = query_map(&u);
    let mut ob = Map::new();
    ob.insert("type".into(), json!("vmess"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    ob.insert("uuid".into(), json!(uuid));
    ob.insert("security".into(), json!(q.get("encryption").map(String::as_str).unwrap_or("auto")));
    build_tls_and_transport(&mut ob, &q, false, &host);
    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("VMess · {host}:{port}"),
        proto_badge: "VMess".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_trojan(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let u = Url::parse(raw).map_err(|e| format!("Invalid Trojan URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let password = pct_decode(u.username());
    if password.is_empty() {
        return Err("Trojan missing password".into());
    }
    let q = query_map(&u);
    let mut ob = Map::new();
    ob.insert("type".into(), json!("trojan"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    ob.insert("password".into(), json!(password));
    build_tls_and_transport(&mut ob, &q, true, &host);

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("Trojan · {host}:{port}"),
        proto_badge: "Trojan".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_shadowsocks(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let body = raw
        .strip_prefix("ss://")
        .ok_or("Invalid ss:// URI")?
        .split('#')
        .next()
        .unwrap_or("")
        .split('?')
        .next()
        .unwrap_or("")
        .trim();

    let (creds, host_port) = if let Some((left, right)) = body.split_once('@') {
        let cred_str = if left.contains(':') {
            pct_decode(left)
        } else {
            String::from_utf8(decode_b64_flex(left)?).map_err(|e| e.to_string())?
        };
        (cred_str, right.to_string())
    } else {
        let decoded = String::from_utf8(decode_b64_flex(body)?).map_err(|e| e.to_string())?;
        let (left, right) = decoded
            .rsplit_once('@')
            .ok_or("Invalid Shadowsocks URI")?;
        (left.to_string(), right.to_string())
    };

    let (method, password) = creds
        .split_once(':')
        .ok_or("Shadowsocks missing method:password")?;
    let (host, port) = parse_host_port_str(&host_port, 8388)?;

    let ob = json!({
        "type": "shadowsocks",
        "tag": tag,
        "server": host,
        "server_port": port,
        "method": method,
        "password": password
    });

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("Shadowsocks ({method}) · {host}:{port}"),
        proto_badge: "Shadowsocks".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: true,
        auto_core: true,
        outbounds: vec![ob],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_hysteria_v1(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let normalized = if let Some(rest) = raw.strip_prefix("hy://") {
        format!("hysteria://{rest}")
    } else {
        raw.to_string()
    };
    let u = Url::parse(&normalized).map_err(|e| format!("Invalid Hysteria URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let q = query_map(&u);

    let mut ob = Map::new();
    ob.insert("type".into(), json!("hysteria"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));

    let up = q.get("upmbps").and_then(|v| v.parse::<u64>().ok()).unwrap_or(100);
    let down = q.get("downmbps").and_then(|v| v.parse::<u64>().ok()).unwrap_or(100);
    ob.insert("up_mbps".into(), json!(up));
    ob.insert("down_mbps".into(), json!(down));

    if let Some(auth) = q.get("auth").filter(|s| !s.is_empty()) {
        ob.insert("auth_str".into(), json!(auth));
    } else if !u.username().is_empty() {
        ob.insert("auth_str".into(), json!(pct_decode(u.username())));
    }
    if let Some(obfs) = q.get("obfsparam").or_else(|| q.get("obfs")).filter(|s| !s.is_empty()) {
        ob.insert("obfs".into(), json!(obfs));
    }
    build_tls_and_transport(&mut ob, &q, true, &host);

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("Hysteria · {host}:{port}"),
        proto_badge: "Hysteria".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: false,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_hysteria2(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let normalized = if let Some(rest) = raw.strip_prefix("hy2://") {
        format!("hysteria2://{rest}")
    } else {
        raw.to_string()
    };
    let u = Url::parse(&normalized).map_err(|e| format!("Invalid Hysteria2 URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let q = query_map(&u);

    let mut ob = Map::new();
    ob.insert("type".into(), json!("hysteria2"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));

    if let Some(mport) = q.get("mport").or_else(|| q.get("ports")).filter(|s| !s.is_empty()) {
        let ranges: Vec<String> = mport
            .split(',')
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(|r| {
                if let Some((a, b)) = r.split_once('-') {
                    format!("{a}:{b}")
                } else if r.contains(':') {
                    r.to_string()
                } else {
                    format!("{r}:{r}")
                }
            })
            .collect();
        if !ranges.is_empty() {
            ob.insert("server_ports".into(), json!(ranges));
        } else {
            ob.insert("server_port".into(), json!(port));
        }
    } else {
        ob.insert("server_port".into(), json!(port));
    }

    if !u.username().is_empty() {
        let user = pct_decode(u.username());
        let password = match u.password() {
            Some(p) => format!("{user}:{}", pct_decode(p)),
            None => user,
        };
        ob.insert("password".into(), json!(password));
    }
    if let Some(up) = q.get("upmbps").and_then(|v| v.parse::<u64>().ok()) {
        ob.insert("up_mbps".into(), json!(up));
    }
    if let Some(down) = q.get("downmbps").and_then(|v| v.parse::<u64>().ok()) {
        ob.insert("down_mbps".into(), json!(down));
    }
    if let Some(obfs_ty) = q.get("obfs").filter(|s| !s.is_empty() && s.as_str() != "none") {
        let mut obfs = Map::new();
        obfs.insert("type".into(), json!(obfs_ty));
        if let Some(pw) = q.get("obfs-password") {
            obfs.insert("password".into(), json!(pw));
        }
        ob.insert("obfs".into(), Value::Object(obfs));
    }
    build_tls_and_transport(&mut ob, &q, true, &host);

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("Hysteria2 · {host}:{port}"),
        proto_badge: "Hysteria2".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: false,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_tuic(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let u = Url::parse(raw).map_err(|e| format!("Invalid TUIC URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(443);
    let uuid = pct_decode(u.username());
    let password = u.password().map(pct_decode).unwrap_or_default();
    let q = query_map(&u);

    let mut ob = Map::new();
    ob.insert("type".into(), json!("tuic"));
    ob.insert("tag".into(), json!(tag));
    ob.insert("server".into(), json!(host));
    ob.insert("server_port".into(), json!(port));
    ob.insert("uuid".into(), json!(uuid));
    if !password.is_empty() {
        ob.insert("password".into(), json!(password));
    }
    if let Some(cc) = q.get("congestion_control").filter(|s| !s.is_empty()) {
        ob.insert("congestion_control".into(), json!(cc));
    }
    if let Some(udp) = q.get("udp_relay_mode").filter(|s| !s.is_empty()) {
        ob.insert("udp_relay_mode".into(), json!(udp));
    }
    build_tls_and_transport(&mut ob, &q, true, &host);

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("TUIC · {host}:{port}"),
        proto_badge: "TUIC".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: false,
        auto_core: true,
        outbounds: vec![Value::Object(ob)],
        endpoints: vec![],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

fn parse_wireguard(raw: &str, tag: &str) -> Result<ParsedProfile, String> {
    let normalized = if raw.starts_with("wg://") {
        format!("wireguard://{}", &raw[5..])
    } else {
        raw.to_string()
    };
    let u = Url::parse(&normalized).map_err(|e| format!("Invalid WireGuard URI: {e}"))?;
    let host = u.host_str().ok_or("Missing host")?.to_string();
    let port = u.port().unwrap_or(51820);
    let private_key = pct_decode(u.username());
    let q = query_map(&u);

    let addr_list: Vec<String> = q
        .get("address")
        .map(|s| s.split(',').map(|x| x.trim().to_string()).filter(|x| !x.is_empty()).collect())
        .filter(|v: &Vec<String>| !v.is_empty())
        .unwrap_or_else(|| vec!["10.0.0.2/32".into()]);

    let mut peer = Map::new();
    peer.insert("address".into(), json!(host));
    peer.insert("port".into(), json!(port));
    if let Some(pk) = q.get("publickey").or_else(|| q.get("public_key")) {
        peer.insert("public_key".into(), json!(pk));
    }
    peer.insert("allowed_ips".into(), json!(["0.0.0.0/0", "::/0"]));
    if let Some(res) = q.get("reserved").filter(|s| !s.is_empty()) {
        let nums: Vec<u8> = res
            .split(',')
            .filter_map(|x| x.trim().parse::<u8>().ok())
            .collect();
        if !nums.is_empty() {
            peer.insert("reserved".into(), json!(nums));
        }
    }

    let mut ep = Map::new();
    ep.insert("type".into(), json!("wireguard"));
    ep.insert("tag".into(), json!(tag));
    ep.insert("address".into(), json!(addr_list));
    ep.insert("private_key".into(), json!(private_key));
    if let Some(mtu) = q.get("mtu").and_then(|v| v.parse::<u64>().ok()) {
        ep.insert("mtu".into(), json!(mtu));
    }
    ep.insert("peers".into(), json!([Value::Object(peer)]));

    let display_name = extract_fragment_name(raw).unwrap_or_else(|| format!("{host}:{port}"));
    Ok(ParsedProfile {
        summary: format!("WireGuard · {host}:{port}"),
        proto_badge: "WireGuard".into(),
        display_name,
        primary_addr: Some((host, port)),
        primary_tcp: false,
        auto_core: true,
        outbounds: vec![],
        endpoints: vec![Value::Object(ep)],
        raw_full_config: None,
        primary_uri: Some(raw.to_string()),
    })
}

pub fn build_config_json(parsed: &ParsedProfile, target: ConfigTarget<'_>) -> Result<String, String> {
    let inbound = match &target {
        ConfigTarget::Mixed { port } => json!({
            "type": "mixed",
            "tag": "mixed-in",
            "listen": "127.0.0.1",
            "listen_port": port
        }),
        ConfigTarget::Tun { per_app_pkg } | ConfigTarget::External { per_app_pkg } => {
            let mut tun = Map::new();
            tun.insert("type".into(), json!("tun"));
            tun.insert("tag".into(), json!("tun-in"));
            tun.insert("address".into(), json!(["172.19.0.1/30"]));
            tun.insert("auto_route".into(), json!(true));
            tun.insert("strict_route".into(), json!(true));
            tun.insert("stack".into(), json!("system"));
            if let Some(pkg) = per_app_pkg {
                tun.insert("include_package".into(), json!([pkg]));
            }
            Value::Object(tun)
        }
    };

    if let Some(full) = &parsed.raw_full_config {
        let mut cfg = full.clone();
        cfg["inbounds"] = json!([inbound]);
        return serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string());
    }

    let mut outbounds = Vec::new();
    let mut node_tags = Vec::new();
    for ob in &parsed.outbounds {
        if let Some(t) = ob.get("tag").and_then(|v| v.as_str()) {
            node_tags.push(t.to_string());
        }
        outbounds.push(ob.clone());
    }
    for ep in &parsed.endpoints {
        if let Some(t) = ep.get("tag").and_then(|v| v.as_str()) {
            node_tags.push(t.to_string());
        }
    }

    let final_tag = if node_tags.len() > 1 {
        outbounds.insert(
            0,
            json!({
                "type": "selector",
                "tag": "proxy",
                "outbounds": node_tags,
                "default": node_tags[0]
            }),
        );
        "proxy".to_string()
    } else {
        node_tags.into_iter().next().unwrap_or_else(|| "proxy".into())
    };

    let is_http_only = outbounds.len() == 1
        && outbounds[0].get("type").and_then(|v| v.as_str()) == Some("http")
        && outbounds[0].get("tls").is_none()
        && parsed.endpoints.is_empty();

    outbounds.push(json!({
        "type": "direct",
        "tag": "direct"
    }));

    let mut root = Map::new();
    root.insert("log".into(), json!({ "level": "info", "timestamp": true }));
    root.insert(
        "dns".into(),
        json!({
            "servers": [
                { "tag": "direct-dns", "type": "udp", "server": "8.8.8.8" }
            ],
            "final": "direct-dns"
        }),
    );
    root.insert("inbounds".into(), json!([inbound]));
    if !parsed.endpoints.is_empty() {
        root.insert("endpoints".into(), Value::Array(parsed.endpoints.clone()));
    }
    root.insert("outbounds".into(), Value::Array(outbounds));
    if is_http_only {
        root.insert(
            "route".into(),
            json!({
                "rules": [
                    { "action": "sniff" },
                    { "protocol": "dns", "action": "hijack-dns" },
                    { "network": "udp", "outbound": "direct" },
                    { "network": "tcp", "outbound": final_tag }
                ],
                "final": "direct",
                "auto_detect_interface": true,
                "default_domain_resolver": "direct-dns"
            }),
        );
    } else {
        root.insert(
            "route".into(),
            json!({
                "rules": [
                    { "action": "sniff" },
                    { "protocol": "dns", "action": "hijack-dns" }
                ],
                "final": final_tag,
                "auto_detect_interface": true,
                "default_domain_resolver": "direct-dns"
            }),
        );
    }

    serde_json::to_string_pretty(&Value::Object(root)).map_err(|e| e.to_string())
}

pub fn build_local_vpn_config_json(mixed_port: u16, local_http_port: u16) -> Result<String, String> {
    let cfg = json!({
        "log": { "level": "info", "timestamp": true },
        "dns": {
            "servers": [
                { "tag": "direct-dns", "type": "udp", "server": "8.8.8.8" }
            ],
            "final": "direct-dns"
        },
        "inbounds": [
            {
                "type": "mixed",
                "tag": "mixed-in",
                "listen": "127.0.0.1",
                "listen_port": mixed_port
            }
        ],
        "outbounds": [
            {
                "type": "http",
                "tag": "local-gw",
                "server": "127.0.0.1",
                "server_port": local_http_port
            },
            {
                "type": "direct",
                "tag": "direct"
            }
        ],
        "route": {
            "rules": [
                { "action": "sniff" },
                { "protocol": "dns", "action": "hijack-dns" },
                { "ip_is_private": true, "outbound": "direct" },
                { "network": "tcp", "outbound": "local-gw" }
            ],
            "final": "direct",
            "auto_detect_interface": true,
            "default_domain_resolver": "direct-dns"
        }
    });
    serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_plain_ip_and_builds_valid_json() {
        let p = parse_profile("43.167.4.171", 8080).unwrap();
        assert_eq!(p.primary_addr, Some(("43.167.4.171".into(), 8080)));
        let cfg = build_config_json(&p, ConfigTarget::Mixed { port: 10808 }).unwrap();
        let v: Value = serde_json::from_str(&cfg).unwrap();
        assert_eq!(v["inbounds"][0]["type"], "mixed");
        assert_eq!(v["outbounds"][0]["type"], "http");
        assert_eq!(v["outbounds"][0]["server_port"], 8080);
    }

    #[test]
    fn parses_vless_reality() {
        let uri = "vless://11111111-1111-1111-1111-111111111111@example.com:443?type=tcp&security=reality&sni=cdn.example.com&fp=chrome&pbk=PUBKEY&sid=abcd&flow=xtls-rprx-vision#Test";
        let p = parse_profile(uri, 8080).unwrap();
        assert!(p.summary.contains("VLESS · Reality"));
        assert_eq!(p.proto_badge, "VLESS · Reality");
        assert!(p.primary_tcp);
        assert!(p.auto_core);
        assert_eq!(p.outbounds[0]["type"], "vless");
        assert_eq!(p.outbounds[0]["tls"]["reality"]["public_key"], "PUBKEY");
        assert_eq!(p.outbounds[0]["flow"], "xtls-rprx-vision");
    }

    #[test]
    fn parses_hysteria_v1_and_hyperia() {
        let hy1 = "hysteria://1.2.3.4:443?protocol=udp&auth=my%40auth&peer=sni.test&insecure=1&upmbps=50&downmbps=100#Hy1";
        let p1 = parse_profile(hy1, 8080).unwrap();
        assert_eq!(p1.proto_badge, "Hysteria");
        assert!(!p1.primary_tcp);
        assert!(p1.auto_core);
        assert_eq!(p1.outbounds[0]["type"], "hysteria");
        assert_eq!(p1.outbounds[0]["auth_str"], "my@auth");

        let hyperia = "hyperia://my%2Bpass@5.6.7.8:443?sni=hy.test&mport=20000-30000&insecure=1#Fast";
        let p2 = parse_profile(hyperia, 8080).unwrap();
        assert_eq!(p2.proto_badge, "Hysteria2");
        assert!(!p2.primary_tcp);
        assert_eq!(p2.outbounds[0]["password"], "my+pass");
        assert_eq!(p2.outbounds[0]["server_ports"][0], "20000:30000");
    }

    #[test]
    fn parses_vmess_b64_and_ss_and_hy2_and_tuic_and_socks() {
        let vmess_json = r#"{"v":"2","ps":"node","add":"v.example.com","port":"443","id":"uuid-1","aid":"0","net":"ws","host":"v.example.com","path":"/ws","tls":"tls"}"#;
        let vmess = format!("vmess://{}", STANDARD.encode(vmess_json));
        assert_eq!(parse_profile(&vmess, 8080).unwrap().outbounds[0]["type"], "vmess");

        let ss_cred = STANDARD_NO_PAD.encode("aes-256-gcm:secret");
        let ss = format!("ss://{ss_cred}@ss.example.com:8388#SS");
        let p_ss = parse_profile(&ss, 8080).unwrap();
        assert_eq!(p_ss.outbounds[0]["type"], "shadowsocks");
        assert_eq!(p_ss.outbounds[0]["method"], "aes-256-gcm");

        let hy2 = "hy2://pass123@hy.example.com:443?sni=hy.example.com&insecure=1&obfs=salamander&obfs-password=obf";
        let p_hy2 = parse_profile(hy2, 8080).unwrap();
        assert_eq!(p_hy2.outbounds[0]["type"], "hysteria2");
        assert_eq!(p_hy2.outbounds[0]["obfs"]["type"], "salamander");

        let playit_hy2 = "hy2://e168f74e-c8a7-4993-8195-67b015769f7a@147.185.221.218:35634/?insecure=1&sni=bing.com#WuWa-Playit-UDP";
        let p_playit = parse_profile(playit_hy2, 8080).unwrap();
        assert_eq!(p_playit.proto_badge, "Hysteria2");
        assert_eq!(p_playit.outbounds[0]["server"], "147.185.221.218");
        assert_eq!(p_playit.outbounds[0]["server_port"], 35634);
        assert_eq!(p_playit.outbounds[0]["tls"]["server_name"], "bing.com");
        assert_eq!(p_playit.outbounds[0]["tls"]["insecure"], true);

        let tuic = "tuic://uuid-1:pass@tuic.example.com:443?congestion_control=bbr&alpn=h3";
        assert_eq!(parse_profile(tuic, 8080).unwrap().outbounds[0]["type"], "tuic");

        let socks = "socks5://user:pass@127.0.0.1:1080";
        assert_eq!(parse_profile(socks, 8080).unwrap().outbounds[0]["type"], "socks");
    }

    #[test]
    fn parses_wireguard_and_base64_sub() {
        let wg = "wg://PRIVKEY@1.2.3.4:51820?publickey=PUBKEY&address=10.0.0.2/32&mtu=1280";
        let p_wg = parse_profile(wg, 8080).unwrap();
        assert_eq!(p_wg.endpoints[0]["type"], "wireguard");
        let cfg = build_config_json(&p_wg, ConfigTarget::Tun { per_app_pkg: Some("com.kurogame.wutheringwaves.global") }).unwrap();
        let v: Value = serde_json::from_str(&cfg).unwrap();
        assert_eq!(v["inbounds"][0]["include_package"][0], "com.kurogame.wutheringwaves.global");

        let sub_raw = "socks5://1.1.1.1:1080\ntrojan://pw@2.2.2.2:443?sni=2.2.2.2";
        let sub_b64 = STANDARD.encode(sub_raw);
        let p_sub = parse_profile(&sub_b64, 8080).unwrap();
        assert_eq!(p_sub.outbounds.len(), 2);
    }
}
