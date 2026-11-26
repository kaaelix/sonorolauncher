use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::{HashSet, VecDeque};
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufReader, Read, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Component, Path, PathBuf};
use lzma::read::XzDecoder;

const TERMUX_PREFIX_STRIP: &str = "data/data/com.termux/files/usr/";

#[derive(Clone, Debug)]
pub struct DebPkg {
    pub name: String,
    pub version: String,
    pub filename: String,
    pub size: u64,
    pub sha256: String,
    pub depends: Vec<String>,
}

pub fn parse_apt_packages_and_resolve(index_text: &str, roots: &[&str]) -> Vec<Value> {
    let mut map = std::collections::HashMap::<String, DebPkg>::new();
    for block in index_text.split("\n\n") {
        let mut name = String::new();
        let mut version = String::new();
        let mut filename = String::new();
        let mut size: u64 = 0;
        let mut sha256 = String::new();
        let mut depends = Vec::new();
        for line in block.lines() {
            if let Some(v) = line.strip_prefix("Package:") {
                name = v.trim().to_string();
            } else if let Some(v) = line.strip_prefix("Version:") {
                version = v.trim().to_string();
            } else if let Some(v) = line.strip_prefix("Filename:") {
                filename = v.trim().to_string();
            } else if let Some(v) = line.strip_prefix("Size:") {
                size = v.trim().parse().unwrap_or(0);
            } else if let Some(v) = line.strip_prefix("SHA256:") {
                sha256 = v.trim().to_string();
            } else if let Some(v) = line.strip_prefix("Depends:") {
                for raw_dep in v.split(',') {
                    let first_alt = raw_dep.split('|').next().unwrap_or("").trim();
                    let pkg_name = first_alt
                        .split(|c: char| c == ' ' || c == '(' || c == ':')
                        .next()
                        .unwrap_or("")
                        .trim();
                    if !pkg_name.is_empty() {
                        depends.push(pkg_name.to_string());
                    }
                }
            }
        }
        if !name.is_empty() && !filename.is_empty() {
            map.insert(
                name.clone(),
                DebPkg {
                    name,
                    version,
                    filename,
                    size,
                    sha256,
                    depends,
                },
            );
        }
    }

    let mut seen = HashSet::new();
    let mut q = VecDeque::new();
    for &r in roots {
        q.push_back(r.to_string());
    }
    let mut out = Vec::new();
    while let Some(cur) = q.pop_front() {
        if !seen.insert(cur.clone()) {
            continue;
        }
        if let Some(pkg) = map.get(&cur) {
            out.push(json!({
                "name": pkg.name,
                "version": pkg.version,
                "filename": pkg.filename,
                "size": pkg.size,
                "sha256": pkg.sha256
            }));
            for dep in &pkg.depends {
                if !seen.contains(dep) {
                    q.push_back(dep.clone());
                }
            }
        }
    }
    out
}

pub fn sha256_file_hex(path: &Path) -> Result<String, String> {
    let mut f = File::open(path).map_err(|e| format!("open {}: {e}", path.display()))?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 262144];
    loop {
        let n = f.read(&mut buf).map_err(|e| format!("read: {e}"))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

fn should_skip_rel(rel: &str) -> bool {
    rel.starts_with("share/doc/")
        || rel.starts_with("share/man/")
        || rel.starts_with("share/info/")
        || rel.starts_with("share/bash-completion/")
        || rel.starts_with("share/zsh/")
        || rel.starts_with("share/fish/")
        || rel.starts_with("include/llvm/")
        || rel.starts_with("include/llvm-c/")
        || rel.starts_with("include/clang/")
        || rel.starts_with("include/lld/")
        || rel.starts_with("lib/rustlib/armv7-")
        || rel.starts_with("lib/rustlib/i686-")
        || rel.starts_with("lib/rustlib/x86_64-")
        || matches!(
            rel,
            "bin/rust-analyzer"
                | "bin/rust-lldb"
                | "bin/rust-gdb"
                | "bin/rustdoc"
                | "bin/clippy-driver"
                | "bin/cargo-clippy"
                | "bin/cargo-fmt"
                | "bin/rustfmt"
                | "bin/clang-tidy"
                | "bin/clang-format"
                | "bin/clangd"
                | "bin/git-clang-format"
                | "bin/llc"
                | "bin/opt"
                | "bin/llvm-lto"
                | "bin/llvm-lto2"
                | "bin/llvm-dwarfdump"
                | "bin/llvm-objdump"
        )
}

fn clean_rel(path: &Path) -> Option<PathBuf> {
    let mut s = path.to_string_lossy().replace('\\', "/");
    while let Some(stripped) = s.strip_prefix("./") {
        s = stripped.to_string();
    }
    while let Some(stripped) = s.strip_prefix('/') {
        s = stripped.to_string();
    }
    if let Some(after) = s.strip_prefix(TERMUX_PREFIX_STRIP) {
        s = after.to_string();
    } else if s == TERMUX_PREFIX_STRIP.trim_end_matches('/') {
        return None;
    }
    if s.is_empty() || should_skip_rel(&s) {
        return None;
    }
    let mut pb = PathBuf::new();
    for c in Path::new(&s).components() {
        match c {
            Component::Normal(p) => pb.push(p),
            Component::CurDir => {}
            _ => return None,
        }
    }
    if pb.as_os_str().is_empty() {
        None
    } else {
        Some(pb)
    }
}

fn maybe_rewrite_shebang(rel: &Path, first_chunk: &[u8], runtime_prefix: &str) -> Option<Vec<u8>> {
    if !rel.starts_with("bin") {
        return None;
    }
    const OLD_PREFIX: &[u8] = b"#!/data/data/com.termux/files/usr/";
    if !first_chunk.starts_with(OLD_PREFIX) {
        return None;
    }
    let rest_all = &first_chunk[OLD_PREFIX.len()..];
    let mut out = Vec::with_capacity(first_chunk.len());
    if rest_all.starts_with(b"bin/sh\n") || rest_all.starts_with(b"bin/sh ") {
        out.extend_from_slice(b"#!/system/bin/sh");
        out.extend_from_slice(&rest_all[b"bin/sh".len()..]);
    } else {
        out.extend_from_slice(b"#!");
        out.extend_from_slice(runtime_prefix.as_bytes());
        out.push(b'/');
        out.extend_from_slice(rest_all);
    }
    Some(out)
}

fn extract_tar_stream<R: Read>(
    reader: R,
    dest_dir: &Path,
    r_prefix: &str,
) -> Result<Vec<String>, String> {
    let mut archive = tar::Archive::new(reader);
    let mut installed_paths: Vec<String> = Vec::new();
    let mut symlinks: Vec<(PathBuf, String, String)> = Vec::new();
    let mut created_dirs: HashSet<PathBuf> = HashSet::new();
    created_dirs.insert(dest_dir.to_path_buf());
    let mut copy_buf = vec![0u8; 131072];

    let entries = archive
        .entries()
        .map_err(|e| format!("tar entries: {e}"))?;
    for entry_res in entries {
        let mut entry = entry_res.map_err(|e| format!("tar entry: {e}"))?;
        let raw_path = entry.path().map_err(|e| format!("entry path: {e}"))?.into_owned();
        let Some(rel) = clean_rel(&raw_path) else {
            continue;
        };
        let rel_str = rel.to_string_lossy().into_owned();
        let target_path = dest_dir.join(&rel);
        let etype = entry.header().entry_type();
        let mode = entry.header().mode().unwrap_or(0o644);

        if etype.is_dir() {
            if !created_dirs.contains(&target_path) {
                fs::create_dir_all(&target_path)
                    .map_err(|e| format!("mkdir {}: {e}", target_path.display()))?;
                created_dirs.insert(target_path);
            }
        } else if etype.is_symlink() {
            if let Ok(Some(link)) = entry.link_name() {
                let mut lstr = link.to_string_lossy().into_owned();
                if let Some(rest) = lstr.strip_prefix("/data/data/com.termux/files/usr/") {
                    lstr = format!("{r_prefix}/{rest}");
                }
                symlinks.push((target_path, lstr, rel_str));
            }
        } else if etype.is_hard_link() {
            if let Ok(Some(link)) = entry.link_name() {
                if let Some(link_rel) = clean_rel(&link) {
                    let src = dest_dir.join(&link_rel);
                    if let Some(parent) = target_path.parent() {
                        if !created_dirs.contains(parent) {
                            let _ = fs::create_dir_all(parent);
                            created_dirs.insert(parent.to_path_buf());
                        }
                    }
                    let _ = fs::remove_file(&target_path);
                    if fs::hard_link(&src, &target_path).is_err() {
                        let _ = fs::copy(&src, &target_path);
                    }
                    installed_paths.push(rel_str);
                }
            }
        } else if etype.is_file() {
            if let Some(parent) = target_path.parent() {
                if !created_dirs.contains(parent) {
                    fs::create_dir_all(parent)
                        .map_err(|e| format!("mkdir parent: {e}"))?;
                    created_dirs.insert(parent.to_path_buf());
                }
            }
            let mut opts = OpenOptions::new();
            opts.write(true).create(true).truncate(true).mode(mode | 0o600);
            let mut out = match opts.open(&target_path) {
                Ok(f) => f,
                Err(_) => {
                    let _ = fs::remove_file(&target_path);
                    opts.open(&target_path)
                        .map_err(|e| format!("create {}: {e}", target_path.display()))?
                }
            };
            let mut first = true;
            loop {
                let n = entry
                    .read(&mut copy_buf)
                    .map_err(|e| format!("extract {}: {e}", target_path.display()))?;
                if n == 0 {
                    break;
                }
                if first {
                    first = false;
                    if let Some(rewritten) = maybe_rewrite_shebang(&rel, &copy_buf[..n], r_prefix) {
                        out.write_all(&rewritten)
                            .map_err(|e| format!("write {}: {e}", target_path.display()))?;
                        continue;
                    }
                }
                out.write_all(&copy_buf[..n])
                    .map_err(|e| format!("write {}: {e}", target_path.display()))?;
            }
            installed_paths.push(rel_str);
        }
    }

    for (dst, target, rel_str) in symlinks {
        if let Some(parent) = dst.parent() {
            if !created_dirs.contains(parent) {
                let _ = fs::create_dir_all(parent);
                created_dirs.insert(parent.to_path_buf());
            }
        }
        let _ = fs::remove_file(&dst);
        if std::os::unix::fs::symlink(&target, &dst).is_ok() {
            installed_paths.push(rel_str);
        }
    }

    Ok(installed_paths)
}

pub fn extract_deb_with_list(
    deb_path: &Path,
    dest_dir: &Path,
    runtime_prefix: &str,
) -> Result<Vec<String>, String> {
    fs::create_dir_all(dest_dir).map_err(|e| format!("mkdir {}: {e}", dest_dir.display()))?;
    let mut file = File::open(deb_path).map_err(|e| format!("open {}: {e}", deb_path.display()))?;
    let mut magic = [0u8; 8];
    file.read_exact(&mut magic)
        .map_err(|e| format!("read ar magic: {e}"))?;
    if &magic != b"!<arch>\n" {
        return Err("Invalid .deb AR archive".into());
    }

    let mut data_member: Option<(String, u64, u64)> = None;
    let mut pos: u64 = 8;
    loop {
        let mut hdr = [0u8; 60];
        if file.read_exact(&mut hdr).is_err() {
            break;
        }
        pos += 60;
        if &hdr[58..60] != b"`\n" {
            return Err("Corrupt .deb AR header".into());
        }
        let name = String::from_utf8_lossy(&hdr[0..16])
            .trim_end_matches(|c: char| c == ' ' || c == '/')
            .to_string();
        let size: u64 = String::from_utf8_lossy(&hdr[48..58])
            .trim()
            .parse()
            .map_err(|_| "Invalid AR member size".to_string())?;
        if name == "data.tar.xz" || name == "data.tar" {
            data_member = Some((name, pos, size));
            break;
        }
        let step = size + (size & 1);
        pos += step;
        use std::io::Seek;
        file.seek(io::SeekFrom::Start(pos))
            .map_err(|e| format!("seek ar: {e}"))?;
    }

    let (mname, off, sz) = data_member.ok_or_else(|| "No data.tar.xz in .deb".to_string())?;
    use std::io::Seek;
    file.seek(io::SeekFrom::Start(off))
        .map_err(|e| format!("seek {mname}: {e}"))?;

    let br = BufReader::with_capacity(512 * 1024, file.take(sz));
    let r_prefix = runtime_prefix.trim_end_matches('/');
    if mname == "data.tar" {
        extract_tar_stream(br, dest_dir, r_prefix)
    } else {
        extract_tar_stream(XzDecoder::new(br), dest_dir, r_prefix)
    }
}

pub fn extract_deb_to_dir(
    deb_path: &Path,
    dest_dir: &Path,
    runtime_prefix: &str,
) -> Result<u64, String> {
    extract_deb_with_list(deb_path, dest_dir, runtime_prefix).map(|v| v.len() as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_apt_deps() {
        let sample = "\
Package: rust
Filename: pool/rust.deb
Size: 100
SHA256: abc
Depends: clang, zlib (>= 1.2)

Package: clang
Filename: pool/clang.deb
Size: 50
SHA256: def
Depends: zlib

Package: zlib
Filename: pool/zlib.deb
Size: 10
SHA256: ghi
";
        let resolved = parse_apt_packages_and_resolve(sample, &["rust"]);
        assert_eq!(resolved.len(), 3);
        assert_eq!(resolved[0]["name"], "rust");
        assert_eq!(resolved[1]["name"], "clang");
        assert_eq!(resolved[2]["name"], "zlib");
    }

    #[test]
    fn extracts_synthetic_deb_xz() {
        use lzma::write::XzEncoder;
        use std::os::unix::fs::PermissionsExt;

        let mut tar_buf = Vec::new();
        {
            let enc = XzEncoder::new(&mut tar_buf, 1);
            let mut builder = tar::Builder::new(enc);
            let mut hdr = tar::Header::new_gnu();
            let payload = b"#!/bin/sh\necho ok\n";
            hdr.set_size(payload.len() as u64);
            hdr.set_mode(0o755);
            hdr.set_cksum();
            builder
                .append_data(&mut hdr, "data/data/com.termux/files/usr/bin/hello", &payload[..])
                .unwrap();
            let enc = builder.into_inner().unwrap();
            enc.finish().unwrap();
        }

        let mut ar = Vec::from(&b"!<arch>\n"[..]);
        let name_field = format!("{:<16}", "data.tar.xz");
        let hdr_str = format!("{name_field}0           0     0     100644  {:<10}`\n", tar_buf.len());
        ar.extend_from_slice(hdr_str.as_bytes());
        ar.extend_from_slice(&tar_buf);
        if tar_buf.len() & 1 == 1 {
            ar.push(b'\n');
        }

        let base = std::env::temp_dir().join(format!("wc_deb_test_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        let deb_path = base.join("test.deb");
        fs::write(&deb_path, &ar).unwrap();
        let out_dir = base.join("out");
        let n = extract_deb_to_dir(&deb_path, &out_dir, "/data/local/tmp/sonoro/tc").unwrap();
        assert_eq!(n, 1);
        let extracted = out_dir.join("bin/hello");
        assert_eq!(fs::read(&extracted).unwrap(), b"#!/bin/sh\necho ok\n");
        let mode = fs::metadata(&extracted).unwrap().permissions().mode();
        assert_eq!(mode & 0o700, 0o700);
        let _ = fs::remove_dir_all(&base);
    }
}
