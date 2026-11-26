package id.local.wuwaconnect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Keeps downloads and Rust compilation alive with a persistent foreground notification
 * until finished, canceled, or the user closes the app task.
 */
public class SonoroBuildService extends Service {
    public static final String ACTION_START = "id.local.wuwaconnect.BUILD_START";
    public static final String ACTION_UPDATE = "id.local.wuwaconnect.BUILD_UPDATE";
    public static final String ACTION_STOP = "id.local.wuwaconnect.BUILD_STOP";
    public static final String ACTION_USER_CANCEL = "id.local.wuwaconnect.BUILD_CANCEL";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";

    private static final int NOTIF_ID = 102;
    private static final String CHANNEL_ID = "sonoro_build";

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private String lastTitle = "Sonoro";
    private String lastText = "Preparing...";

    public static void start(Context ctx, String title, String text) {
        if (ctx == null) return;
        Intent i = new Intent(ctx, SonoroBuildService.class)
            .setAction(ACTION_START)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void update(Context ctx, String title, String text) {
        if (ctx == null) return;
        Intent i = new Intent(ctx, SonoroBuildService.class)
            .setAction(ACTION_UPDATE)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text);
        try {
            ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context ctx) {
        if (ctx == null) return;
        try {
            ctx.stopService(new Intent(ctx, SonoroBuildService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(act)) {
            releaseLocks();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_USER_CANCEL.equals(act)) {
            SharedPreferences p = getSharedPreferences("sonoro_prefs", MODE_PRIVATE);
            final int method = p.getInt("priv_method", 1);
            new Thread(() -> {
                LocalServerManager.requestCancel(method);
                PrivilegedHelper.stopCore(this, method == 0);
                LocalServerManager.stopLocalServer(method);
                if (SonoroVpnService.isVpnActive()) {
                    try {
                        startService(new Intent(this, SonoroVpnService.class).setAction(SonoroVpnService.ACTION_STOP));
                    } catch (Throwable ignored) {
                    }
                }
                releaseLocks();
                stopForeground(true);
                stopSelf();
            }, "sonoro-notif-cancel").start();
            return START_NOT_STICKY;
        }
        String t = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        String d = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        if (t != null && !t.isEmpty()) lastTitle = t;
        if (d != null && !d.isEmpty()) lastText = d;

        acquireLocks();
        promote(lastTitle, lastText);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        SharedPreferences p = getSharedPreferences("sonoro_prefs", MODE_PRIVATE);
        final int method = p.getInt("priv_method", 1);
        Thread killer = new Thread(() -> {
            LocalServerManager.requestCancel(method);
            PrivilegedHelper.stopCore(this, method == 0);
            LocalServerManager.stopLocalServer(method);
        }, "sonoro-task-removed");
        killer.start();
        if (SonoroVpnService.isVpnActive()) {
            try {
                startService(new Intent(this, SonoroVpnService.class).setAction(SonoroVpnService.ACTION_STOP));
            } catch (Throwable ignored) {
            }
        }
        try {
            killer.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        releaseLocks();
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    private void promote(String title, String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Server Setup & Compiler",
                    NotificationManager.IMPORTANCE_LOW
                );
                nm.createNotificationChannel(ch);
            }
            int piFlags = Build.VERSION.SDK_INT >= 23
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent openPi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                piFlags
            );
            PendingIntent cancelPi = PendingIntent.getService(
                this, 1,
                new Intent(this, SonoroBuildService.class).setAction(ACTION_USER_CANCEL),
                piFlags
            );

            Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
            nb.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", cancelPi);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, nb.build());
            }
        } catch (Throwable ignored) {
        }
    }

    private void acquireLocks() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sonoro:build_lock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= 29
                        ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                    wifiLock = wm.createWifiLock(mode, "sonoro:build_wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
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
}
