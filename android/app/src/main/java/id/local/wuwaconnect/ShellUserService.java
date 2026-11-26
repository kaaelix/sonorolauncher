package id.local.wuwaconnect;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ShellUserService extends IShellService.Stub {
    private static volatile Process activeProc;

    public ShellUserService() {
    }

    @Override
    public Bundle exec(String script) {
        Bundle b = new Bundle();
        try {
            long timeoutSec = 30;
            String body = script;
            if (script != null && script.startsWith("#TIMEOUT=")) {
                int nl = script.indexOf('\n');
                if (nl > 9) {
                    try {
                        timeoutSec = Math.max(10L, Long.parseLong(script.substring(9, nl).trim()));
                    } catch (Throwable ignored) {
                    }
                    body = script.substring(nl + 1);
                }
            }
            Process proc = new ProcessBuilder("sh").start();
            activeProc = proc;
            try (OutputStream os = proc.getOutputStream()) {
                os.write((body + "\nexit $?\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
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
                killSonoroTree();
                tOut.join(1000);
                tErr.join(1000);
                b.putInt("code", -1);
                b.putString("out", outBuf.toString(StandardCharsets.UTF_8));
                b.putString("err", "Shizuku command timed out");
                return b;
            }

            tOut.join(2000);
            tErr.join(2000);
            b.putInt("code", proc.exitValue());
            b.putString("out", outBuf.toString(StandardCharsets.UTF_8));
            b.putString("err", errBuf.toString(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            b.putInt("code", -1);
            b.putString("out", "");
            b.putString("err", t.toString());
        } finally {
            activeProc = null;
        }
        return b;
    }

    private static void killSonoroTree() {
        try {
            new ProcessBuilder("sh", "-c",
                "for pf in /data/local/tmp/sonoro/build.pid /data/local/tmp/sonoro/singbox.pid; do "
                + "[ -f \"$pf\" ] && kill -9 -$(cat \"$pf\" 2>/dev/null) $(cat \"$pf\" 2>/dev/null) 2>/dev/null || true; rm -f \"$pf\"; done; "
                + "pkill -9 -f '/[d]ata/local/tmp/sonoro/' 2>/dev/null || true; "
                + "for p in cargo rustc clang clang-21 lld rust-lld aarch64-cc sonoro-deb sing-box sonoro-cfgsv sonoro-loginsv sonoro-aki-logic sonoro-aki-logi sonoro-gamesv build_script_build; do "
                + "for id in $(pidof \"$p\" 2>/dev/null); do kill -9 \"$id\" 2>/dev/null || true; done; done"
            ).start().waitFor(4, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean writeFile(String destPath, ParcelFileDescriptor pfd) {
        if (pfd == null) return false;
        try (ParcelFileDescriptor autoClose = pfd;
             FileInputStream fis = new FileInputStream(autoClose.getFileDescriptor())) {
            File target = new File(destPath);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(target)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void destroy() {
        Process p = activeProc;
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
        killSonoroTree();
        System.exit(0);
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
}
