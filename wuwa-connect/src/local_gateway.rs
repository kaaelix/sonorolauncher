use aes::cipher::{block_padding::Pkcs7, BlockEncryptMut, KeyIvInit};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use rustls::pki_types::{CertificateDer, PrivateKeyDer};
use rustls::{ServerConfig, ServerConnection, StreamOwned};
use serde_json::json;
use std::collections::HashMap;
use std::fs;
use std::io::{self, BufReader, Read, Write};
use std::net::{Shutdown, TcpListener, TcpStream, ToSocketAddrs};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::Duration;

type Aes256CbcEnc = cbc::Encryptor<aes::Aes256>;

const AES_KEY: [u8; 32] = [
    0xb7, 0xe0, 0x04, 0xbb, 0x94, 0x86, 0x76, 0x9c, 0xf4, 0xea, 0xda, 0x26, 0xa2, 0x76, 0xa3, 0x2c,
    0xc6, 0xae, 0xf4, 0x02, 0x60, 0x9b, 0x24, 0xef, 0x56, 0x1c, 0xfd, 0x56, 0xd1, 0x9f, 0xd7, 0xed,
];
const AES_IV: [u8; 16] = [
    0x7e, 0x9a, 0xdc, 0xe6, 0x50, 0x56, 0x00, 0x34, 0x01, 0xee, 0xd8, 0xa6, 0xd1, 0x1d, 0x89, 0xc5,
];

struct GatewayConfig {
    host_override: String,
    login_port: u16,
    gateway_port: u16,
    loginsv_addr: String,
    gm_addr: String,
    state_dir: PathBuf,
    index_template: String,
    default_enc_payload: Vec<u8>,
    tls_cfg: Arc<ServerConfig>,
    payload_cache: Mutex<HashMap<String, Vec<u8>>>,
    sex_cache: Mutex<HashMap<String, i64>>,
}

struct GatewayState {
    stop: Arc<AtomicBool>,
}

static GATEWAY: OnceLock<Mutex<Option<GatewayState>>> = OnceLock::new();

fn gateway_slot() -> &'static Mutex<Option<GatewayState>> {
    GATEWAY.get_or_init(|| Mutex::new(None))
}

pub fn encrypt_modified_index(
    template: &str,
    target_host: &str,
    login_port: u16,
) -> Result<Vec<u8>, String> {
    let mut rendered = template.replace("127.0.0.1", target_host);
    if login_port != 5500 {
        rendered = rendered.replace(":5500", &format!(":{login_port}"));
    }
    let pt = rendered.as_bytes();
    let mut buf = vec![0u8; pt.len() + 16];
    buf[..pt.len()].copy_from_slice(pt);
    let enc = Aes256CbcEnc::new(&AES_KEY.into(), &AES_IV.into())
        .encrypt_padded_mut::<Pkcs7>(&mut buf, pt.len())
        .map_err(|e| format!("AES encrypt failed: {e}"))?;
    Ok(STANDARD.encode(enc).into_bytes())
}

fn load_tls_config(cert_pem: &[u8], key_pem: &[u8]) -> Result<Arc<ServerConfig>, String> {
    let mut cr = BufReader::new(cert_pem);
    let certs: Vec<CertificateDer<'static>> = rustls_pemfile::certs(&mut cr)
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| format!("Parse cert PEM failed: {e}"))?;
    let mut kr = BufReader::new(key_pem);
    let key: PrivateKeyDer<'static> = rustls_pemfile::private_key(&mut kr)
        .map_err(|e| format!("Parse key PEM failed: {e}"))?
        .ok_or_else(|| "No private key found in PEM".to_string())?;
    let cfg = ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(certs, key)
        .map_err(|e| format!("TLS ServerConfig error: {e}"))?;
    Ok(Arc::new(cfg))
}

fn get_payload(cfg: &GatewayConfig, target_host: &str) -> Vec<u8> {
    if target_host == "127.0.0.1" && cfg.login_port == 5500 && !cfg.default_enc_payload.is_empty() {
        return cfg.default_enc_payload.clone();
    }
    if let Ok(cache) = cfg.payload_cache.lock() {
        if let Some(hit) = cache.get(target_host) {
            return hit.clone();
        }
    }
    let computed = encrypt_modified_index(&cfg.index_template, target_host, cfg.login_port)
        .unwrap_or_else(|_| cfg.default_enc_payload.clone());
    if let Ok(mut cache) = cfg.payload_cache.lock() {
        cache.insert(target_host.to_string(), computed.clone());
    }
    computed
}

fn resolve_target_host(cfg: &GatewayConfig, stream: &TcpStream, host_hdr: &str) -> String {
    if !cfg.host_override.is_empty() {
        return cfg.host_override.clone();
    }
    let hdr_host = host_hdr.split(':').next().unwrap_or("").trim();
    if !hdr_host.is_empty()
        && !matches!(hdr_host, "0.0.0.0" | "::" | "::1" | "localhost")
    {
        return hdr_host.to_string();
    }
    if let Ok(addr) = stream.local_addr() {
        let mut s = addr.ip().to_string();
        if let Some(v4) = s.strip_prefix("::ffff:") {
            s = v4.to_string();
        }
        if !matches!(s.as_str(), "" | "0.0.0.0" | "::" | "::1") {
            return s;
        }
    }
    "127.0.0.1".to_string()
}

fn is_gm_path(path: &str) -> bool {
    matches!(
        path,
        "/gm" | "/gm/" | "/announcement" | "/announcement/" | "/aki-logic/status" | "/gm/auth"
    ) || path.starts_with("/gm/state")
        || path.starts_with("/gm/exec")
        || path.starts_with("/gm/catalog")
        || path.starts_with("/aki-logic/")
}

fn is_notice_path(path: &str) -> bool {
    path.starts_with("/gm-notice")
        || path.starts_with("/gamenotice")
        || path.starts_with("/notice/")
        || path.contains("/gm/loginNotice/")
        || path.ends_with("LoginNotice.json")
}

fn build_notice_payload(path: &str, base_origin: &str) -> Option<Vec<u8>> {
    if path.ends_with("entrypoint.json") || path.contains("/gamenotice/") {
        return Some(
            json!({
                "h5AppUrl": [format!("{base_origin}/gm")],
                "contentUrl": [format!("{base_origin}/gm-notice/notice.json")],
                "apiUrl": format!("{base_origin}/gm-notice"),
                "apiUrls": [format!("{base_origin}/gm-notice")]
            })
            .to_string()
            .into_bytes(),
        );
    }
    if path.ends_with("notice.json") {
        return Some(
            json!({
                "game": [{
                    "id": "cheat_code_notice",
                    "red": false,
                    "platform": [0, 1, 2, 3, 4, 5, 6],
                    "channel": ["0", "1", "2", "3", "18", "19"],
                    "whiteList": [],
                    "startTimeMs": 0,
                    "endTimeMs": 4102444800000_i64,
                    "permanent": true
                }],
                "activity": []
            })
            .to_string()
            .into_bytes(),
        );
    }
    if path.contains("/notice/red-dot/get") {
        return Some(br#"{"code":200,"message":"ok","data":false}"#.to_vec());
    }
    if path.contains("/notice/read-ids/list") {
        return Some(br#"{"code":200,"message":"ok","data":[]}"#.to_vec());
    }
    if path.contains("loginNotice") || path.ends_with("LoginNotice.json") {
        return Some(
            json!([{
                "Id": 1,
                "WhiteLists": [],
                "ModifyTime": 1,
                "BeginTime": 0,
                "EndTime": 4102444800_i64,
                "Title": "GM Control Panel",
                "content": format!("Open {base_origin}/gm in your browser to use the GM Control Panel.")
            }])
            .to_string()
            .into_bytes(),
        );
    }
    None
}

struct HttpReq {
    method: String,
    target: String,
    headers: Vec<(String, String)>,
    body: Vec<u8>,
}

fn read_http_request<R: Read>(stream: &mut R) -> io::Result<Option<HttpReq>> {
    let mut raw = Vec::with_capacity(2048);
    let mut tmp = [0u8; 4096];
    let header_end;
    loop {
        let n = stream.read(&mut tmp)?;
        if n == 0 {
            return Ok(None);
        }
        raw.extend_from_slice(&tmp[..n]);
        if let Some(pos) = raw.windows(4).position(|w| w == b"\r\n\r\n") {
            header_end = pos;
            break;
        }
        if raw.len() > 65536 {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "header too large"));
        }
    }

    let hdr_str = String::from_utf8_lossy(&raw[..header_end]).into_owned();
    let mut lines = hdr_str.split("\r\n");
    let first = lines.next().unwrap_or("");
    let mut parts = first.split_whitespace();
    let method = parts.next().unwrap_or("GET").to_string();
    let target = parts.next().unwrap_or("/").to_string();

    let mut headers = Vec::new();
    let mut clen: usize = 0;
    for ln in lines {
        if let Some((k, v)) = ln.split_once(':') {
            let ks = k.trim().to_string();
            let vs = v.trim().to_string();
            if ks.eq_ignore_ascii_case("content-length") {
                clen = vs.parse::<usize>().unwrap_or(0).min(16 * 1024 * 1024);
            }
            headers.push((ks, vs));
        }
    }

    let mut body = raw[header_end + 4..].to_vec();
    while body.len() < clen {
        let need = (clen - body.len()).min(tmp.len());
        let n = stream.read(&mut tmp[..need])?;
        if n == 0 {
            break;
        }
        body.extend_from_slice(&tmp[..n]);
    }
    body.truncate(clen);

    Ok(Some(HttpReq {
        method,
        target,
        headers,
        body,
    }))
}

fn header_val<'a>(headers: &'a [(String, String)], name: &str) -> &'a str {
    headers
        .iter()
        .find(|(k, _)| k.eq_ignore_ascii_case(name))
        .map(|(_, v)| v.as_str())
        .unwrap_or("")
}

fn send_http_response<W: Write>(
    w: &mut W,
    status: u16,
    ctype: &str,
    body: &[u8],
) -> io::Result<()> {
    let phrase = match status {
        200 => "OK",
        204 => "No Content",
        404 => "Not Found",
        500 => "Internal Server Error",
        _ => "OK",
    };
    let head = format!(
        "HTTP/1.1 {status} {phrase}\r\nContent-Type: {ctype}\r\nContent-Length: {}\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Content-Type, X-GM-Sign, X-GM-Admin\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n",
        body.len()
    );
    w.write_all(head.as_bytes())?;
    if !body.is_empty() {
        w.write_all(body)?;
    }
    w.flush()
}

fn forward_http_raw(
    addr: &str,
    method: &str,
    path_and_query: &str,
    headers: &[(String, String)],
    body: &[u8],
    timeout: Duration,
) -> Result<(u16, String, Vec<u8>), String> {
    let sa = addr
        .to_socket_addrs()
        .map_err(|e| e.to_string())?
        .next()
        .ok_or_else(|| "resolve failed".to_string())?;
    let mut s = TcpStream::connect_timeout(&sa, timeout).map_err(|e| e.to_string())?;
    let _ = s.set_read_timeout(Some(timeout));
    let _ = s.set_write_timeout(Some(timeout));

    let mut req = format!("{method} {path_and_query} HTTP/1.1\r\nHost: {addr}\r\nConnection: close\r\n");
    for (k, v) in headers {
        let kl = k.to_ascii_lowercase();
        if kl != "host" && kl != "connection" && kl != "content-length" && kl != "proxy-connection" {
            req.push_str(&format!("{k}: {v}\r\n"));
        }
    }
    if !body.is_empty() {
        req.push_str(&format!("Content-Length: {}\r\n", body.len()));
    }
    req.push_str("\r\n");
    s.write_all(req.as_bytes()).map_err(|e| e.to_string())?;
    if !body.is_empty() {
        s.write_all(body).map_err(|e| e.to_string())?;
    }
    let _ = s.flush();

    let mut resp_bytes = Vec::new();
    s.read_to_end(&mut resp_bytes).map_err(|e| e.to_string())?;
    let split = resp_bytes
        .windows(4)
        .position(|w| w == b"\r\n\r\n")
        .ok_or_else(|| "Malformed HTTP response".to_string())?;
    let hdr_str = String::from_utf8_lossy(&resp_bytes[..split]);
    let mut lines = hdr_str.split("\r\n");
    let status_line = lines.next().unwrap_or("HTTP/1.1 200 OK");
    let status: u16 = status_line
        .split_whitespace()
        .nth(1)
        .and_then(|n| n.parse().ok())
        .unwrap_or(200);
    let mut ctype = "application/json; charset=utf-8".to_string();
    let mut chunked = false;
    for ln in lines {
        if let Some((k, v)) = ln.split_once(':') {
            if k.trim().eq_ignore_ascii_case("content-type") {
                ctype = v.trim().to_string();
            } else if k.trim().eq_ignore_ascii_case("transfer-encoding")
                && v.to_ascii_lowercase().contains("chunked")
            {
                chunked = true;
            }
        }
    }
    let mut raw_body = resp_bytes[split + 4..].to_vec();
    if chunked {
        raw_body = decode_chunked(&raw_body);
    }
    Ok((status, ctype, raw_body))
}

fn decode_chunked(data: &[u8]) -> Vec<u8> {
    let mut out = Vec::new();
    let mut i = 0;
    while i < data.len() {
        let Some(nl) = data[i..].windows(2).position(|w| w == b"\r\n") else {
            break;
        };
        let len_hex = String::from_utf8_lossy(&data[i..i + nl]);
        let sz = usize::from_str_radix(len_hex.split(';').next().unwrap_or("").trim(), 16)
            .unwrap_or(0);
        i += nl + 2;
        if sz == 0 || i + sz > data.len() {
            break;
        }
        out.extend_from_slice(&data[i..i + sz]);
        i += sz + 2;
    }
    out
}

fn parse_query(q: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for pair in q.split('&') {
        if let Some((k, v)) = pair.split_once('=') {
            map.insert(
                crate::singbox::pct_decode(&k.replace('+', " ")),
                crate::singbox::pct_decode(&v.replace('+', " ")),
            );
        }
    }
    map
}

fn split_path_query(raw_target: &str) -> (String, String) {
    if let Some(pos) = raw_target.find("://") {
        let after = &raw_target[pos + 3..];
        let path_part = after.find('/').map(|i| &after[i..]).unwrap_or("/");
        if let Some((p, q)) = path_part.split_once('?') {
            (p.to_string(), q.to_string())
        } else {
            (path_part.to_string(), String::new())
        }
    } else if let Some((p, q)) = raw_target.split_once('?') {
        (p.to_string(), q.to_string())
    } else {
        (raw_target.to_string(), String::new())
    }
}

fn read_player_package_info(state_dir: &PathBuf, uid: &str) -> Vec<u8> {
    let pdir = state_dir.join("player").join(uid);
    let mut sex: i64 = 0;
    let mut finished = Vec::<i64>::new();
    let mut missing = Vec::<i64>::new();

    if let Ok(txt) = fs::read_to_string(pdir.join("basic_info")) {
        if let Some(pos) = txt.find(".sex") {
            let tail = &txt[pos..];
            if let Some(eq) = tail.find('=') {
                let num: String = tail[eq + 1..]
                    .trim_start()
                    .chars()
                    .take_while(|c| c.is_ascii_digit())
                    .collect();
                if let Ok(n) = num.parse::<i64>() {
                    sex = n;
                }
            }
        }
    }
    if let Ok(txt) = fs::read_to_string(pdir.join("quest_info")) {
        if let Some(pos) = txt.find(".finished_quests") {
            if let Some(open) = txt[pos..].find(".{") {
                let start = pos + open + 2;
                if let Some(close) = txt[start..].find('}') {
                    for part in txt[start..start + close].split(',') {
                        if let Ok(id) = part.trim().parse::<i64>() {
                            finished.push(id);
                        }
                    }
                }
            }
        }
    }
    if let Ok(txt) = fs::read_to_string(pdir.join("progress_info")) {
        if let Some(pos) = txt.find("quest:missing_res") {
            if let Some(valp) = txt[pos..].find(".val") {
                let tail = &txt[pos + valp..];
                if let Some(q1) = tail.find('"') {
                    if let Some(q2) = tail[q1 + 1..].find('"') {
                        for part in tail[q1 + 1..q1 + 1 + q2].split(',') {
                            if let Ok(id) = part.trim().parse::<i64>() {
                                missing.push(id);
                            }
                        }
                    }
                }
            }
        }
    }

    json!({
        "code": 0,
        "userId": uid,
        "needConfirmQuestIdSet": missing,
        "finishQuestIdSet": finished,
        "currentBlockIdSet": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],
        "sex": sex,
        "curRecallId": 0,
        "mp4FinishQuestFlag": []
    })
    .to_string()
    .into_bytes()
}

fn handle_login_client(cfg: Arc<GatewayConfig>, mut stream: TcpStream) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(15)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(15)));
    let _ = stream.set_nodelay(true);
    let Ok(Some(req)) = read_http_request(&mut stream) else {
        return;
    };
    let host_hdr = header_val(&req.headers, "host").to_string();
    let target_host = resolve_target_host(&cfg, &stream, &host_hdr);
    let (path, query) = split_path_query(&req.target);
    let pq = if query.is_empty() {
        path.clone()
    } else {
        format!("{path}?{query}")
    };

    if req.method == "OPTIONS" {
        let _ = send_http_response(&mut stream, 204, "text/plain", b"");
        return;
    }
    if path == "/health" {
        let _ = send_http_response(&mut stream, 200, "text/plain; charset=utf-8", b"OK");
        return;
    }
    if is_gm_path(&path) {
        match forward_http_raw(
            &cfg.gm_addr,
            &req.method,
            &pq,
            &req.headers,
            &req.body,
            Duration::from_secs(10),
        ) {
            Ok((st, ct, body)) => {
                let _ = send_http_response(&mut stream, st, &ct, &body);
            }
            Err(e) => {
                let b = json!({"ok": false, "online": false, "message": e})
                    .to_string()
                    .into_bytes();
                let _ = send_http_response(&mut stream, 200, "application/json; charset=utf-8", &b);
            }
        }
        return;
    }
    if is_notice_path(&path) {
        if req.method == "POST" {
            let _ = send_http_response(
                &mut stream,
                200,
                "application/json; charset=utf-8",
                br#"{"code":200,"message":"ok","data":true}"#,
            );
            return;
        }
        let origin = format!("http://{target_host}:{}", cfg.login_port);
        let payload = build_notice_payload(&path, &origin)
            .unwrap_or_else(|| br#"{"code":200,"message":"ok"}"#.to_vec());
        let _ = send_http_response(&mut stream, 200, "application/json; charset=utf-8", &payload);
        return;
    }
    if path.contains("index.json") {
        let payload = get_payload(&cfg, &target_host);
        let _ = send_http_response(&mut stream, 200, "application/json", &payload);
        return;
    }
    if path.starts_with("/api/getResourcePackageInfo") {
        let qs = parse_query(&query);
        let uid = qs
            .get("userId")
            .filter(|s| !s.is_empty())
            .cloned()
            .unwrap_or_else(|| "597938510".to_string());
        let body = read_player_package_info(&cfg.state_dir, &uid);
        let _ = send_http_response(&mut stream, 200, "application/json", &body);
        return;
    }
    if path.starts_with("/api/login") {
        let qs = parse_query(&query);
        let uid = qs
            .get("userId")
            .filter(|s| !s.is_empty())
            .cloned()
            .unwrap_or_else(|| "597938510".to_string());
        let uname = qs
            .get("userName")
            .filter(|s| !s.is_empty())
            .cloned()
            .unwrap_or_else(|| uid.clone());
        let token = qs
            .get("token")
            .filter(|s| !s.is_empty())
            .cloned()
            .unwrap_or_else(|| format!("00-local-{uid}-00"));
        let user_data: i64 = qs
            .get("userData")
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);

        let forward_q = if req.method == "POST" {
            pq.clone()
        } else {
            format!("/api/login?loginType=1&userId={uid}&userName={uname}&token={token}&userData={user_data}")
        };
        let mut sex: i64 = cfg
            .sex_cache
            .lock()
            .ok()
            .and_then(|m| m.get(&uid).copied())
            .unwrap_or(-1);
        let mut code: i64 = 0;

        if let Ok((_, _, body)) = forward_http_raw(
            &cfg.loginsv_addr,
            &req.method,
            &forward_q,
            &[],
            &req.body,
            Duration::from_millis(2000),
        ) {
            if let Ok(sv) = serde_json::from_slice::<serde_json::Value>(&body) {
                code = sv.get("code").and_then(|v| v.as_i64()).unwrap_or(0);
                if let Some(sx) = sv.get("sex").and_then(|v| v.as_i64()) {
                    sex = sx;
                    if sx >= 0 {
                        if let Ok(mut m) = cfg.sex_cache.lock() {
                            m.insert(uid.clone(), sx);
                        }
                    }
                }
            }
        }

        let resp = json!({
            "code": code,
            "token": token,
            "udpHosts": [{"host": target_host, "port": cfg.gateway_port}],
            "tcpHosts": [{"host": target_host, "port": cfg.gateway_port}],
            "tcpRatio": 0,
            "userData": user_data,
            "sex": sex,
            "gatewayLatencyConfig": {
                "enabled": false,
                "count": 1,
                "timeoutMs": 3000
            }
        });
        let _ = send_http_response(
            &mut stream,
            200,
            "application/json",
            resp.to_string().as_bytes(),
        );
        return;
    }

    let _ = send_http_response(
        &mut stream,
        200,
        "application/json",
        br#"{"code": 0, "msg": "success"}"#,
    );
}

fn serve_tls_mitm(
    cfg: &GatewayConfig,
    mut stream: TcpStream,
    host: &str,
    target_host: &str,
    is_config: bool,
) {
    if stream
        .write_all(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        .is_err()
    {
        return;
    }
    let _ = stream.flush();
    let Ok(conn) = ServerConnection::new(cfg.tls_cfg.clone()) else {
        return;
    };
    let mut tls = StreamOwned::new(conn, stream);
    let Ok(Some(req)) = read_http_request(&mut tls) else {
        return;
    };
    if is_config {
        let payload = get_payload(cfg, target_host);
        let _ = send_http_response(&mut tls, 200, "application/json", &payload);
        return;
    }
    let (path, query) = split_path_query(&req.target);
    let pq = if query.is_empty() {
        path.clone()
    } else {
        format!("{path}?{query}")
    };
    if req.method == "OPTIONS" {
        let _ = send_http_response(&mut tls, 204, "text/plain", b"");
    } else if is_gm_path(&path) {
        match forward_http_raw(
            &cfg.gm_addr,
            &req.method,
            &pq,
            &req.headers,
            &req.body,
            Duration::from_secs(10),
        ) {
            Ok((st, ct, body)) => {
                let _ = send_http_response(&mut tls, st, &ct, &body);
            }
            Err(_) => {
                let _ = send_http_response(
                    &mut tls,
                    200,
                    "application/json; charset=utf-8",
                    br#"{"ok":false}"#,
                );
            }
        }
    } else {
        let origin = format!("https://{host}");
        let body = build_notice_payload(&path, &origin)
            .unwrap_or_else(|| br#"{"code":200,"message":"ok"}"#.to_vec());
        let _ = send_http_response(&mut tls, 200, "application/json; charset=utf-8", &body);
    }
}

fn tunnel_tcp(mut c: TcpStream, host: &str, port: u16) {
    let addr = format!("{host}:{port}");
    let Ok(sa_iter) = addr.to_socket_addrs() else {
        return;
    };
    let Some(sa) = sa_iter.into_iter().next() else {
        return;
    };
    let Ok(remote) = TcpStream::connect_timeout(&sa, Duration::from_secs(10)) else {
        return;
    };
    let _ = remote.set_nodelay(true);
    let _ = c.set_read_timeout(Some(Duration::from_secs(120)));
    let _ = remote.set_read_timeout(Some(Duration::from_secs(120)));
    if c.write_all(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        .is_err()
    {
        return;
    }
    let (Ok(mut c_r), Ok(mut c_w)) = (c.try_clone(), c.try_clone()) else {
        return;
    };
    let (Ok(mut r_r), Ok(mut r_w)) = (remote.try_clone(), remote.try_clone()) else {
        return;
    };
    let t = thread::spawn(move || {
        let _ = io::copy(&mut c_r, &mut r_w);
        let _ = r_w.shutdown(Shutdown::Write);
    });
    let _ = io::copy(&mut r_r, &mut c_w);
    let _ = c_w.shutdown(Shutdown::Write);
    let _ = t.join();
}

fn handle_proxy_client(cfg: Arc<GatewayConfig>, mut stream: TcpStream) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(15)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(15)));
    let _ = stream.set_nodelay(true);
    let Ok(Some(req)) = read_http_request(&mut stream) else {
        return;
    };
    let host_hdr = header_val(&req.headers, "host").to_string();
    let target_host = resolve_target_host(&cfg, &stream, &host_hdr);

    if req.method.eq_ignore_ascii_case("CONNECT") {
        let (h, p) = if let Some((hp, pp)) = req.target.rsplit_once(':') {
            (
                hp.trim_matches(|c| c == '[' || c == ']').to_string(),
                pp.parse::<u16>().unwrap_or(443),
            )
        } else {
            (req.target.clone(), 443)
        };
        if h.contains("aki-config") {
            serve_tls_mitm(&cfg, stream, &h, &target_host, true);
            return;
        }
        if h.contains("aki-gm-resources-back") {
            serve_tls_mitm(&cfg, stream, &h, &target_host, false);
            return;
        }
        tunnel_tcp(stream, &h, p);
        return;
    }

    let (path, query) = split_path_query(&req.target);
    let pq = if query.is_empty() {
        path.clone()
    } else {
        format!("{path}?{query}")
    };
    if path.contains("index.json") && req.target.contains("aki-config") {
        let payload = get_payload(&cfg, &target_host);
        let _ = send_http_response(&mut stream, 200, "application/json", &payload);
        return;
    }
    if req.target.contains("aki-gm-resources-back") || is_notice_path(&path) || is_gm_path(&path) {
        if is_gm_path(&path) {
            match forward_http_raw(
                &cfg.gm_addr,
                &req.method,
                &pq,
                &req.headers,
                &req.body,
                Duration::from_secs(10),
            ) {
                Ok((st, ct, body)) => {
                    let _ = send_http_response(&mut stream, st, &ct, &body);
                }
                Err(_) => {
                    let _ = send_http_response(&mut stream, 200, "application/json", b"{}");
                }
            }
        } else {
            let origin = format!("http://{target_host}:{}", cfg.login_port);
            let body = build_notice_payload(&path, &origin)
                .unwrap_or_else(|| br#"{"code":200,"message":"ok"}"#.to_vec());
            let _ = send_http_response(&mut stream, 200, "application/json; charset=utf-8", &body);
        }
        return;
    }

    let target_addr = if let Some(after) = req.target.strip_prefix("http://") {
        after.split('/').next().unwrap_or(&host_hdr).to_string()
    } else {
        host_hdr.clone()
    };
    let forward_host = if target_addr.contains(':') {
        target_addr
    } else {
        format!("{target_addr}:80")
    };
    match forward_http_raw(
        &forward_host,
        &req.method,
        &pq,
        &req.headers,
        &req.body,
        Duration::from_secs(20),
    ) {
        Ok((st, ct, body)) => {
            let _ = send_http_response(&mut stream, st, &ct, &body);
        }
        Err(_) => {
            let _ = send_http_response(&mut stream, 200, "application/json", br#"{"code":0}"#);
        }
    }
}

fn run_accept_loop(
    listener: TcpListener,
    stop: Arc<AtomicBool>,
    cfg: Arc<GatewayConfig>,
    is_login: bool,
) {
    let _ = listener.set_nonblocking(true);
    while !stop.load(Ordering::Relaxed) {
        match listener.accept() {
            Ok((stream, _)) => {
                let _ = stream.set_nonblocking(false);
                let c = cfg.clone();
                thread::spawn(move || {
                    if is_login {
                        handle_login_client(c, stream);
                    } else {
                        handle_proxy_client(c, stream);
                    }
                });
            }
            Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(50));
            }
            Err(_) => {
                thread::sleep(Duration::from_millis(100));
            }
        }
    }
}

pub fn stop_gateway() {
    if let Ok(mut guard) = gateway_slot().lock() {
        if let Some(st) = guard.take() {
            st.stop.store(true, Ordering::SeqCst);
        }
    }
}

pub fn start_gateway(
    host_override: &str,
    login_port: u16,
    proxy_port: u16,
    gateway_port: u16,
    state_dir: &str,
    proxy_dir: &str,
) -> Result<(), String> {
    stop_gateway();
    let pdir = PathBuf::from(proxy_dir);
    let cert_pem = fs::read(pdir.join("server.crt"))
        .map_err(|e| format!("Read server.crt failed: {e}"))?;
    let key_pem = fs::read(pdir.join("server.key"))
        .map_err(|e| format!("Read server.key failed: {e}"))?;
    let index_template = fs::read_to_string(pdir.join("modified_3.8_index.json"))
        .map_err(|e| format!("Read modified_3.8_index.json failed: {e}"))?;
    let default_enc_payload = fs::read(pdir.join("encrypted_3.8_index.b64"))
        .map(|b| String::from_utf8_lossy(&b).trim().as_bytes().to_vec())
        .unwrap_or_default();

    let tls_cfg = load_tls_config(&cert_pem, &key_pem)?;
    let cfg = Arc::new(GatewayConfig {
        host_override: host_override.trim().to_string(),
        login_port,
        gateway_port,
        loginsv_addr: "127.0.0.1:10001".to_string(),
        gm_addr: "127.0.0.1:13101".to_string(),
        state_dir: PathBuf::from(state_dir),
        index_template,
        default_enc_payload,
        tls_cfg,
        payload_cache: Mutex::new(HashMap::new()),
        sex_cache: Mutex::new(HashMap::from([("597938510".to_string(), 1)])),
    });

    let login_listener = TcpListener::bind(("0.0.0.0", login_port))
        .map_err(|e| format!("Bind :{login_port} failed: {e}"))?;
    let proxy_listener = TcpListener::bind(("0.0.0.0", proxy_port))
        .map_err(|e| format!("Bind :{proxy_port} failed: {e}"))?;

    let stop = Arc::new(AtomicBool::new(false));
    {
        let mut guard = gateway_slot().lock().map_err(|_| "lock poisoned".to_string())?;
        *guard = Some(GatewayState { stop: stop.clone() });
    }

    let c1 = cfg.clone();
    let s1 = stop.clone();
    thread::spawn(move || run_accept_loop(login_listener, s1, c1, true));

    let c2 = cfg;
    let s2 = stop;
    thread::spawn(move || run_accept_loop(proxy_listener, s2, c2, false));

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn aes_encrypt_matches_pkcs7_and_b64() {
        let out = encrypt_modified_index(r#"{"url":"http://127.0.0.1:5500"}"#, "10.0.0.2", 5500).unwrap();
        assert!(!out.is_empty());
        let decoded = STANDARD.decode(&out).unwrap();
        assert_eq!(decoded.len() % 16, 0);
    }
}
