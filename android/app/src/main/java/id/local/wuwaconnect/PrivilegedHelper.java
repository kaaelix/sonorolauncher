package id.local.wuwaconnect;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.MediaStore;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import rikka.shizuku.Shizuku;

public final class PrivilegedHelper {
    private static final int REQ_CODE = 1001;
    private static final String TMP_DIR = "/data/local/tmp/sonoro";
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object SERVICE_LOCK = new Object();
    private static final Object CORE_LOCK = new Object();
    private static final Object HTTP_LOCK = new Object();

    private static Context appContext;
    private static volatile IShellService shellService;
    private static volatile String servedProfileJson = "";
    private static volatile int profileServerPort = 0;

    private PrivilegedHelper() {}

    public static void init(Context ctx) {
        if (ctx != null) {
            appContext = ctx.getApplicationContext();
        }
        if (INITIALIZED.compareAndSet(false, true)) {
            Shizuku.addBinderDeadListener(() -> {
                synchronized (SERVICE_LOCK) {
                    shellService = null;
                }
            });
        }
    }

    public static int getAppUid(Context ctx, String pkg) {
        try {
            ApplicationInfo info = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return info.uid;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static final String[] SU_PATHS = {
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/su/bin/su"
    };

    private static volatile String cachedSuBin = "su";

    private static String findSuBinary() {
        for (String p : SU_PATHS) {
            if ("su".equals(p)) continue;
            if (new File(p).exists()) {
                return p;
            }
        }
        return "su";
    }

    public static String checkRoot() {
        String su = findSuBinary();
        cachedSuBin = su;
        String[] res = runLocalProcess(new String[]{su, "-c", "id -u"}, null, 30);
        int code = parseCode(res[0]);
        if (code == 0 && "0".equals(res[1].trim())) {
            return "OK:Root ready (uid 0)";
        }
        // Fallback interactive su in case su -c syntax differs
        String[] res2 = runLocalProcess(new String[]{su}, "id -u\nexit $?\n", 30);
        if (parseCode(res2[0]) == 0 && "0".equals(res2[1].trim())) {
            return "OK:Root ready (uid 0)";
        }
        String err = res[2].trim().isEmpty() ? res2[2].trim() : res[2].trim();
        return err.isEmpty() ? "ERR:Root unavailable" : "ERR:Root unavailable: " + err;
    }

    public static String openRootManager(Context ctx) {
        if (ctx == null) return null;
        PackageManager pm = ctx.getPackageManager();
        String[][] known = {
            {"com.rifsxd.ksunext", "KernelSU Next"},
            {"me.weishu.kernelsu", "KernelSU"},
            {"org.kernelsu.manager", "KernelSU"},
            {"com.sukisu.ultra", "SukiSU"},
            {"me.bmax.apatch", "APatch"},
            {"io.github.vvb2060.magisk", "Magisk"},
            {"io.github.huskydg.magisk", "Magisk"},
            {"com.topjohnwu.magisk", "Magisk"}
        };
        for (String[] entry : known) {
            Intent launch = pm.getLaunchIntentForPackage(entry[0]);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ctx.startActivity(launch);
                    return entry[1];
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                String p = ai.packageName != null ? ai.packageName.toLowerCase() : "";
                if (p.contains("kernelsu") || p.contains("ksunext") || p.contains("sukisu")
                    || p.contains("apatch") || p.contains("magisk")) {
                    Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(launch);
                        if (p.contains("kernel") || p.contains("ksu")) return "KernelSU";
                        if (p.contains("apatch")) return "APatch";
                        return "Magisk";
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String checkShizuku(boolean requestPermission) {
        if (!Shizuku.pingBinder()) {
            for (int i = 0; i < 8 && !Shizuku.pingBinder(); i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!Shizuku.pingBinder()) {
            return "ERR:Shizuku not running";
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return "OK:Shizuku ready (uid " + Shizuku.getUid() + ")";
        }
        if (!requestPermission) {
            return "ERR:Shizuku detected (permission needed)";
        }
        if (requestPermissionSync(25_000)) {
            return "OK:Shizuku ready (uid " + Shizuku.getUid() + ")";
        }
        return "ERR:Shizuku permission denied";
    }

    public static String[] execRoot(String script) {
        return execRootTimeout(script, 30);
    }

    public static String[] execRootTimeout(String script, long timeoutSec) {
        String su = cachedSuBin != null ? cachedSuBin : findSuBinary();
        return runLocalProcess(new String[]{su}, script + "\nexit $?\n", timeoutSec);
    }

    public static String[] execShizuku(String script) {
        return execShizukuTimeout(script, 30);
    }

    public static String[] execShizukuTimeout(String script, long timeoutSec) {
        if (timeoutSec > 30 && script != null && !script.startsWith("#TIMEOUT=")) {
            script = "#TIMEOUT=" + timeoutSec + "\n" + script;
        }
        if (!Shizuku.pingBinder()) {
            return new String[]{"-1", "", "Shizuku service is not running"};
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (!requestPermissionSync(25_000)) {
                return new String[]{"-1", "", "Shizuku permission denied"};
            }
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            IShellService svc;
            try {
                svc = getOrBindShellService();
            } catch (Throwable t) {
                return new String[]{"-1", "", t.getMessage() != null ? t.getMessage() : t.toString()};
            }
            try {
                Bundle b = svc.exec(script);
                int code = b.getInt("code", -1);
                String out = b.getString("out", "");
                String err = b.getString("err", "");
                return new String[]{String.valueOf(code), out, err};
            } catch (RemoteException e) {
                synchronized (SERVICE_LOCK) {
                    shellService = null;
                }
                if (attempt == 1) {
                    return new String[]{"-1", "", "Shizuku remote error: " + e.getMessage()};
                }
            }
        }
        return new String[]{"-1", "", "Shizuku execution failed"};
    }

    public static String[] httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "sing-box/1.14.4");
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (is != null) {
                drain(is, baos);
            }
            String body = baos.toString(StandardCharsets.UTF_8);
            if (code >= 200 && code < 300) {
                return new String[]{"OK", body};
            }
            return new String[]{"ERR", "HTTP " + code + (body.isEmpty() ? "" : ": " + trimText(body, 180))};
        } catch (Throwable t) {
            return new String[]{"ERR", t.getMessage() != null ? t.getMessage() : t.toString()};
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String stageExecAsset(Context ctx, String assetName, boolean useRoot) {
        try {
            Context c = ctx != null ? ctx.getApplicationContext() : appContext;
            if (c == null) return "ERR:Missing Android context";
            File staged = new File(c.getFilesDir(), assetName);
            File tmp = new File(c.getFilesDir(), assetName + ".tmp");
            long copiedLen = 0;
            try (InputStream is = c.getAssets().open(assetName);
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    copiedLen += n;
                }
            }
            if (!staged.exists() || staged.length() != copiedLen) {
                if (staged.exists()) staged.delete();
                if (!tmp.renameTo(staged)) return "ERR:Failed staging " + assetName;
            } else {
                tmp.delete();
            }
            String dst = TMP_DIR + "/" + assetName;
            if (!useRoot) {
                if (!Shizuku.pingBinder()) return "ERR:Shizuku not running";
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED && !requestPermissionSync(25_000)) {
                    return "ERR:Shizuku permission denied";
                }
                IShellService svc = getOrBindShellService();
                try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)) {
                    if (!svc.writeFile(dst, pfd)) return "ERR:Failed staging " + assetName + " via Shizuku";
                }
                String[] r = execShizuku("mkdir -p '" + TMP_DIR + "' && chmod 755 '" + dst + "'");
                if (parseCode(r[0]) != 0) return "ERR:chmod failed on " + dst;
            } else {
                String[] r = execRoot("mkdir -p '" + TMP_DIR + "' && cp -f '" + staged.getAbsolutePath() + "' '" + dst + "' && chmod 755 '" + dst + "'");
                if (parseCode(r[0]) != 0) return "ERR:" + (r[2].trim().isEmpty() ? "Stage copy failed" : r[2].trim());
            }
            return "OK:" + dst;
        } catch (Throwable t) {
            return "ERR:" + (t.getMessage() != null ? t.getMessage() : t.toString());
        }
    }

    private static File ensureSingboxExtracted(Context c) throws Exception {
        File out = new File(c.getFilesDir(), "sing-box");
        try (InputStream is = c.getAssets().open("sing-box")) {
            long assetLen = is.available();
            if (out.exists() && assetLen > 0 && out.length() == assetLen) {
                return out;
            }
            File tmp = new File(c.getFilesDir(), "sing-box.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(out)) {
                throw new IllegalStateException("Failed to stage sing-box binary");
            }
        }
        return out;
    }

    public static String startCore(Context ctx, String configJson, boolean useRoot) {
        synchronized (CORE_LOCK) {
            try {
                Context c = ctx != null ? ctx.getApplicationContext() : appContext;
                if (c == null) {
                    return "ERR:Missing Android context";
                }
                File stagedBin = ensureSingboxExtracted(c);
                File stagedCfg = new File(c.getFilesDir(), "singbox.json");
                try (FileOutputStream fos = new FileOutputStream(stagedCfg)) {
                    fos.write(configJson.getBytes(StandardCharsets.UTF_8));
                }

                String tmpBin = TMP_DIR + "/sing-box";
                String tmpCfg = TMP_DIR + "/config.json";
                String tmpLog = TMP_DIR + "/singbox.log";
                String tmpPid = TMP_DIR + "/singbox.pid";

                if (!useRoot) {
                    if (!Shizuku.pingBinder()) {
                        return "ERR:Shizuku not running";
                    }
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED && !requestPermissionSync(25_000)) {
                        return "ERR:Shizuku permission denied";
                    }
                    IShellService svc = getOrBindShellService();
                    try (ParcelFileDescriptor pfdBin = ParcelFileDescriptor.open(stagedBin, ParcelFileDescriptor.MODE_READ_ONLY);
                         ParcelFileDescriptor pfdCfg = ParcelFileDescriptor.open(stagedCfg, ParcelFileDescriptor.MODE_READ_ONLY)) {
                        if (!svc.writeFile(tmpBin, pfdBin)) {
                            return "ERR:Failed installing sing-box via Shizuku";
                        }
                        if (!svc.writeFile(tmpCfg, pfdCfg)) {
                            return "ERR:Failed writing config.json via Shizuku";
                        }
                    }
                }

                String installPart = useRoot
                    ? "mkdir -p '" + TMP_DIR + "' && cp '" + stagedBin.getAbsolutePath() + "' '" + tmpBin + "' && cp '" + stagedCfg.getAbsolutePath() + "' '" + tmpCfg + "' && "
                    : "mkdir -p '" + TMP_DIR + "' && ";

                String launchScript =
                    installPart
                    + "chmod 755 '" + tmpBin + "' && "
                    + "if ! '" + tmpBin + "' check -c '" + tmpCfg + "' 2>'" + tmpLog + "'; then cat '" + tmpLog + "'; exit 1; fi\n"
                    + "if [ -f '" + tmpPid + "' ]; then kill $(cat '" + tmpPid + "') 2>/dev/null || true; fi\n"
                    + "for p in $(pidof sing-box 2>/dev/null); do kill \"$p\" 2>/dev/null || true; done\n"
                    + "if command -v setsid >/dev/null 2>&1; then\n"
                    + "  setsid '" + tmpBin + "' run -c '" + tmpCfg + "' -D '" + TMP_DIR + "' > '" + tmpLog + "' 2>&1 </dev/null &\n"
                    + "else\n"
                    + "  nohup '" + tmpBin + "' run -c '" + tmpCfg + "' -D '" + TMP_DIR + "' > '" + tmpLog + "' 2>&1 </dev/null &\n"
                    + "fi\n"
                    + "PID=$!\n"
                    + "echo \"$PID\" > '" + tmpPid + "'\n"
                    + "echo -500 > /proc/$PID/oom_score_adj 2>/dev/null || true\n"
                    + "sleep 0.7\n"
                    + "if kill -0 \"$PID\" 2>/dev/null; then echo \"PID=$PID\"; tail -n 20 '" + tmpLog + "' 2>/dev/null || true; else cat '" + tmpLog + "' 2>/dev/null; exit 1; fi\n";

                String[] res = useRoot ? execRoot(launchScript) : execShizuku(launchScript);
                if (parseCode(res[0]) == 0 && res[1].contains("PID=")) {
                    return "OK:sing-box running\n" + res[1].trim();
                }
                String err = res[2].trim().isEmpty() ? res[1].trim() : res[2].trim();
                return "ERR:sing-box failed: " + trimText(err, 280);
            } catch (Throwable t) {
                return "ERR:" + (t.getMessage() != null ? t.getMessage() : t.toString());
            }
        }
    }

    public static String stopCore(Context ctx, boolean useRoot) {
        try {
            RustCore.stopHttpProxyRelay();
        } catch (Throwable ignored) {
        }
        synchronized (CORE_LOCK) {
            String tmpBin = TMP_DIR + "/sing-box";
            String tmpPid = TMP_DIR + "/singbox.pid";
            String script =
                "if [ -f '" + tmpPid + "' ]; then kill $(cat '" + tmpPid + "') 2>/dev/null || true; rm -f '" + tmpPid + "'; fi\n"
                + "for p in $(pidof sing-box 2>/dev/null); do kill \"$p\" 2>/dev/null || true; done\n";
            if (useRoot) {
                execRoot(script);
            } else if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                execShizuku(script);
            }
            return "OK:sing-box stopped";
        }
    }

    public static void copyText(Context ctx, String text) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Sonoro", text));
            }
        });
    }

    public static String pasteText(Context ctx) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return "";
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] out = new String[]{""};
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
                    if (cs != null) out[0] = cs.toString();
                }
            } catch (Throwable ignored) {
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return out[0];
    }

    public static String loadData(Context ctx, String name) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return "";
        try {
            File f = new File(c.getFilesDir(), name);
            if (!f.exists()) return "";
            try (FileInputStream fis = new FileInputStream(f)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                drain(fis, baos);
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return "";
        }
    }

    public static void saveData(Context ctx, String name, String content) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return;
        try {
            File f = new File(c.getFilesDir(), name);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    public static String saveJsonToDownloads(Context ctx, String filename, String json) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return "ERR:Missing context";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = c.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os != null) {
                            os.write(json.getBytes(StandardCharsets.UTF_8));
                            return "OK:Saved to Download/" + filename;
                        }
                    }
                }
            }
            File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return "OK:Saved to " + out.getAbsolutePath();
        } catch (Throwable t) {
            return "ERR:Save failed: " + (t.getMessage() != null ? t.getMessage() : t.toString());
        }
    }

    public static String importExternal(Context ctx, String clientId, String configJson, String rawUri) {
        Context c = ctx != null ? ctx.getApplicationContext() : appContext;
        if (c == null) return "ERR:Missing context";
        try {
            if ("copy".equals(clientId)) {
                copyText(c, configJson);
                return "OK:Config JSON copied to clipboard";
            }
            if ("save".equals(clientId)) {
                return saveJsonToDownloads(c, "wuwa-singbox.json", configJson);
            }

            String localUrl = ensureProfileServer(configJson);
            String encodedUrl = URLEncoder.encode(localUrl, "UTF-8");

            String pkg = null;
            String deepLink = null;
            if ("sfa".equals(clientId)) {
                pkg = "io.nekohasekai.sfa";
                deepLink = "sing-box://import-remote-profile?url=" + encodedUrl + "#WuWa%20Connect";
            } else if ("exclave".equals(clientId)) {
                pkg = isInstalled(c, "com.github.dyhkwong.sfa") ? "com.github.dyhkwong.sfa" : "dy.nekohasekai.sfa";
                deepLink = "sing-box://import-remote-profile?url=" + encodedUrl + "#WuWa%20Connect";
            } else if ("hiddify".equals(clientId)) {
                pkg = "app.hiddify.com";
                deepLink = "hiddify://import/" + localUrl + "#WuWa%20Connect";
            } else if ("karing".equals(clientId)) {
                pkg = "com.nebula.karing";
                deepLink = "karing://install-config?url=" + encodedUrl + "&name=WuWa%20Connect";
            } else if ("nekobox".equals(clientId)) {
                pkg = "moe.nb4a";
                String clip = (rawUri != null && !rawUri.trim().isEmpty()) ? rawUri.trim() : configJson;
                copyText(c, clip);
                if (rawUri != null && rawUri.trim().startsWith("sn://")) {
                    deepLink = rawUri.trim();
                }
            }

            if (!"nekobox".equals(clientId)) {
                copyText(c, configJson);
            }

            if (deepLink != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg != null && isInstalled(c, pkg)) {
                    intent.setPackage(pkg);
                }
                try {
                    c.startActivity(intent);
                    return "OK:Sent profile to " + clientId.toUpperCase() + " (" + localUrl + ")";
                } catch (Throwable ignored) {
                }
            }

            if (pkg != null && isInstalled(c, pkg)) {
                Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(launch);
                    return "OK:Opened " + clientId + " (profile copied to clipboard)";
                }
            }

            return "ERR:" + clientId + " is not installed (JSON copied to clipboard)";
        } catch (Throwable t) {
            return "ERR:" + (t.getMessage() != null ? t.getMessage() : t.toString());
        }
    }

    private static boolean isInstalled(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String ensureProfileServer(String json) throws Exception {
        synchronized (HTTP_LOCK) {
            servedProfileJson = json;
            if (profileServerPort != 0) {
                return "http://127.0.0.1:" + profileServerPort + "/profile.json";
            }
            ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            profileServerPort = server.getLocalPort();
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket sock = server.accept()) {
                        sock.setSoTimeout(2000);
                        BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = br.readLine()) != null && !line.isEmpty()) {
                        }
                        byte[] payload = servedProfileJson.getBytes(StandardCharsets.UTF_8);
                        String hdr = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json; charset=utf-8\r\n"
                            + "Content-Disposition: attachment; filename=\"wuwa-singbox.json\"\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                        OutputStream os = sock.getOutputStream();
                        os.write(hdr.getBytes(StandardCharsets.US_ASCII));
                        os.write(payload);
                        os.flush();
                    } catch (Throwable ignored) {
                    }
                }
            }, "sb-profile-httpd");
            t.setDaemon(true);
            t.start();
            return "http://127.0.0.1:" + profileServerPort + "/profile.json";
        }
    }

    private static boolean requestPermissionSync(long timeoutMs) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (Shizuku.isPreV11()) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(PackageManager.PERMISSION_DENIED);
        Shizuku.OnRequestPermissionResultListener listener = (requestCode, grantResult) -> {
            if (requestCode == REQ_CODE) {
                result.set(grantResult);
                latch.countDown();
            }
        };
        Shizuku.addRequestPermissionResultListener(listener);
        try {
            new Handler(Looper.getMainLooper()).post(() -> Shizuku.requestPermission(REQ_CODE));
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener);
        }
        return result.get() == PackageManager.PERMISSION_GRANTED
            || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private static IShellService getOrBindShellService() throws Exception {
        synchronized (SERVICE_LOCK) {
            if (shellService != null && shellService.asBinder().pingBinder()) {
                return shellService;
            }
            if (appContext == null) {
                throw new IllegalStateException("PrivilegedHelper not initialized");
            }
            if (Shizuku.getVersion() < 10) {
                throw new IllegalStateException("Shizuku v10+ required");
            }

            CountDownLatch latch = new CountDownLatch(1);
            final IShellService[] bound = new IShellService[1];

            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    bound[0] = IShellService.Stub.asInterface(binder);
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized (SERVICE_LOCK) {
                        shellService = null;
                    }
                }
            };

            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(appContext.getPackageName(), ShellUserService.class.getName())
            )
                .daemon(false)
                .processNameSuffix("shizuku_shell")
                .debuggable(false)
                .version(1);

            new Handler(Looper.getMainLooper()).post(() -> Shizuku.bindUserService(args, conn));
            if (!latch.await(10, TimeUnit.SECONDS) || bound[0] == null) {
                throw new IllegalStateException("Timed out binding Shizuku UserService");
            }
            shellService = bound[0];
            return shellService;
        }
    }

    private static String[] runLocalProcess(String[] cmd, String stdin, long timeoutSec) {
        try {
            Process proc = new ProcessBuilder(cmd).start();
            if (stdin != null) {
                try (OutputStream os = proc.getOutputStream()) {
                    os.write(stdin.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            Thread tOut = new Thread(() -> drain(proc.getInputStream(), outBuf));
            Thread tErr = new Thread(() -> drain(proc.getErrorStream(), errBuf));
            tOut.start();
            tErr.start();

            boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                tOut.join(1000);
                tErr.join(1000);
                return new String[]{"-1", outBuf.toString(StandardCharsets.UTF_8), "Command timed out"};
            }
            tOut.join(2000);
            tErr.join(2000);
            return new String[]{
                String.valueOf(proc.exitValue()),
                outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8)
            };
        } catch (Throwable t) {
            return new String[]{"-1", "", t.getMessage() != null ? t.getMessage() : t.toString()};
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String trimText(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(s.length() - maxChars);
    }

    private static int parseCode(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }
}
