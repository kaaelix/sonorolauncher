use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::{HashSet, VecDeque};
use std::fs::{self, File};
use std::io::{self, BufReader, Read, Write};
use std::os::unix::fs::PermissionsExt;
use std::path::{Component, Path, PathBuf};
use std::sync::mpsc;
use std::thread;

const TERMUX_PREFIX_STRIP: &str = "data/data/com.termux/files/usr/";

#[derive(Clone, Debug)]
pub struct DebPkg {
    pub name: String,
    pub filename: String,
    pub size: u64,
    pub sha256: String,
    pub depends: Vec<String>,
}

pub fn parse_apt_packages_and_resolve(index_text: &str, roots: &[&str]) -> Vec<Value> {
    let mut map = std::collections::HashMap::<String, DebPkg>::new();
    for block in index_text.split("\n\n") {
        let mut name = String::new();
        let mut filename = String::new();
        let mut size: u64 = 0;
        let mut sha256 = String::new();
        let mut depends = Vec::new();
        for line in block.lines() {
            if let Some(v) = line.strip_prefix("Package:") {
                name = v.trim().to_string();
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
    let mut buf = [0u8; 65536];
    loop {
        let n = f.read(&mut buf).map_err(|e| format!("read: {e}"))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

struct ChanWriter {
    tx: mpsc::SyncSender<Result<Vec<u8>, String>>,
    buf: Vec<u8>,
}

impl Write for ChanWriter {
    fn write(&mut self, data: &[u8]) -> io::Result<usize> {
        self.buf.extend_from_slice(data);
        if self.buf.len() >= 65536 {
            let chunk = std::mem::take(&mut self.buf);
            if self.tx.send(Ok(chunk)).is_err() {
                return Err(io::Error::new(io::ErrorKind::BrokenPipe, "reader closed"));
            }
        }
        Ok(data.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        if !self.buf.is_empty() {
            let chunk = std::mem::take(&mut self.buf);
            let _ = self.tx.send(Ok(chunk));
        }
        Ok(())
    }
}

struct ChanReader {
    rx: mpsc::Receiver<Result<Vec<u8>, String>>,
    cur: Vec<u8>,
    pos: usize,
    done: bool,
}

impl Read for ChanReader {
    fn read(&mut self, out: &mut [u8]) -> io::Result<usize> {
        if self.done {
            return Ok(0);
        }
        while self.pos >= self.cur.len() {
            match self.rx.recv() {
                Ok(Ok(next)) => {
                    if !next.is_empty() {
                        self.cur = next;
                        self.pos = 0;
                        break;
                    }
                }
                Ok(Err(e)) => {
                    self.done = true;
                    return Err(io::Error::new(io::ErrorKind::Other, e));
                }
                Err(_) => {
                    self.done = true;
                    return Ok(0);
                }
            }
        }
        let n = (self.cur.len() - self.pos).min(out.len());
        out[..n].copy_from_slice(&self.cur[self.pos..self.pos + n]);
        self.pos += n;
        Ok(n)
    }
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

pub fn extract_deb_to_dir(
    deb_path: &Path,
    dest_dir: &Path,
    runtime_prefix: &str,
) -> Result<u64, String> {
    fs::create_dir_all(dest_dir).map_err(|e| format!("mkdir {}: {e}", dest_dir.display()))?;
    let mut file = File::open(deb_path).map_err(|e| format!("open {}: {e}", deb_path.display()))?;
    let mut magic = [0u8; 8];
    file.read_exact(&mut magic)
        .map_err(|e| format!("read ar magic: {e}"))?;
    if &magic != b"!<arch>\n" {
        return Err("Invalid .deb AR archive".into());
    }

    let mut data_offset: Option<(u64, u64)> = None;
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
        if name == "data.tar.xz" {
            data_offset = Some((pos, size));
            break;
        }
        let step = size + (size & 1);
        pos += step;
        use std::io::Seek;
        file.seek(io::SeekFrom::Start(pos))
            .map_err(|e| format!("seek ar: {e}"))?;
    }

    let (off, sz) = data_offset.ok_or_else(|| "No data.tar.xz in .deb".to_string())?;
    use std::io::Seek;
    file.seek(io::SeekFrom::Start(off))
        .map_err(|e| format!("seek data.tar.xz: {e}"))?;

    let (tx, rx) = mpsc::sync_channel::<Result<Vec<u8>, String>>(24);
    let tx_err = tx.clone();
    let handle = thread::spawn(move || {
        let mut br = BufReader::with_capacity(65536, file.take(sz));
        let mut cw = ChanWriter {
            tx,
            buf: Vec::with_capacity(65536),
        };
        if let Err(e) = lzma_rs::xz_decompress(&mut br, &mut cw) {
            let _ = tx_err.send(Err(format!("xz_decompress: {e}")));
            return;
        }
        let _ = cw.flush();
    });

    let reader = ChanReader {
        rx,
        cur: Vec::new(),
        pos: 0,
        done: false,
    };
    let mut archive = tar::Archive::new(reader);
    let mut count: u64 = 0;
    let mut symlinks: Vec<(PathBuf, String)> = Vec::new();
    let mut modes: Vec<(PathBuf, u32)> = Vec::new();
    let r_prefix = runtime_prefix.trim_end_matches('/');

    let entries = archive
        .entries()
        .map_err(|e| format!("tar entries: {e}"))?;
    for entry_res in entries {
        let mut entry = entry_res.map_err(|e| format!("tar entry: {e}"))?;
        let raw_path = entry.path().map_err(|e| format!("entry path: {e}"))?.into_owned();
        let Some(rel) = clean_rel(&raw_path) else {
            continue;
        };
        let target_path = dest_dir.join(&rel);
        let etype = entry.header().entry_type();
        let mode = entry.header().mode().unwrap_or(0o644);

        if etype.is_dir() {
            fs::create_dir_all(&target_path)
                .map_err(|e| format!("mkdir {}: {e}", target_path.display()))?;
        } else if etype.is_symlink() {
            if let Ok(Some(link)) = entry.link_name() {
                let mut lstr = link.to_string_lossy().into_owned();
                if let Some(rest) = lstr.strip_prefix("/data/data/com.termux/files/usr/") {
                    lstr = format!("{r_prefix}/{rest}");
                }
                symlinks.push((target_path, lstr));
            }
        } else if etype.is_hard_link() {
            if let Ok(Some(link)) = entry.link_name() {
                if let Some(link_rel) = clean_rel(&link) {
                    let src = dest_dir.join(&link_rel);
                    if let Some(parent) = target_path.parent() {
                        let _ = fs::create_dir_all(parent);
                    }
                    let _ = fs::remove_file(&target_path);
                    if fs::hard_link(&src, &target_path).is_err() {
                        let _ = fs::copy(&src, &target_path);
                    }
                    count += 1;
                }
            }
        } else if etype.is_file() {
            if let Some(parent) = target_path.parent() {
                fs::create_dir_all(parent)
                    .map_err(|e| format!("mkdir parent: {e}"))?;
            }
            let _ = fs::remove_file(&target_path);
            let mut out = File::create(&target_path)
                .map_err(|e| format!("create {}: {e}", target_path.display()))?;
            io::copy(&mut entry, &mut out)
                .map_err(|e| format!("extract {}: {e}", target_path.display()))?;
            modes.push((target_path, mode));
            count += 1;
        }
    }

    for (dst, target) in symlinks {
        if let Some(parent) = dst.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let _ = fs::remove_file(&dst);
        if std::os::unix::fs::symlink(&target, &dst).is_ok() {
            count += 1;
        }
    }
    for (p, mode) in modes {
        let perms = fs::Permissions::from_mode(mode | 0o600);
        let _ = fs::set_permissions(&p, perms);
    }

    let _ = handle.join();
    Ok(count)
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
}
