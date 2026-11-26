use std::fs::{self, File, OpenOptions};
use std::io::{self, BufWriter, Write};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use wuwa_connect::deb_extract::extract_deb_with_list;

struct DualLog {
    file: Option<BufWriter<File>>,
}

impl DualLog {
    fn open(path: Option<&str>) -> Self {
        let file = path.and_then(|p| {
            if let Some(parent) = Path::new(p).parent() {
                let _ = fs::create_dir_all(parent);
            }
            OpenOptions::new()
                .create(true)
                .append(true)
                .open(p)
                .ok()
                .map(BufWriter::new)
        });
        Self { file }
    }

    fn line(&mut self, msg: &str) {
        let mut out = io::stdout().lock();
        let _ = writeln!(out, "{msg}");
        let _ = out.flush();
        if let Some(ref mut f) = self.file {
            let _ = writeln!(f, "{msg}");
            let _ = f.flush();
        }
    }
}

fn chmod_exec_tree(dir: &Path) {
    let Ok(rd) = fs::read_dir(dir) else { return; };
    for entry in rd.flatten() {
        let p = entry.path();
        if let Ok(meta) = fs::symlink_metadata(&p) {
            if meta.file_type().is_file() {
                let _ = fs::set_permissions(&p, fs::Permissions::from_mode(0o755));
            }
        }
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 4 || args[1] != "install" {
        eprintln!("Usage: sonoro-deb install <tc_dir> <manifest.tsv> [log_file]");
        std::process::exit(2);
    }

    let tc_dir = PathBuf::from(&args[2]);
    let tc_str = tc_dir.to_string_lossy().trim_end_matches('/').to_string();
    let manifest_path = &args[3];
    let log_path = args.get(4).map(|s| s.as_str());
    let mut log = DualLog::open(log_path);

    let Ok(manifest_text) = fs::read_to_string(manifest_path) else {
        log.line(&format!("dpkg: error reading manifest: {manifest_path}"));
        std::process::exit(1);
    };

    let stamp_dir = tc_dir.join(".pkg_stamps");
    let info_dir = tc_dir.join(".dpkg_info");
    if let Err(e) = fs::create_dir_all(&stamp_dir).and_then(|_| fs::create_dir_all(&info_dir)) {
        log.line(&format!("dpkg: failed creating metadata dirs: {e}"));
        std::process::exit(1);
    }

    struct PkgEntry {
        name: String,
        version: String,
        deb_path: String,
        stamp_key: String,
    }

    let mut entries = Vec::new();
    for raw in manifest_text.lines() {
        let line = raw.trim();
        if line.is_empty() {
            continue;
        }
        let cols: Vec<&str> = line.split('\t').collect();
        if cols.len() < 4 {
            continue;
        }
        entries.push(PkgEntry {
            name: cols[0].to_string(),
            version: cols[1].to_string(),
            deb_path: cols[2].to_string(),
            stamp_key: cols[3].to_string(),
        });
    }

    let mut unpacked_now: Vec<(String, String)> = Vec::new();
    for item in &entries {
        let stamp_file = stamp_dir.join(&item.stamp_key);
        if stamp_file.exists() {
            log.line(&format!(
                "{} is already the newest version ({}).",
                item.name,
                if item.version.is_empty() { "cached" } else { &item.version }
            ));
            continue;
        }

        let deb_name = Path::new(&item.deb_path)
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or(&item.deb_path);
        let ver_disp = if item.version.is_empty() { "latest" } else { &item.version };

        log.line(&format!("Selecting previously unselected package {}.", item.name));
        log.line(&format!("Preparing to unpack .../{deb_name} ..."));
        log.line(&format!("Unpacking {} ({ver_disp}) over (none) ...", item.name));

        match extract_deb_with_list(Path::new(&item.deb_path), &tc_dir, &tc_str) {
            Ok(files) => {
                let list_path = info_dir.join(format!("{}.list", item.name));
                let _ = fs::write(&list_path, files.join("\n"));
                let _ = fs::write(&stamp_file, ver_disp.as_bytes());
                unpacked_now.push((item.name.clone(), ver_disp.to_string()));
            }
            Err(e) => {
                log.line(&format!("dpkg: error processing archive {deb_name} (--unpack): {e}"));
                std::process::exit(1);
            }
        }
    }

    for (name, ver) in &unpacked_now {
        log.line(&format!("Setting up {name} ({ver}) ..."));
    }

    log.line(&format!("Configuring aarch64-cc wrapper in {tc_str}/bin ..."));
    let bin_dir = tc_dir.join("bin");
    let _ = fs::create_dir_all(&bin_dir);
    let cc_path = bin_dir.join("aarch64-cc");
    let cc_wrapper = format!(
        "#!/system/bin/sh\nTC=\"{tc_str}\"\nexport LD_LIBRARY_PATH=\"$TC/lib\"\nRES=\"\"\nfor d in \"$TC\"/lib/clang/*; do [ -d \"$d\" ] && RES=\"-resource-dir=$d\"; done\nN=$#\ni=0\nSKIP=0\nPREV_T=0\nwhile [ \"$i\" -lt \"$N\" ]; do\n  a=\"$1\"; shift\n  if [ \"$PREV_T\" = 1 ]; then\n    PREV_T=0\n    [ \"$a\" = \"aarch64-linux-android\" ] && a=\"aarch64-linux-android26\"\n  else\n    case \"$a\" in\n      --version|-V|-print-*) SKIP=1 ;;\n      --target=aarch64-linux-android) a=\"--target=aarch64-linux-android26\" ;;\n      -target) PREV_T=1 ;;\n    esac\n  fi\n  set -- \"$@\" \"$a\"\n  i=$((i + 1))\ndone\nif [ \"$SKIP\" = 1 ]; then\n  exec \"$TC/bin/clang\" \"$@\"\nfi\nexec \"$TC/bin/clang\" --target=aarch64-linux-android26 $RES --sysroot=\"$TC\" -Qunused-arguments -isystem \"$TC/include/aarch64-linux-android\" -isystem \"$TC/include\" -L\"$TC/lib\" -Wl,-rpath,\"$TC/lib\" -fuse-ld=lld \"$@\"\n"
    );
    if let Err(e) = fs::write(&cc_path, cc_wrapper.as_bytes()) {
        log.line(&format!("dpkg: error writing aarch64-cc: {e}"));
        std::process::exit(1);
    }

    chmod_exec_tree(&bin_dir);
    chmod_exec_tree(&tc_dir.join("lib"));
    if let Ok(rl) = fs::read_dir(tc_dir.join("lib").join("rustlib")) {
        for sub in rl.flatten() {
            chmod_exec_tree(&sub.path().join("bin"));
        }
    }
    log.line("dpkg: toolchain packages configured successfully");
}
