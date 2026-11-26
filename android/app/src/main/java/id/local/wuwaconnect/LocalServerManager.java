package id.local.wuwaconnect;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LocalServerManager {
    public static final String ASSETS_MANIFEST_URL =
        "https://raw.githubusercontent.com/kaaelix/sonoroserver/main/assets.json";
    public static final String APP_UPDATE_URL =
        "https://raw.githubusercontent.com/kaaelix/sonorolauncher/main/app.json";

    private static volatile boolean cancelled = false;
    private static volatile HttpURLConnection activeDownloadConn = null;
    private static volatile String lastDownloadErr = "";
    private static volatile Thread runtimeTailThread = null;
    private static final java.util.concurrent.atomic.AtomicBoolean runtimeTailStop = new java.util.concurrent.atomic.AtomicBoolean(true);

    public static void stopRuntimeLogTailer() {
        runtimeTailStop.set(true);
        Thread t = runtimeTailThread;
        runtimeTailThread = null;
        if (t != null) t.interrupt();
    }

    public static void startRuntimeLogTailer(LogCb log) {
        stopRuntimeLogTailer();
        if (log == null) return;
        runtimeTailStop.set(false);
        final String logsDir = new File(publicRoot(), "logs").getAbsolutePath();
        final String[] names = {"aki-logic", "cfgsv", "loginsv", "gamesv"};
        Thread t = new Thread(() -> {
            long[] pos = new long[names.length];
            File[] files = new File[names.length];
            for (int i = 0; i < names.length; i++) {
                files[i] = new File(logsDir, names[i] + ".log");
            }
            while (!runtimeTailStop.get() && !cancelled) {
                boolean readAny = false;
                for (int i = 0; i < names.length; i++) {
                    File f = files[i];
                    if (!f.exists()) continue;
                    long len = f.length();
                    if (len < pos[i]) pos[i] = 0;
                    if (len > pos[i]) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
                            raf.seek(pos[i]);
                            byte[] buf = new byte[(int) Math.min(16384, len - pos[i])];
                            int n = raf.read(buf);
                            if (n > 0) {
                                pos[i] += n;
                                readAny = true;
                                String chunk = new String(buf, 0, n, StandardCharsets.UTF_8)
                                    .replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
                                for (String ln : chunk.split("\n")) {
                                    String clean = ln.replace("\r", "").trim();
                                    if (!clean.isEmpty()) {
                                        log.onLog("[" + names[i] + "] " + clean);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                try {
                    Thread.sleep(readAny ? 200 : 500);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "sonoro-runtime-log-tail");
        t.setDaemon(true);
        runtimeTailThread = t;
        t.start();
    }

    public static void resetCancel() {
        cancelled = false;
    }

    public static boolean isCancelled() {
        return cancelled;
    }

    private static final String KILL_ALL_TREE_SH =
        "for pf in /data/local/tmp/sonoro/build.pid /data/local/tmp/sonoro/singbox.pid /data/local/tmp/sonoro/cargo.pid; do "
        + "  if [ -f \"$pf\" ]; then "
        + "    p=$(cat \"$pf\" 2>/dev/null); "
        + "    [ -n \"$p\" ] && [ \"$p\" != \"$$\" ] && [ \"$p\" != \"$PPID\" ] && kill -9 -\"$p\" \"$p\" 2>/dev/null || true; "
        + "    rm -f \"$pf\" 2>/dev/null || true; "
        + "  fi; "
        + "done; "
        + "pkill -9 -f '/[d]ata/local/tmp/sonoro/' 2>/dev/null || true; "
        + "for pat in '[c]argo' '[r]ustc' '[c]lang-21' '[r]ust-lld' '[l]d.lld' '[s]onoro-deb' '[s]onoro-cfgsv' '[s]onoro-loginsv' '[s]onoro-aki-logi' '[s]onoro-gamesv' '[b]uild_script_build' '[s]ing-box'; do "
        + "  pkill -9 -f \"$pat\" 2>/dev/null || true; "
        + "done; "
        + "for bin in cargo rustc clang clang-21 lld rust-lld aarch64-cc sonoro-deb sing-box sonoro-cfgsv sonoro-loginsv sonoro-aki-logic sonoro-aki-logi sonoro-gamesv build_script_build; do "
        + "  for pid in $(pidof \"$bin\" 2>/dev/null); do [ \"$pid\" != \"$$\" ] && [ \"$pid\" != \"$PPID\" ] && kill -9 \"$pid\" 2>/dev/null || true; done; "
        + "done; "
        + "true";

    private static final String KILL_SERVERS_SH =
        "for pat in '[s]onoro-aki-logi' '[s]onoro-cfgsv' '[s]onoro-loginsv' '[s]onoro-gamesv'; do "
        + "  pkill -9 -f \"$pat\" 2>/dev/null || true; "
        + "done; "
        + "for bin in sonoro-aki-logic sonoro-aki-logi sonoro-cfgsv sonoro-loginsv sonoro-gamesv; do "
        + "  for pid in $(pidof \"$bin\" 2>/dev/null); do [ \"$pid\" != \"$$\" ] && [ \"$pid\" != \"$PPID\" ] && kill -9 \"$pid\" 2>/dev/null || true; done; "
        + "done; "
        + "true";

    public static void requestCancel(int method) {
        cancelled = true;
        stopRuntimeLogTailer();
        HttpURLConnection c = activeDownloadConn;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
        PrivilegedHelper.destroyAllActiveProcesses();
        execPrivTimeout(method, KILL_ALL_TREE_SH, 12);
    }

    public static void cleanupOrphanedBuildIfPresent(int method) {
        String[] chk = execPrivTimeout(method,
            "[ -f " + EXEC_ROOT + "/build.pid ] && echo STALE || true", 8);
        if (chk[1] != null && chk[1].contains("STALE")) {
            execPrivTimeout(method, KILL_ALL_TREE_SH, 12);
        }
    }

    public static JSONObject checkAppUpdate() {
        String[] res = PrivilegedHelper.httpGet(APP_UPDATE_URL);
        if ("OK".equals(res[0])) {
            try {
                return new JSONObject(res[1]);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
    public static final String TERMUX_REPO_BASE =
        "https://packages.termux.dev/apt/termux-main/";
    public static final String[] TERMUX_MIRRORS = {
        "https://packages.termux.dev/apt/termux-main/",
        "https://packages-cf.termux.dev/apt/termux-main/"
    };
    public static final String TERMUX_PACKAGES_PATH =
        "dists/stable/main/binary-aarch64/Packages";

    public static final String EXEC_ROOT = "/data/local/tmp/sonoro";
    public static final String TC_DIR = EXEC_ROOT + "/tc";
    public static final String BIN_DIR = EXEC_ROOT + "/bin";
    public static final String SRC_STAGE = EXEC_ROOT + "/src";
    public static final String TARGET_DIR = EXEC_ROOT + "/target";
    public static final String AKI_SOCK = EXEC_ROOT + "/aki_logic.sock";
    public static final String TERMUX_USR = "/data/data/com.termux/files/usr";

    public interface LogCb {
        void onLog(String line);
    }

    private LocalServerManager() {}

    public static File publicRoot() {
        return new File(Environment.getExternalStorageDirectory(), "sonoro");
    }

    public static String publicRootPath() {
        return publicRoot().getAbsolutePath();
    }

    public static String stateDirPath() {
        return new File(publicRoot(), "state").getAbsolutePath();
    }

    public static String gamesvDbPath() {
        return new File(new File(publicRoot(), "state"), "gamesv.db").getAbsolutePath();
    }

    public static String proxyDirPath() {
        return new File(publicRoot(), "proxy").getAbsolutePath();
    }

    public static String assetsDbPath() {
        return new File(new File(publicRoot(), "assets"), "assets.db").getAbsolutePath();
    }

    public static String serverDirPath() {
        return new File(publicRoot(), "server").getAbsolutePath();
    }

    public static String backupsDirPath() {
        return new File(publicRoot(), "backups").getAbsolutePath();
    }

    public static File connectionsRootFile() {
        return new File(publicRoot(), "connections.json");
    }

    public static File connectionsStateFile() {
        return new File(new File(publicRoot(), "state"), "connections.json");
    }

    public static void writeExternalText(File f, String content) {
        if (f == null || !hasStorageAccess()) return;
        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
            }
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Throwable ignored) {
        }
    }

    public static String readExternalText(File f) {
        if (f == null || !hasStorageAccess()) return "";
        return readFileText(f);
    }

    public static boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    public static boolean ensureStorageAccess(Context ctx, int method) {
        if (hasStorageAccess()) return true;
        String pkg = ctx.getPackageName();
        String cmd = "appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true";
        if (method == 0) PrivilegedHelper.execRoot(cmd);
        else PrivilegedHelper.execShizuku(cmd);
        if (hasStorageAccess()) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Throwable ignored) {
        }
        return hasStorageAccess();
    }

    public static void ensureDirs(Context ctx) {
        File root = publicRoot();
        for (String sub : new String[]{"assets", "assets/scripts", "proxy", "state", "server", "toolchain", "logs", "backups"}) {
            File d = new File(root, sub);
            if (!d.exists()) d.mkdirs();
        }
        ensureDefaultProxyAssets(ctx);
    }

    public static void ensureDefaultProxyAssets(Context ctx) {
        File proxyDir = new File(publicRoot(), "proxy");
        if (!proxyDir.exists()) proxyDir.mkdirs();
        for (String name : new String[]{"server.crt", "server.key", "modified_3.8_index.json", "encrypted_3.8_index.b64"}) {
            File dst = new File(proxyDir, name);
            if (dst.exists() && dst.length() > 0) continue;
            try (InputStream is = ctx.getAssets().open(name);
                 FileOutputStream fos = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            } catch (Throwable ignored) {
            }
        }
    }

    public static JSONObject fetchManifest() {
        String[] res = PrivilegedHelper.httpGet(ASSETS_MANIFEST_URL);
        if ("OK".equals(res[0])) {
            try {
                return new JSONObject(res[1]);
            } catch (Throwable ignored) {
            }
        }
        File local = new File(serverDirPath(), "assets.json");
        if (local.exists()) {
            try {
                return new JSONObject(readFileText(local));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean syncServerProject(Context ctx, JSONObject manifest, LogCb log) {
        ensureDirs(ctx);
        if (manifest == null || !manifest.has("server")) {
            log.onLog("warn: assets.json manifest unavailable; checking /0/sonoro/server");
            return false;
        }
        JSONObject srv = manifest.optJSONObject("server");
        if (srv == null) return false;
        JSONObject files = srv.optJSONObject("files");
        if (files == null) return false;

        final String remoteVer = srv.optString("version", "");
        final List<String> bases = new ArrayList<>();
        JSONArray rb = srv.optJSONArray("raw_bases");
        if (rb != null) {
            for (int i = 0; i < rb.length(); i++) {
                String b = rb.optString(i, "").trim();
                if (!b.isEmpty()) bases.add(b);
            }
        }
        if (bases.isEmpty()) bases.add("https://raw.githubusercontent.com/kaaelix/sonoroserver/main");

        final File srvRoot = new File(serverDirPath());
        log.onLog("$ git-sync sonoroserver -> " + srvRoot.getAbsolutePath() + (remoteVer.isEmpty() ? "" : " (v" + remoteVer + ")"));

        Iterator<String> keys = files.keys();
        final List<String> toDownload = new ArrayList<>();
        while (keys.hasNext()) {
            String rel = keys.next();
            if (rel.startsWith("assets/BinData/") || "assets/assets.db".equals(rel)) continue;
            String expectedSha = files.optString(rel, "").toLowerCase(Locale.ROOT);
            File dest = new File(srvRoot, rel);
            if (dest.exists() && !expectedSha.isEmpty()) {
                String actual = stripOk(RustCore.sha256File(dest.getAbsolutePath()));
                if (actual.equalsIgnoreCase(expectedSha)) continue;
            }
            toDownload.add(rel);
        }

        if (toDownload.isEmpty()) {
            if (!remoteVer.isEmpty()) writeFileText(new File(srvRoot, ".server.version"), remoteVer);
            writeFileText(new File(srvRoot, "assets.json"), manifest.toString());
            if (!dirtyBinsFile().exists()) {
                writeDirtyBins(Collections.emptySet());
            }
            log.onLog("Already up to date (v" + remoteVer + ")");
            return false;
        }

        final int needCount = toDownload.size();
        log.onLog("sync: fetching " + needCount + " updated files (6 workers)...");
        final java.util.concurrent.atomic.AtomicBoolean rustChanged = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Set<String> dirtyDelta = Collections.synchronizedSet(new LinkedHashSet<>());
        final java.util.concurrent.atomic.AtomicInteger doneCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger okCount = new java.util.concurrent.atomic.AtomicInteger(0);
        long syncStartMs = System.currentTimeMillis();

        ExecutorService syncPool = Executors.newFixedThreadPool(6);
        List<Future<?>> jobs = new ArrayList<>(needCount);
        try {
            for (final String rel : toDownload) {
                jobs.add(syncPool.submit(() -> {
                    if (cancelled) return;
                    String expectedSha = files.optString(rel, "").toLowerCase(Locale.ROOT);
                    File dest = new File(srvRoot, rel);
                    boolean ok = false;
                    for (String base : bases) {
                        if (cancelled) return;
                        String u = base.replaceAll("/+$", "") + "/" + rel;
                        if (downloadFile(u, dest, expectedSha, null)) {
                            ok = true;
                            break;
                        }
                    }
                    int cur = doneCount.incrementAndGet();
                    if (!ok) {
                        log.onLog("warn: failed syncing " + rel);
                        return;
                    }
                    okCount.incrementAndGet();
                    Set<String> hit = new LinkedHashSet<>();
                    collectAffectedBins(rel, hit);
                    if (!hit.isEmpty()) {
                        dirtyDelta.addAll(hit);
                        rustChanged.set(true);
                    }
                    if (rel.startsWith("proxy/") || rel.startsWith("assets/scripts/")) {
                        copyFile(dest, new File(publicRoot(), rel));
                    }
                    if (cur == 1 || cur % 25 == 0 || cur == needCount) {
                        int pct = (cur * 100) / Math.max(1, needCount);
                        log.onLog("sync: [" + cur + "/" + needCount + " · " + pct + "%] " + rel);
                    }
                }));
            }
            for (Future<?> f : jobs) {
                if (cancelled) break;
                try {
                    f.get();
                } catch (Throwable ignored) {
                }
            }
        } finally {
            syncPool.shutdownNow();
        }

        if (cancelled) return false;
        if (!remoteVer.isEmpty()) {
            writeFileText(new File(srvRoot, ".server.version"), remoteVer);
        }
        writeFileText(new File(srvRoot, "assets.json"), manifest.toString());
        markDirtyBins(dirtyDelta);
        double sec = Math.max(0.1, (System.currentTimeMillis() - syncStartMs) / 1000.0);
        if (!dirtyDelta.isEmpty()) {
            log.onLog("Affected binaries: " + String.join(", ", dirtyDelta));
        }
        log.onLog(String.format(Locale.US, "Synced %d/%d server files in %.1fs (v%s)",
            okCount.get(), needCount, sec, remoteVer));
        return rustChanged.get();
    }

    private static String asciiBar(int pct) {
        int filled = Math.max(0, Math.min(20, (pct * 20) / 100));
        StringBuilder sb = new StringBuilder(22);
        sb.append('[');
        for (int i = 0; i < 20; i++) sb.append(i < filled ? '#' : '.');
        sb.append(']');
        return sb.toString();
    }

    public static boolean ensureAssetsDb(JSONObject manifest, LogCb log) {
        if (manifest == null) {
            File f = new File(assetsDbPath());
            return f.exists() && f.length() > 0;
        }
        String targetVer = manifest.optString("version", "");
        String targetSha = manifest.optString("sha256", "").toLowerCase(Locale.ROOT);
        File dbFile = new File(assetsDbPath());
        File verFile = new File(new File(publicRoot(), "assets"), "assets.version.json");

        if (dbFile.exists() && dbFile.length() > 0 && verFile.exists()) {
            try {
                JSONObject vf = new JSONObject(readFileText(verFile));
                if (vf.optLong("size", -1) == dbFile.length()
                    && targetSha.equalsIgnoreCase(vf.optString("sha256", ""))) {
                    log.onLog("assets.db: already latest (v" + targetVer + " · sha256 ok)");
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (dbFile.exists() && dbFile.length() > 0 && !targetSha.isEmpty()) {
            log.onLog("$ sha256sum " + dbFile.getAbsolutePath());
            String curSha = stripOk(RustCore.sha256File(dbFile.getAbsolutePath()));
            if (curSha.equalsIgnoreCase(targetSha)) {
                saveAssetsMeta(verFile, targetVer, curSha, dbFile.length());
                log.onLog("assets.db: sha256 verified (v" + targetVer + ")");
                return true;
            }
        }

        List<String> urls = new ArrayList<>();
        String u0 = manifest.optString("url", "").trim();
        if (!u0.isEmpty()) urls.add(u0);
        JSONArray mir = manifest.optJSONArray("mirrors");
        if (mir != null) {
            for (int i = 0; i < mir.length(); i++) {
                String m = mir.optString(i, "").trim();
                if (!m.isEmpty()) urls.add(m);
            }
        }
        if (urls.isEmpty()) {
            log.onLog("E: No assets.db URL in assets.json");
            return dbFile.exists() && dbFile.length() > 0;
        }

        long expectedSize = manifest.optLong("size", 0);
        double mb = expectedSize / (1024.0 * 1024.0);
        for (String url : urls) {
            log.onLog("$ curl -fL -C - \"" + url + "\" -o " + dbFile.getAbsolutePath());
            log.onLog(String.format(Locale.US, "  size: %.1f MB · version: v%s", mb, targetVer));
            final long startMs = System.currentTimeMillis();
            final long[] lastLogMs = {0L};
            final long[] startOffset = {-1L};
            if (downloadFileWithResume(url, dbFile, targetSha, true, (done, total) -> {
                if (startOffset[0] < 0) startOffset[0] = done;
                long now = System.currentTimeMillis();
                if (now - lastLogMs[0] >= 1200) {
                    lastLogMs[0] = now;
                    double dMb = done / (1024.0 * 1024.0);
                    double tMb = total > 0 ? total / (1024.0 * 1024.0) : mb;
                    double elapsed = Math.max(0.25, (now - startMs) / 1000.0);
                    double speedBps = Math.max(0, done - Math.max(0, startOffset[0])) / elapsed;
                    double speedMb = speedBps / (1024.0 * 1024.0);
                    int pct = tMb > 0 ? (int) Math.min(99, Math.round((dMb * 100.0) / tMb)) : 0;
                    long etaSec = (speedBps > 1024 && total > done) ? (long) ((total - done) / speedBps) : -1;
                    String etaStr = etaSec >= 0 ? (" eta " + etaSec + "s") : "";
                    log.onLog(String.format(Locale.US, "  %s %3d%%  %.1f / %.1f MB  %.1f MB/s%s",
                        asciiBar(pct), pct, dMb, tMb, speedMb, etaStr));
                }
            })) {
                saveAssetsMeta(verFile, targetVer, targetSha, dbFile.length());
                double totalSec = Math.max(0.2, (System.currentTimeMillis() - startMs) / 1000.0);
                double fMb = dbFile.length() / (1024.0 * 1024.0);
                log.onLog(String.format(Locale.US, "  %s 100%%  %.1f MB in %.0fs (%.1f MB/s) · sha256 ok",
                    asciiBar(100), fMb, Math.ceil(totalSec), fMb / totalSec));
                return true;
            }
            if (cancelled) return false;
            log.onLog("warn: mirror failed (" + lastDownloadErr + "), trying next...");
        }
        log.onLog("E: Failed downloading assets.db");
        return false;
    }

    public static String checkToolchainReady(int method) {
        String[] res = execPrivTimeout(method,
            "LD_LIBRARY_PATH=" + TC_DIR + "/lib " + TC_DIR + "/bin/rustc --version 2>/dev/null; "
                + "LD_LIBRARY_PATH=" + TC_DIR + "/lib " + TC_DIR + "/bin/cargo --version 2>/dev/null; "
                + "[ -x " + TC_DIR + "/bin/aarch64-cc ] && echo CC_OK", 45);
        if ("0".equals(res[0].trim()) && res[1].contains("rustc") && res[1].contains("cargo") && res[1].contains("CC_OK")) {
            return TC_DIR;
        }
        if (method == 0) {
            String[] tRes = PrivilegedHelper.execRootTimeout(
                "LD_LIBRARY_PATH=" + TERMUX_USR + "/lib " + TERMUX_USR + "/bin/rustc --version 2>/dev/null; "
                    + "LD_LIBRARY_PATH=" + TERMUX_USR + "/lib " + TERMUX_USR + "/bin/cargo --version 2>/dev/null; "
                    + "LD_LIBRARY_PATH=" + TERMUX_USR + "/lib " + TERMUX_USR + "/bin/clang --version 2>/dev/null; "
                    + "([ -x " + TERMUX_USR + "/bin/llvm-ar ] || [ -x " + TERMUX_USR + "/bin/ar ]) && echo AR_OK", 45);
            if ("0".equals(tRes[0].trim()) && tRes[1].contains("rustc") && tRes[1].contains("cargo")
                && tRes[1].contains("clang") && tRes[1].contains("AR_OK")) {
                return TERMUX_USR;
            }
        }
        return null;
    }

    private static String safeCacheFileName(String name) {
        if (name == null || name.isEmpty()) return "pkg.deb";
        return name.replaceAll("[^a-zA-Z0-9._+-]", "_");
    }

    private static String pkgStampKey(String name, String sha256) {
        String shortSha = (sha256 != null && sha256.length() >= 12) ? sha256.substring(0, 12) : "nosha";
        return safeCacheFileName(name) + "_" + shortSha;
    }

    public static String ensureRustToolchain(Context ctx, int method, LogCb log) {
        String ready = checkToolchainReady(method);
        if (ready != null) {
            log.onLog("Rust compiler ready (" + (ready.equals(TERMUX_USR) ? "Termux" : "Local") + ")");
            return ready;
        }

        log.onLog("$ pkg update && pkg install -y rust clang");
        String[] idxRes = null;
        String activeMirror = TERMUX_REPO_BASE;
        for (String mirror : TERMUX_MIRRORS) {
            log.onLog("Get:1 " + mirror + " stable InRelease");
            String[] tryIdx = PrivilegedHelper.httpGet(mirror + TERMUX_PACKAGES_PATH);
            if ("OK".equals(tryIdx[0])) {
                idxRes = tryIdx;
                activeMirror = mirror;
                break;
            }
        }
        if (idxRes == null) {
            log.onLog("Err: Failed to fetch ARM64 package index");
            return null;
        }
        log.onLog("Reading package lists... Done");
        log.onLog("Building dependency tree... Done");

        JSONArray pkgs;
        try {
            pkgs = new JSONArray(RustCore.resolveDebPackages(idxRes[1], "rust,clang"));
        } catch (Throwable t) {
            log.onLog("Resolve packages error: " + t.getMessage());
            return null;
        }
        if (pkgs.length() == 0) {
            log.onLog("E: Unable to locate packages rust, clang");
            return null;
        }

        final int nPkgs = pkgs.length();
        long totalBytes = 0;
        StringBuilder pkgNames = new StringBuilder();
        for (int i = 0; i < nPkgs; i++) {
            JSONObject p = pkgs.optJSONObject(i);
            totalBytes += p.optLong("size", 0);
            if (i > 0) pkgNames.append(' ');
            pkgNames.append(p.optString("name", ""));
        }
        log.onLog("The following NEW packages will be installed:");
        log.onLog("  " + pkgNames.toString());
        log.onLog("0 upgraded, " + nPkgs + " newly installed, 0 to remove.");
        log.onLog(String.format(Locale.US, "Need to get %.1f MB of archives.", totalBytes / (1024.0 * 1024.0)));

        File tcCacheDir = new File(publicRoot(), "toolchain");
        if (!tcCacheDir.exists()) tcCacheDir.mkdirs();
        File fallbackCacheDir = new File(ctx.getCacheDir(), "tc_debs");
        if (!fallbackCacheDir.exists()) fallbackCacheDir.mkdirs();

        String stampDir = TC_DIR + "/.pkg_stamps";
        execPriv(method, "mkdir -p " + TC_DIR + " " + BIN_DIR + " " + TARGET_DIR + " " + stampDir);
        String[] stampRes = execPriv(method, "ls -1 " + stampDir + " 2>/dev/null || true");
        String existingStamps = "\n" + (stampRes[1] != null ? stampRes[1] : "") + "\n";

        String debBinRes = PrivilegedHelper.stageExecAsset(ctx, "sonoro-deb", method == 0);
        if (!debBinRes.startsWith("OK:")) {
            log.onLog("dpkg: failed preparing sonoro-deb: " + debBinRes);
            return null;
        }
        final String debBinPath = debBinRes.substring(3);

        // Clean any old stage dir from previous versions
        File oldStageDir = new File(ctx.getCacheDir(), "tc_stage");
        if (oldStageDir.exists()) execPriv(method, "rm -rf \"" + oldStageDir.getAbsolutePath() + "\"");

        final File[] resolvedDebs = new File[nPkgs];
        final String[] pkgVersions = new String[nPkgs];
        final String[] pkgStamps = new String[nPkgs];
        final boolean[] alreadyDone = new boolean[nPkgs];
        final java.util.concurrent.atomic.AtomicLong bytesToFetch = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicLong bytesFetched = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicInteger dlSeq = new java.util.concurrent.atomic.AtomicInteger(0);
        final String primaryMirror = activeMirror;
        final java.util.concurrent.atomic.AtomicReference<String> firstFail = new java.util.concurrent.atomic.AtomicReference<>(null);

        @SuppressWarnings("unchecked")
        Future<File>[] dlFutures = new Future[nPkgs];
        ExecutorService dlPool = Executors.newFixedThreadPool(4);
        long dlStartMs = System.currentTimeMillis();
        final java.util.concurrent.atomic.AtomicBoolean progStop = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread progThread = null;

        try {
            for (int i = 0; i < nPkgs; i++) {
                final int idx = i;
                final JSONObject pkg = pkgs.optJSONObject(i);
                final String name = pkg.optString("name", "");
                final String version = pkg.optString("version", "");
                final String filename = pkg.optString("filename", "");
                final long size = pkg.optLong("size", 0);
                final String sha256 = pkg.optString("sha256", "").toLowerCase(Locale.ROOT);
                final String stampKey = pkgStampKey(name, sha256);
                pkgVersions[i] = version;
                pkgStamps[i] = stampKey;

                String rawBase = new File(filename).getName();
                String safeBase = safeCacheFileName(rawBase);
                File debFile = new File(tcCacheDir, safeBase);
                File rawDebFile = rawBase.equals(safeBase) ? null : new File(tcCacheDir, rawBase);
                File fbDebFile = new File(fallbackCacheDir, safeBase);

                if (existingStamps.contains("\n" + stampKey + "\n")) {
                    alreadyDone[i] = true;
                    resolvedDebs[i] = debFile;
                    continue;
                }

                for (File cand : new File[]{debFile, rawDebFile, fbDebFile}) {
                    if (cand != null && cand.exists() && cand.length() > 0 && !sha256.isEmpty()) {
                        String actual = stripOk(RustCore.sha256File(cand.getAbsolutePath()));
                        if (actual.equalsIgnoreCase(sha256)) {
                            resolvedDebs[i] = cand;
                            break;
                        }
                    }
                }
                if (resolvedDebs[i] != null) continue;

                bytesToFetch.addAndGet(size);
                dlFutures[i] = dlPool.submit(() -> {
                    if (cancelled) return null;
                    int seq = dlSeq.incrementAndGet();
                    String verTag = version.isEmpty() ? "" : (" " + version);
                    log.onLog(String.format(Locale.US, "Get:%d %s %s%s [%.1f MB]",
                        seq, primaryMirror, name, verTag, size / (1024.0 * 1024.0)));
                    final long[] lastSeen = {0L};
                    DownloadProgress cb = (done, tot) -> {
                        long delta = done - lastSeen[0];
                        if (delta > 0) {
                            lastSeen[0] = done;
                            bytesFetched.addAndGet(delta);
                        }
                    };
                    for (String mir : TERMUX_MIRRORS) {
                        if (cancelled) return null;
                        String url = mir + filename;
                        if (downloadFile(url, debFile, sha256, cb)) return debFile;
                        if (method == 0 && downloadFile(url, fbDebFile, sha256, cb)) return fbDebFile;
                        // reset counter delta if retrying package on next mirror
                        if (lastSeen[0] > 0) {
                            bytesFetched.addAndGet(-lastSeen[0]);
                            lastSeen[0] = 0L;
                        }
                    }
                    firstFail.compareAndSet(null, name + " (" + lastDownloadErr + ")");
                    return null;
                });
            }

            if (bytesToFetch.get() > 0) {
                progThread = new Thread(() -> {
                    while (!progStop.get() && !cancelled) {
                        try {
                            Thread.sleep(1200);
                        } catch (InterruptedException ie) {
                            break;
                        }
                        if (progStop.get() || cancelled) break;
                        long need = bytesToFetch.get();
                        long got = Math.min(need, bytesFetched.get());
                        if (got > 0 && got < need) {
                            double sec = Math.max(0.2, (System.currentTimeMillis() - dlStartMs) / 1000.0);
                            double spd = (got / (1024.0 * 1024.0)) / sec;
                            int pct = (int) Math.min(99, (got * 100L) / Math.max(1L, need));
                            log.onLog(String.format(Locale.US, "apt: %.1f / %.1f MB (%d%% · %.1f MB/s)",
                                got / (1024.0 * 1024.0), need / (1024.0 * 1024.0), pct, spd));
                        }
                    }
                }, "sonoro-apt-prog");
                progThread.setDaemon(true);
                progThread.start();
            }

            for (int i = 0; i < nPkgs; i++) {
                if (dlFutures[i] == null) continue;
                try {
                    resolvedDebs[i] = dlFutures[i].get();
                } catch (Throwable ignored) {
                }
                if (resolvedDebs[i] == null || cancelled) {
                    progStop.set(true);
                    if (!cancelled) {
                        String failed = firstFail.get();
                        log.onLog("E: Failed to fetch " + (failed != null ? failed : pkgs.optJSONObject(i).optString("name", "")));
                    }
                    return null;
                }
            }
            progStop.set(true);
            if (bytesToFetch.get() > 0) {
                double sec = Math.max(0.2, (System.currentTimeMillis() - dlStartMs) / 1000.0);
                double mb = bytesToFetch.get() / (1024.0 * 1024.0);
                log.onLog(String.format(Locale.US, "Fetched %.1f MB in %.0fs (%.1f MB/s)", mb, Math.ceil(sec), mb / sec));
            }
        } finally {
            progStop.set(true);
            if (progThread != null) progThread.interrupt();
            dlPool.shutdownNow();
        }

        StringBuilder manifestTsv = new StringBuilder();
        for (int i = 0; i < nPkgs; i++) {
            String name = pkgs.optJSONObject(i).optString("name", "");
            manifestTsv.append(name).append('\t')
                .append(pkgVersions[i]).append('\t')
                .append(resolvedDebs[i].getAbsolutePath()).append('\t')
                .append(pkgStamps[i]).append('\n');
        }
        File manFile = new File(tcCacheDir, ".install_manifest.tsv");
        writeFileText(manFile, manifestTsv.toString());
        File dpkgLog = new File(new File(publicRoot(), "logs"), "dpkg.log");
        writeFileText(dpkgLog, "");

        final java.util.concurrent.atomic.AtomicBoolean dpkgTailStop = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicInteger printedLines = new java.util.concurrent.atomic.AtomicInteger(0);
        Thread dpkgTailer = new Thread(() -> {
            long pos = 0;
            StringBuilder carry = new StringBuilder();
            while (!dpkgTailStop.get()) {
                try {
                    long len = dpkgLog.exists() ? dpkgLog.length() : 0;
                    if (len > pos) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(dpkgLog, "r")) {
                            raf.seek(pos);
                            byte[] buf = new byte[(int) Math.min(8192, len - pos)];
                            int n = raf.read(buf);
                            if (n > 0) {
                                pos += n;
                                carry.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                                int nl;
                                while ((nl = carry.indexOf("\n")) != -1) {
                                    String ln = carry.substring(0, nl).replace("\r", "").trim();
                                    carry.delete(0, nl + 1);
                                    if (!ln.isEmpty()) {
                                        printedLines.incrementAndGet();
                                        log.onLog(ln);
                                    }
                                }
                            }
                        }
                    }
                    Thread.sleep(100);
                } catch (Throwable ignored) {
                    try { Thread.sleep(120); } catch (InterruptedException ie) { break; }
                }
            }
        }, "sonoro-dpkg-tail");
        dpkgTailer.setDaemon(true);
        dpkgTailer.start();

        String installCmd = "\"" + debBinPath + "\" install \"" + TC_DIR + "\" \"" + manFile.getAbsolutePath() + "\" \"" + dpkgLog.getAbsolutePath() + "\"";
        log.onLog("$ " + debBinPath + " install " + TC_DIR + " (" + nPkgs + " pkgs)");
        String[] instRes;
        try {
            instRes = execPrivTimeout(method, installCmd, 600);
        } finally {
            dpkgTailStop.set(true);
            dpkgTailer.interrupt();
        }
        // Flush any lines from stdout that weren't tailed via dpkg.log (e.g. if Shizuku blocked writing /sdcard log)
        if (printedLines.get() == 0 && instRes[1] != null) {
            for (String ln : instRes[1].split("\n")) {
                String clean = ln.trim();
                if (!clean.isEmpty()) log.onLog(clean);
            }
        }
        if (!"0".equals(instRes[0].trim())) {
            String err = instRes[2].trim();
            if (err.isEmpty()) err = instRes[1].trim();
            log.onLog("E: Sub-process sonoro-deb returned error (" + instRes[0].trim() + "): " + err);
            return null;
        }

        ready = checkToolchainReady(method);
        if (ready == null) {
            log.onLog("E: Rust compiler verification failed after dpkg setup");
            return null;
        }
        log.onLog("rustc & cargo ready in " + ready);
        return ready;
    }

    private static final String[] SERVER_BINS = {
        "sonoro-cfgsv", "sonoro-loginsv", "sonoro-aki-logic", "sonoro-gamesv"
    };

    public static String binCacheDirPath() {
        return new File(new File(publicRoot(), "state"), "bins").getAbsolutePath();
    }

    private static File dirtyBinsFile() {
        return new File(new File(publicRoot(), "state"), "dirty_bins.txt");
    }

    private static void collectAffectedBins(String rel, Set<String> out) {
        if (rel == null || rel.isEmpty()) return;
        String p = rel.startsWith("./") ? rel.substring(2) : rel;
        if (p.equals("Cargo.toml") || p.equals("Cargo.lock") || p.startsWith(".cargo/")
            || p.startsWith("common/") || p.startsWith("build/")
            || p.equals("src/lib.rs") || p.equals("src/main.rs")) {
            Collections.addAll(out, SERVER_BINS);
            return;
        }
        if (p.startsWith("cfgsv/")) {
            out.add("sonoro-cfgsv");
            return;
        }
        if (p.startsWith("loginsv/")) {
            out.add("sonoro-loginsv");
            return;
        }
        if (p.startsWith("gamesv/")) {
            out.add("sonoro-gamesv");
            return;
        }
        if (p.startsWith("services/aki-logic/") || p.startsWith("proto/")) {
            out.add("sonoro-aki-logic");
            out.add("sonoro-gamesv");
            return;
        }
        if (p.endsWith(".rs") || p.endsWith("Cargo.toml") || p.endsWith("Cargo.lock")) {
            Collections.addAll(out, SERVER_BINS);
        }
    }

    private static Set<String> readDirtyBins() {
        Set<String> set = new LinkedHashSet<>();
        File f = dirtyBinsFile();
        if (!f.exists()) return set;
        String raw = readFileText(f);
        for (String line : raw.split("\\s+")) {
            String t = line.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private static void writeDirtyBins(Collection<String> dirty) {
        File f = dirtyBinsFile();
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        if (dirty == null || dirty.isEmpty()) {
            writeFileText(f, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String b : dirty) {
            if (b != null && !b.trim().isEmpty()) {
                sb.append(b.trim()).append('\n');
            }
        }
        writeFileText(f, sb.toString());
    }

    private static void markDirtyBins(Collection<String> add) {
        Set<String> merged = readDirtyBins();
        if (add != null) merged.addAll(add);
        writeDirtyBins(merged);
    }

    public static boolean areServerBinsBuilt(int method, String targetVer) {
        Set<String> dirty = readDirtyBins();
        if (!dirty.isEmpty()) {
            return false;
        }
        boolean dirtyTracked = dirtyBinsFile().exists();
        String checkCmd = "for b in sonoro-aki-logic sonoro-cfgsv sonoro-loginsv sonoro-gamesv; do "
            + "[ -x " + BIN_DIR + "/$b ] || exit 1; done; "
            + "cat " + BIN_DIR + "/.built.version 2>/dev/null || true";
        String[] res = execPriv(method, checkCmd);
        if ("0".equals(res[0].trim())) {
            String builtVer = res[1].trim();
            if (dirtyTracked || targetVer == null || targetVer.isEmpty() || targetVer.equals(builtVer)) {
                return true;
            }
        }
        // If /data/local/tmp was cleared across a reboot, restore cached binaries from /0/sonoro/state/bins
        File cacheDir = new File(binCacheDirPath());
        File verFile = new File(cacheDir, ".built.version");
        boolean cacheReady = cacheDir.isDirectory();
        for (String b : SERVER_BINS) {
            File f = new File(cacheDir, b);
            if (!f.exists() || f.length() == 0) {
                cacheReady = false;
                break;
            }
        }
        if (cacheReady) {
            String cachedVer = verFile.exists() ? readFileText(verFile).trim() : "";
            if (dirtyTracked || targetVer == null || targetVer.isEmpty() || targetVer.equals(cachedVer)) {
                String stamp = (targetVer != null && !targetVer.isEmpty()) ? targetVer : cachedVer;
                String cleanStamp = stamp.replace("'", "");
                String restoreCmd = "mkdir -p " + BIN_DIR + " && "
                    + "for b in sonoro-aki-logic sonoro-cfgsv sonoro-loginsv sonoro-gamesv; do "
                    + "cp -f \"" + cacheDir.getAbsolutePath() + "/$b\" \"" + BIN_DIR + "/$b\" && chmod 755 \"" + BIN_DIR + "/$b\" && "
                    + "printf '%s' '" + cleanStamp + "' > \"" + BIN_DIR + "/.$b.ver\"; done && "
                    + "printf '%s' '" + cleanStamp + "' > " + BIN_DIR + "/.built.version";
                String[] rRes = execPriv(method, restoreCmd);
                if ("0".equals(rRes[0].trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean buildLocalServer(Context ctx, int method, boolean forceRebuild, LogCb log) {
        String verFile = new File(serverDirPath(), ".server.version").getAbsolutePath();
        String targetVer = new File(verFile).exists() ? readFileText(new File(verFile)).trim() : "";
        if (forceRebuild) {
            Set<String> all = new LinkedHashSet<>(Arrays.asList(SERVER_BINS));
            writeDirtyBins(all);
        } else if (areServerBinsBuilt(method, targetVer)) {
            log.onLog("Server binaries up to date");
            return true;
        }

        Set<String> dirty = readDirtyBins();
        boolean dirtyTracked = dirtyBinsFile().exists();
        String cleanVer = targetVer.replace("'", "");
        String binCache = binCacheDirPath();

        // If dirty tracking shows no Rust crates need rebuilding and all 4 binaries exist, stamp & skip toolchain setup.
        if (!forceRebuild && dirtyTracked && dirty.isEmpty()) {
            String fastRestore = "set -e\n"
                + "mkdir -p " + BIN_DIR + " \"" + binCache + "\"\n"
                + "for pkg in sonoro-cfgsv sonoro-loginsv sonoro-aki-logic sonoro-gamesv; do\n"
                + "  if [ ! -x \"" + BIN_DIR + "/$pkg\" ]; then\n"
                + "    [ -s \"" + binCache + "/$pkg\" ] || exit 1\n"
                + "    cp -f \"" + binCache + "/$pkg\" \"" + BIN_DIR + "/$pkg\" && chmod 755 \"" + BIN_DIR + "/$pkg\"\n"
                + "  fi\n"
                + "  printf '%s' '" + cleanVer + "' > \"" + BIN_DIR + "/.$pkg.ver\"\n"
                + "  printf '%s' '" + cleanVer + "' > \"" + binCache + "/.$pkg.ver\" 2>/dev/null || true\n"
                + "done\n"
                + "printf '%s' '" + cleanVer + "' > \"" + BIN_DIR + "/.built.version\"\n"
                + "printf '%s' '" + cleanVer + "' > \"" + binCache + "/.built.version\" 2>/dev/null || true\n";
            if ("0".equals(execPriv(method, fastRestore)[0].trim())) {
                log.onLog("Server binaries up to date (no Rust crates dirty)");
                return true;
            }
        }

        String tc = ensureRustToolchain(ctx, method, log);
        if (tc == null) return false;

        String dirtySpace = String.join(" ", dirty).replace("'", "");
        log.onLog("$ cd " + SRC_STAGE + " && cargo build --release (per-binary fast mode"
            + (!dirtySpace.isEmpty() && !forceRebuild ? ", dirty: " + dirtySpace : "") + ")");
        String ccBin = tc.equals(TERMUX_USR) ? (tc + "/bin/clang") : (tc + "/bin/aarch64-cc");
        String logOut = new File(new File(publicRoot(), "logs"), "build.log").getAbsolutePath();
        writeFileText(new File(logOut), "");
        final java.util.concurrent.atomic.AtomicBoolean tailStop = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread tailer = new Thread(() -> {
            long pos = 0;
            File lf = new File(logOut);
            StringBuilder carry = new StringBuilder();
            while (!tailStop.get()) {
                try {
                    long len = lf.exists() ? lf.length() : 0;
                    if (len < pos) pos = 0;
                    if (len > pos) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(lf, "r")) {
                            raf.seek(pos);
                            byte[] buf = new byte[(int) Math.min(16384, len - pos)];
                            int n = raf.read(buf);
                            if (n > 0) {
                                pos += n;
                                String chunk = new String(buf, 0, n, StandardCharsets.UTF_8)
                                    .replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
                                carry.append(chunk);
                                int nl;
                                while ((nl = carry.indexOf("\n")) != -1) {
                                    String line = carry.substring(0, nl).replace("\r", "");
                                    carry.delete(0, nl + 1);
                                    if (!line.trim().isEmpty()) {
                                        log.onLog(line);
                                    }
                                }
                            }
                        }
                    }
                    Thread.sleep(len > pos ? 150 : 450);
                } catch (Throwable ignored) {
                    try { Thread.sleep(450); } catch (InterruptedException ie) { break; }
                }
            }
            if (carry.length() > 0 && !carry.toString().trim().isEmpty()) {
                log.onLog(carry.toString().replace("\r", "").trim());
            }
        }, "sonoro-build-tail");
        tailer.setDaemon(true);
        tailer.start();

        String fixCc = !tc.equals(TC_DIR) ? "" : (
            "cat > \"" + TC_DIR + "/bin/aarch64-cc\" <<'SONORO_CC_EOF'\n"
            + "#!/system/bin/sh\n"
            + "TC=\"" + TC_DIR + "\"\n"
            + "export LD_LIBRARY_PATH=\"$TC/lib\"\n"
            + "RES=\"\"\n"
            + "for d in \"$TC\"/lib/clang/*; do [ -d \"$d\" ] && RES=\"-resource-dir=$d\"; done\n"
            + "N=$#\ni=0\nSKIP=0\nPREV_T=0\n"
            + "while [ \"$i\" -lt \"$N\" ]; do\n"
            + "  a=\"$1\"; shift\n"
            + "  if [ \"$PREV_T\" = 1 ]; then\n"
            + "    PREV_T=0\n"
            + "    [ \"$a\" = \"aarch64-linux-android\" ] && a=\"aarch64-linux-android26\"\n"
            + "  else\n"
            + "    case \"$a\" in\n"
            + "      --version|-V|-print-*) SKIP=1 ;;\n"
            + "      --target=aarch64-linux-android) a=\"--target=aarch64-linux-android26\" ;;\n"
            + "      -target) PREV_T=1 ;;\n"
            + "    esac\n"
            + "  fi\n"
            + "  set -- \"$@\" \"$a\"\n"
            + "  i=$((i + 1))\n"
            + "done\n"
            + "if [ \"$SKIP\" = 1 ]; then\n"
            + "  exec \"$TC/bin/clang\" \"$@\"\n"
            + "fi\n"
            + "exec \"$TC/bin/clang\" --target=aarch64-linux-android26 $RES --sysroot=\"$TC\" -Qunused-arguments -isystem \"$TC/include/aarch64-linux-android\" -isystem \"$TC/include\" -L\"$TC/lib\" -Wl,-rpath,\"$TC/lib\" -fuse-ld=lld \"$@\"\n"
            + "SONORO_CC_EOF\n"
            + "chmod 755 \"" + TC_DIR + "/bin/aarch64-cc\"\n"
        );
        String dirtyPath = dirtyBinsFile().getAbsolutePath();
        String invalidateCmd = forceRebuild
            ? "rm -f \"" + BIN_DIR + "\"/.sonoro-*.ver \"" + BIN_DIR + "/.built.version\" \"" + binCache + "\"/.sonoro-*.ver \"" + binCache + "/.built.version\" 2>/dev/null || true\n"
            : "";
        String cmd = "set -e\n"
            + "mkdir -p " + EXEC_ROOT + "\n"
            + "echo $$ > " + EXEC_ROOT + "/build.pid\n"
            + "MPID=''\n"
            + "trap '[ -n \"$MPID\" ] && kill -9 \"$MPID\" 2>/dev/null || true; for b in cargo rustc clang clang-21 lld rust-lld; do for p in $(pidof \"$b\" 2>/dev/null); do kill -9 \"$p\" 2>/dev/null || true; done; done; rm -f " + EXEC_ROOT + "/build.pid' EXIT INT TERM\n"
            + fixCc
            + "mkdir -p " + SRC_STAGE + " " + BIN_DIR + " " + TARGET_DIR + " " + EXEC_ROOT + "/cargo_home \"" + binCache + "\" \"" + dirtyBinsFile().getParent() + "\"\n"
            + invalidateCmd
            + "cd " + SRC_STAGE + "\n"
            + "export PATH=\"" + tc + "/bin:$PATH\"\n"
            + "export LD_LIBRARY_PATH=\"" + tc + "/lib\"\n"
            + "export CARGO_TERM_COLOR=never\n"
            + "export CARGO_HOME=\"" + EXEC_ROOT + "/cargo_home\"\n"
            + "export CARGO_TARGET_DIR=\"" + TARGET_DIR + "\"\n"
            + "export SSL_CERT_FILE=\"" + tc + "/etc/tls/cert.pem\"\n"
            + "export CC=\"" + ccBin + "\"\n"
            + "export AR=\"" + tc + "/bin/llvm-ar\"\n"
            + "export CC_aarch64_linux_android=\"" + ccBin + "\"\n"
            + "export AR_aarch64_linux_android=\"" + tc + "/bin/llvm-ar\"\n"
            + "export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=\"" + ccBin + "\"\n"
            + "export CARGO_PROFILE_RELEASE_OPT_LEVEL=0\n"
            + "export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16\n"
            + "export CARGO_PROFILE_RELEASE_DEBUG=0\n"
            + "export CARGO_PROFILE_RELEASE_LTO=false\n"
            + "export CARGO_PROFILE_RELEASE_STRIP=symbols\n"
            + "export CARGO_INCREMENTAL=1\n"
            + "export MALLOC_ARENA_MAX=2\n"
            + "NCPU=$(nproc 2>/dev/null || echo 4)\n"
            + "RAM_KB=$(awk '/MemTotal/{print $2}' /proc/meminfo 2>/dev/null || echo 4194304)\n"
            + "if [ \"$RAM_KB\" -lt 4500000 ]; then JOBS=2\n"
            + "elif [ \"$RAM_KB\" -lt 7000000 ]; then JOBS=3\n"
            + "else JOBS=4; fi\n"
            + "[ \"$NCPU\" -lt \"$JOBS\" ] && JOBS=$NCPU\n"
            + "FORCE_REBUILD=" + (forceRebuild ? "1" : "0") + "\n"
            + "DIRTY_TRACKED=" + (dirtyTracked ? "1" : "0") + "\n"
            + "DIRTY_SPACE='" + dirtySpace + "'\n"
            + "DIRTY_FILE=\"" + dirtyPath + "\"\n"
            + ": > \"" + logOut + "\"\n"
            + "BUILD_PKGS=''\n"
            + "BUILD_ARGS=''\n"
            + "HAS_GAMESV=0\n"
            + "for pkg in sonoro-cfgsv sonoro-loginsv sonoro-aki-logic sonoro-gamesv; do\n"
            + "  IS_DIRTY=0\n"
            + "  case \" $DIRTY_SPACE \" in *\" $pkg \"*) IS_DIRTY=1 ;; esac\n"
            + "  if [ \"$FORCE_REBUILD\" = \"0\" ] && [ \"$IS_DIRTY\" = \"0\" ]; then\n"
            + "    if [ -x \"" + BIN_DIR + "/$pkg\" ] && [ -f \"" + BIN_DIR + "/.$pkg.ver\" ] && { [ \"$DIRTY_TRACKED\" = \"1\" ] || [ \"$(cat \"" + BIN_DIR + "/.$pkg.ver\" 2>/dev/null)\" = '" + cleanVer + "' ]; }; then\n"
            + "      printf '%s' '" + cleanVer + "' > \"" + BIN_DIR + "/.$pkg.ver\"\n"
            + "      printf '%s' '" + cleanVer + "' > \"" + binCache + "/.$pkg.ver\" 2>/dev/null || true\n"
            + "      echo \"Up to date: $pkg (skipped)\" >>\"" + logOut + "\"\n"
            + "      continue\n"
            + "    fi\n"
            + "    if [ -s \"" + binCache + "/$pkg\" ] && [ -f \"" + binCache + "/.$pkg.ver\" ] && { [ \"$DIRTY_TRACKED\" = \"1\" ] || [ \"$(cat \"" + binCache + "/.$pkg.ver\" 2>/dev/null)\" = '" + cleanVer + "' ]; }; then\n"
            + "      cp -f \"" + binCache + "/$pkg\" \"" + BIN_DIR + "/$pkg\" && chmod 755 \"" + BIN_DIR + "/$pkg\"\n"
            + "      printf '%s' '" + cleanVer + "' > \"" + BIN_DIR + "/.$pkg.ver\"\n"
            + "      printf '%s' '" + cleanVer + "' > \"" + binCache + "/.$pkg.ver\" 2>/dev/null || true\n"
            + "      echo \"Restored cached binary: $pkg (skipped)\" >>\"" + logOut + "\"\n"
            + "      continue\n"
            + "    fi\n"
            + "  fi\n"
            + "  BUILD_PKGS=\"$BUILD_PKGS $pkg\"\n"
            + "  BUILD_ARGS=\"$BUILD_ARGS -p $pkg\"\n"
            + "  [ \"$pkg\" = \"sonoro-gamesv\" ] && HAS_GAMESV=1\n"
            + "done\n"
            + "BUILD_PKGS=\"${BUILD_PKGS# }\"\n"
            + "if [ -n \"$BUILD_PKGS\" ]; then\n"
            + "  (\n"
            + "    cd \"" + serverDirPath() + "\"\n"
            + "    find . -type f ! -path './.git*' ! -path './assets/BinData*' ! -path './assets/assets.db' | while IFS= read -r rel; do\n"
            + "      dst=\"" + SRC_STAGE + "/${rel#./}\"\n"
            + "      if [ ! -f \"$dst\" ] || ! cmp -s \"$rel\" \"$dst\" 2>/dev/null; then\n"
            + "        mkdir -p \"${dst%/*}\"\n"
            + "        cp -f \"$rel\" \"$dst\"\n"
            + "      fi\n"
            + "    done\n"
            + "  )\n"
            + "  printf '%s' '" + cleanVer + "' > \"" + SRC_STAGE + "/.synced.ver\"\n"
            + "  if [ \"$HAS_GAMESV\" = \"1\" ] && [ \"$JOBS\" -gt 3 ]; then JOBS=3; fi\n"
            + "  export CARGO_BUILD_JOBS=\"$JOBS\"\n"
            + "  echo \"=== Building [$BUILD_PKGS] (-j $JOBS, opt=0, cgu=16, incremental, unified cache) ===\" >>\"" + logOut + "\"\n"
            + "  (\n"
            + "    sleep 15\n"
            + "    while :; do\n"
            + "      CRATE=\"\"\n"
            + "      for pf in /proc/[0-9]*/cmdline; do\n"
            + "        cl=$(tr '\\0' ' ' < \"$pf\" 2>/dev/null) || continue\n"
            + "        case \"$cl\" in\n"
            + "          *rustc*--crate-name\\ *)\n"
            + "            CRATE=${cl#*--crate-name }\n"
            + "            CRATE=${CRATE%% *}\n"
            + "            break\n"
            + "            ;;\n"
            + "        esac\n"
            + "      done\n"
            + "      if [ -n \"$CRATE\" ]; then\n"
            + "        echo \"[working] rustc compiling crate: $CRATE\" >>\"" + logOut + "\"\n"
            + "      else\n"
            + "        echo \"[working] cargo building $BUILD_PKGS...\" >>\"" + logOut + "\"\n"
            + "      fi\n"
            + "      sleep 15\n"
            + "    done\n"
            + "  ) &\n"
            + "  MPID=$!\n"
            + "  set +e\n"
            + "  cargo build --release -j \"$JOBS\" $BUILD_ARGS >>\"" + logOut + "\" 2>&1\n"
            + "  RC=$?\n"
            + "  kill -9 \"$MPID\" 2>/dev/null || true\n"
            + "  wait \"$MPID\" 2>/dev/null || true\n"
            + "  MPID=''\n"
            + "  set -e\n"
            + "  [ \"$RC\" -eq 0 ] || exit \"$RC\"\n"
            + "  for pkg in $BUILD_PKGS; do\n"
            + "    cp -f \"" + TARGET_DIR + "/release/$pkg\" \"" + BIN_DIR + "/$pkg\" && chmod 755 \"" + BIN_DIR + "/$pkg\"\n"
            + "    printf '%s' '" + cleanVer + "' > \"" + BIN_DIR + "/.$pkg.ver\"\n"
            + "    cp -f \"" + BIN_DIR + "/$pkg\" \"" + binCache + "/$pkg\" 2>/dev/null || true\n"
            + "    printf '%s' '" + cleanVer + "' > \"" + binCache + "/.$pkg.ver\" 2>/dev/null || true\n"
            + "    echo \"Finished & saved binary: $pkg\" >>\"" + logOut + "\"\n"
            + "  done\n"
            + "fi\n"
            + ": > \"$DIRTY_FILE\" 2>/dev/null || true\n"
            + "printf '%s' '" + cleanVer + "' > " + BIN_DIR + "/.built.version\n"
            + "printf '%s' '" + cleanVer + "' > \"" + binCache + "/.built.version\" 2>/dev/null || true\n";
        String[] res;
        try {
            res = execPrivTimeout(method, cmd, 3600);
        } finally {
            tailStop.set(true);
            tailer.interrupt();
        }
        if (!"0".equals(res[0].trim())) {
            String err = res[2].trim();
            if (err.isEmpty()) err = readTail(new File(logOut), 25);
            log.onLog("Cargo build failed: " + err);
            return false;
        }
        log.onLog("Compiled all 4 server binaries");
        return true;
    }

    public static String startLocalServer(Context ctx, int method, boolean coop, String advertiseIp, LogCb log) {
        ensureDirs(ctx);
        String adv = (advertiseIp == null || advertiseIp.trim().isEmpty()) ? "127.0.0.1" : advertiseIp.trim();
        String bindHost = coop ? "0.0.0.0" : "127.0.0.1";
        String pubRoot = publicRootPath();
        String stateDir = stateDirPath();
        String logsDir = new File(publicRoot(), "logs").getAbsolutePath();
        String pkg = ctx.getPackageName();

        // Keep app and gateway awake while the game runs
        execPriv(method,
            "dumpsys deviceidle whitelist +" + pkg + " 2>/dev/null || true\n"
                + "cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true\n"
                + "cmd appops set " + pkg + " RUN_IN_BACKGROUND allow 2>/dev/null || true");

        // Start native gateway (:5500 login/cfg, :8080 MITM proxy)
        String gwRes = RustCore.startLocalGateway(adv, 5500, 8080, 13100, stateDir, proxyDirPath());
        if (!gwRes.startsWith("OK:")) {
            return gwRes;
        }
        log.onLog(gwRes.substring(3));

        // Launch the four backend binaries in detached sessions
        String spawn = "set -e\n"
            + KILL_SERVERS_SH + "\n"
            + "sleep 0.2\n"
            + "cd \"" + pubRoot + "\"\n"
            + "mkdir -p \"" + stateDir + "\" \"" + logsDir + "\" \"" + EXEC_ROOT + "\"\n"
            + "rm -f \"" + AKI_SOCK + "\"\n"
            + "export GAMESV_STATE_DIR=\"" + stateDir + "\"\n"
            + "export GAMESV_DB_PATH=\"" + stateDir + "/gamesv.db\"\n"
            + "export AKI_IPC_SOCKET_PATH=\"" + AKI_SOCK + "\"\n"
            + "export AKI_IPC_TCP_PORT=19999\n"
            + "launch_bg() { if command -v setsid >/dev/null 2>&1; then setsid \"$@\" </dev/null & else nohup \"$@\" </dev/null & fi }\n"
            + "launch_bg \"" + BIN_DIR + "/sonoro-aki-logic\" >\"" + logsDir + "/aki-logic.log\" 2>&1\n"
            + "for i in 1 2 3 4 5 6 7 8 9 10; do [ -S \"" + AKI_SOCK + "\" ] && break; sleep 0.2; done\n"
            + "launch_bg \"" + BIN_DIR + "/sonoro-cfgsv\" -s \"" + stateDir + "\" -l \"" + bindHost + ":15500\" >\"" + logsDir + "/cfgsv.log\" 2>&1\n"
            + "launch_bg \"" + BIN_DIR + "/sonoro-loginsv\" -s \"" + stateDir + "\" -l \"" + bindHost + ":10001\" -g \"" + adv + ":13100\" >\"" + logsDir + "/loginsv.log\" 2>&1\n"
            + "launch_bg \"" + BIN_DIR + "/sonoro-gamesv\" -s \"" + stateDir + "\" -b \"" + bindHost + ":13100\" -g \"127.0.0.1:13101\" >\"" + logsDir + "/gamesv.log\" 2>&1\n"
            + "for p in sonoro-aki-logic sonoro-cfgsv sonoro-loginsv sonoro-gamesv; do "
            + "for pid in $(pidof \"$p\" 2>/dev/null); do echo -500 > /proc/$pid/oom_score_adj 2>/dev/null || true; done; done\n";
        String[] res = execPriv(method, spawn);
        if (!"0".equals(res[0].trim())) {
            RustCore.stopLocalGateway();
            return "ERR:" + (res[2].trim().isEmpty() ? "Spawn failed" : res[2].trim());
        }

        // Wait up to 15s for gamesv GM HTTP health
        for (int i = 0; i < 30; i++) {
            String[] h = PrivilegedHelper.httpGet("http://127.0.0.1:13101/health");
            if ("OK".equals(h[0])) {
                log.onLog("Local server healthy (:13100 KCP / :5500 HTTP / :8080 Proxy)");
                startRuntimeLogTailer(log);
                return "OK:" + adv;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stopLocalServer(method);
        return "ERR:Local gamesv timed out (check /0/sonoro/logs/gamesv.log)";
    }

    public static void stopLocalServer(int method) {
        stopRuntimeLogTailer();
        RustCore.stopLocalGateway();
        execPrivTimeout(method, KILL_SERVERS_SH, 10);
    }

    public static String checkRemoteServerAlive(String host) {
        String h = host.trim();
        String tcp8080 = RustCore.checkTcp(h, 8080);
        if (!"OK".equals(tcp8080)) return tcp8080;
        String tcp5500 = RustCore.checkTcp(h, 5500);
        if (!"OK".equals(tcp5500)) return "OK:Proxy port 8080 open";
        String[] health = PrivilegedHelper.httpGet("http://" + h + ":5500/health");
        if (!"OK".equals(health[0])) return "ERR:Health failed (" + health[1] + ")";
        String[] status = PrivilegedHelper.httpGet("http://" + h + ":5500/aki-logic/status");
        if ("OK".equals(status[0])) {
            try {
                JSONObject j = new JSONObject(status[1]);
                if (!j.optBoolean("online", true)) return "ERR:Remote server offline";
            } catch (Throwable ignored) {
            }
        }
        return "OK:Server online (" + h + ")";
    }

    public static String[] detectBestCoopIp() {
        String pub = detectPublicIp();
        if (pub != null && !pub.isEmpty()) {
            return new String[]{pub, "Public IP"};
        }
        String ts = detectTailscaleIp();
        if (ts != null && !ts.isEmpty()) {
            return new String[]{ts, "Tailscale IP"};
        }
        return new String[]{detectLanIp(), "LAN IP"};
    }

    public static String detectPublicIp() {
        String[] endpoints = {
            "https://api.ipify.org",
            "https://ipv4.icanhazip.com",
            "https://checkip.amazonaws.com"
        };
        for (String url : endpoints) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(4500);
                conn.setRequestProperty("User-Agent", "SonoroConnect/1.0");
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line = br.readLine();
                        if (line != null) {
                            String ip = line.trim();
                            if (isValidIpv4(ip)) return ip;
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return "";
    }

    private static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return false;
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    public static String detectTailscaleIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        byte[] b = addr.getAddress();
                        int a0 = b[0] & 0xFF, a1 = b[1] & 0xFF;
                        if (a0 == 100 && a1 >= 64 && a1 <= 127) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String[] probeServerVersions(int method) {
        String ghSrv = "";
        String ghAssets = "";
        JSONObject manifest = fetchManifest();
        if (manifest != null) {
            ghAssets = manifest.optString("version", "");
            JSONObject srv = manifest.optJSONObject("server");
            if (srv != null) ghSrv = srv.optString("version", "");
        }
        File syncedVerFile = new File(serverDirPath(), ".server.version");
        String syncedVer = syncedVerFile.exists() ? readFileText(syncedVerFile).trim() : "";
        String[] res = execPriv(method,
            "for b in sonoro-aki-logic sonoro-cfgsv sonoro-loginsv sonoro-gamesv; do "
                + "[ -x " + BIN_DIR + "/$b ] || exit 1; done; "
                + "cat " + BIN_DIR + "/.built.version 2>/dev/null || echo OK");
        String builtVer = "0".equals(res[0].trim()) ? res[1].trim() : "";
        return new String[]{ghSrv, ghAssets, syncedVer, builtVer};
    }

    public static String detectLanIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }

    public static String backupDatabase() {
        File db = new File(gamesvDbPath());
        if (!db.exists()) return "ERR:No gamesv.db found";
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dst = new File(backupsDirPath(), "gamesv_" + ts + ".db");
        return RustCore.playerDbCopy(db.getAbsolutePath(), dst.getAbsolutePath());
    }

    public static List<File> listBackups() {
        List<File> out = new ArrayList<>();
        File dir = new File(backupsDirPath());
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".db")) out.add(f);
            }
        }
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private interface DownloadProgress {
        void onProgress(long done, long total);
    }

    private static boolean downloadFile(String urlStr, File dest, String expectedSha, DownloadProgress prog) {
        return downloadFileWithResume(urlStr, dest, expectedSha, false, prog);
    }

    private static boolean downloadFileWithResume(String urlStr, File dest, String expectedSha, boolean allowResume, DownloadProgress prog) {
        lastDownloadErr = "";
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection conn = null;
            try {
                if (cancelled) return false;
                if (attempt > 0) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                }
                long existingLen = (allowResume && tmp.exists()) ? tmp.length() : 0L;
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                activeDownloadConn = conn;
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(45_000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "curl/8.10.1 Sonoro/1.0");
                if (existingLen > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existingLen + "-");
                }
                int code = conn.getResponseCode();
                boolean resumed = (existingLen > 0 && code == 206);
                if (code == 416 && expectedSha != null && !expectedSha.isEmpty()) {
                    // Partial temp already reached EOF; check hash below
                    code = 200;
                    resumed = true;
                } else if (code < 200 || code >= 300) {
                    lastDownloadErr = "HTTP " + code;
                    continue;
                }
                long contentLen = conn.getContentLengthLong();
                long total = resumed && contentLen > 0 ? (existingLen + contentLen) : contentLen;
                long done = resumed ? existingLen : 0L;
                if (!(existingLen > 0 && conn.getResponseCode() == 416)) {
                    try (InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tmp, resumed)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while (!cancelled && (n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                            done += n;
                            if (prog != null) prog.onProgress(done, total);
                        }
                    }
                }
                if (cancelled) {
                    if (!allowResume) tmp.delete();
                    return false;
                }
                if (expectedSha != null && !expectedSha.isEmpty()) {
                    String actual = stripOk(RustCore.sha256File(tmp.getAbsolutePath()));
                    if (!actual.equalsIgnoreCase(expectedSha)) {
                        tmp.delete();
                        lastDownloadErr = "SHA256 mismatch";
                        continue;
                    }
                }
                if (dest.exists()) dest.delete();
                if (tmp.renameTo(dest)) {
                    lastDownloadErr = "";
                    return true;
                }
                lastDownloadErr = "rename failed";
            } catch (Throwable t) {
                if (!allowResume) tmp.delete();
                lastDownloadErr = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            } finally {
                if (activeDownloadConn == conn) activeDownloadConn = null;
                if (conn != null) conn.disconnect();
            }
        }
        return false;
    }

    private static String[] execPriv(int method, String script) {
        return execPrivTimeout(method, script, 30);
    }

    private static String[] execPrivTimeout(int method, String script, long timeoutSec) {
        return method == 0
            ? PrivilegedHelper.execRootTimeout(script, timeoutSec)
            : PrivilegedHelper.execShizukuTimeout(script, timeoutSec);
    }

    private static void saveAssetsMeta(File verFile, String ver, String sha, long size) {
        try {
            JSONObject o = new JSONObject();
            o.put("version", ver);
            o.put("sha256", sha);
            o.put("size", size);
            writeFileText(verFile, o.toString());
        } catch (Throwable ignored) {
        }
    }

    private static String stripOk(String s) {
        return s != null && s.startsWith("OK:") ? s.substring(3).trim() : "";
    }

    private static void copyFile(File src, File dst) {
        if (src == null || !src.exists()) return;
        File p = dst.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (InputStream is = new java.io.FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        } catch (Throwable ignored) {
        }
    }

    private static String readFileText(File f) {
        try (InputStream is = new java.io.FileInputStream(f);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readTail(File f, int maxLines) {
        if (!f.exists()) return "";
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) lines.add(ln);
        } catch (Throwable ignored) {
        }
        int start = Math.max(0, lines.size() - maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        return sb.toString().trim();
    }

    private static void writeFileText(File f, String content) {
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static String measureRustCacheSize(Context ctx, int method) {
        long kbTotal = 0;
        String[] res = execPrivTimeout(method,
            "du -sk " + TC_DIR + " " + TARGET_DIR + " " + SRC_STAGE + " " + EXEC_ROOT + "/cargo_home " + BIN_DIR + " 2>/dev/null || true", 15);
        if (res[1] != null) {
            for (String ln : res[1].split("\n")) {
                String[] p = ln.trim().split("\\s+");
                if (p.length >= 1) {
                    try {
                        kbTotal += Long.parseLong(p[0]);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        long appBytes = dirBytes(new File(ctx.getCacheDir(), "tc_stage"))
            + dirBytes(new File(ctx.getCacheDir(), "tc_debs"))
            + dirBytes(new File(publicRoot(), "toolchain"));
        long totalBytes = kbTotal * 1024L + appBytes;
        if (totalBytes <= 0) return "0 MB";
        if (totalBytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.2f GB", totalBytes / (1024.0 * 1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0));
    }

    public static String cleanRustAndBuildCache(Context ctx, int method, boolean includeAssetsAndServer, LogCb log) {
        stopLocalServer(method);
        deleteRec(new File(ctx.getCacheDir(), "tc_stage"));
        deleteRec(new File(ctx.getCacheDir(), "tc_debs"));
        deleteRec(new File(publicRoot(), "toolchain"));
        if (includeAssetsAndServer) {
            deleteRec(new File(publicRoot(), "server"));
            deleteRec(new File(publicRoot(), "logs"));
            deleteRec(new File(binCacheDirPath()));
            File db = new File(assetsDbPath());
            if (db.exists()) db.delete();
            File ver = new File(new File(publicRoot(), "assets"), "assets.version.json");
            if (ver.exists()) ver.delete();
        }
        String privCmd = includeAssetsAndServer
            ? "rm -rf " + EXEC_ROOT
            : "rm -rf " + TC_DIR + " " + TARGET_DIR + " " + SRC_STAGE + " " + EXEC_ROOT + "/cargo_home " + BIN_DIR;
        if (log != null) {
            log.onLog("$ " + privCmd + " /0/sonoro/toolchain" + (includeAssetsAndServer ? " /0/sonoro/server /0/sonoro/assets/assets.db" : ""));
        }
        String[] res = execPrivTimeout(method, privCmd, 60);
        if (log != null) {
            log.onLog(includeAssetsAndServer
                ? "Done: freed Rust, build cache, server source & assets.db (gamesv.db kept)"
                : "Done: freed Rust toolchain, target, cargo_home & .deb cache");
        }
        return "0".equals(res[0].trim()) ? "OK" : ("WARN:" + res[2].trim());
    }

    private static long dirBytes(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long sum = 0;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) sum += dirBytes(c);
        }
        return sum;
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRec(c);
            }
        }
        f.delete();
    }
}
