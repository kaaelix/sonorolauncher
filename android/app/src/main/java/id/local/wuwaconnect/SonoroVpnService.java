package id.local.wuwaconnect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class SonoroVpnService extends VpnService {
    public static final String ACTION_START = "id.local.wuwaconnect.VPN_START";
    public static final String ACTION_STOP = "id.local.wuwaconnect.VPN_STOP";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PKG = "pkg";

    private static volatile boolean libLoaded = false;
    private static volatile SonoroVpnService instance;
    private static volatile boolean intentionalStop = false;

    private ParcelFileDescriptor tunPfd;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
            libLoaded = true;
        } catch (Throwable t) {
            libLoaded = false;
        }
    }

    private static native boolean TProxyStartService(String configPath, int fd);
    private static native boolean TProxyStopService();
    private static native boolean TProxyIsRunning();
    private static native long[] TProxyGetStats();

    public static boolean isVpnActive() {
        if (!libLoaded) return false;
        try {
            return instance != null && TProxyIsRunning();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            intentionalStop = true;
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        intentionalStop = false;
        promoteForeground();
        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 10808) : 10808;
        String pkg = intent != null ? intent.getStringExtra(EXTRA_PKG) : "";
        startVpn(port, pkg);
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        stopVpn();
        cleanupOrphanedBackends();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        boolean revoked = !intentionalStop && (instance != null || tunPfd != null);
        stopVpn();
        if (revoked) {
            cleanupOrphanedBackends();
        }
        super.onDestroy();
    }

    private void cleanupOrphanedBackends() {
        final android.content.Context ctx = getApplicationContext();
        new Thread(() -> {
            try {
                int m = ctx.getSharedPreferences("sonoro_prefs", MODE_PRIVATE).getInt("priv_method", 1);
                PrivilegedHelper.stopCore(ctx, m == 0);
                LocalServerManager.stopLocalServer(m);
            } catch (Throwable ignored) {
            }
        }, "sonoro-vpn-revoke-cleanup").start();
    }

    private void acquireLocks() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sonoro:vpn_gw");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= 29
                        ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                    wifiLock = wm.createWifiLock(mode, "sonoro:wifi_gw");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) {
        }
    }

    private synchronized void startVpn(int socksPort, String targetPkg) {
        stopVpn();
        if (!libLoaded) return;

        try {
            acquireLocks();
            VpnService.Builder b = new VpnService.Builder();
            b.setSession("Sonoro");
            b.setMtu(8500);
            b.addAddress("198.18.0.1", 30);
            b.addRoute("0.0.0.0", 0);
            b.addAddress("fc00::1", 126);
            b.addRoute("::", 0);
            b.addDnsServer("8.8.8.8");
            b.addDnsServer("1.1.1.1");

            for (String skipPkg : new String[]{
                getPackageName(),
                "com.android.shell",
                "moe.shizuku.privileged.api"
            }) {
                if (skipPkg == null || skipPkg.isEmpty()) continue;
                try {
                    b.addDisallowedApplication(skipPkg);
                } catch (Throwable ignored) {
                }
            }

            tunPfd = b.establish();
            if (tunPfd == null) {
                releaseLocks();
                return;
            }

            File cfgFile = new File(getFilesDir(), "tproxy.yml");
            String yml = "tunnel:\n"
                + "  name: tun0\n"
                + "  mtu: 8500\n"
                + "  ipv4: 198.18.0.1\n"
                + "  ipv6: 'fc00::1'\n"
                + "socks5:\n"
                + "  port: " + socksPort + "\n"
                + "  address: 127.0.0.1\n"
                + "  udp: 'udp'\n"
                + "misc:\n"
                + "  task-stack-size: 81920\n";
            try (FileOutputStream fos = new FileOutputStream(cfgFile)) {
                fos.write(yml.getBytes(StandardCharsets.UTF_8));
            }

            promoteForeground();
            if (TProxyStartService(cfgFile.getAbsolutePath(), tunPfd.getFd())) {
                instance = this;
            } else {
                closeTunFd();
                releaseLocks();
            }
        } catch (Throwable t) {
            closeTunFd();
            releaseLocks();
        }
    }

    private void promoteForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    "sonoro_vpn",
                    "Sonoro Tunnel",
                    NotificationManager.IMPORTANCE_LOW
                );
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(ch);
            }
            Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "sonoro_vpn")
                : new Notification.Builder(this);
            nb.setContentTitle("Sonoro")
                .setContentText("All apps VPN tunnel active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true);
            startForeground(101, nb.build());
        } catch (Throwable ignored) {
        }
    }

    private synchronized void stopVpn() {
        instance = null;
        if (libLoaded) {
            try {
                if (TProxyIsRunning()) {
                    TProxyStopService();
                }
            } catch (Throwable ignored) {
            }
        }
        closeTunFd();
        releaseLocks();
    }

    private void closeTunFd() {
        if (tunPfd != null) {
            try {
                tunPfd.close();
            } catch (Throwable ignored) {
            }
            tunPfd = null;
        }
    }
}
