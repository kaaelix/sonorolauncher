package id.local.wuwaconnect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;

/**
 * Checks GitHub for SonoroServer & Sonoro Launcher updates in the background
 * and posts notifications when the user is outside the app.
 */
public class SonoroUpdateJobService extends JobService {
    private static final int JOB_ID = 4101;
    private static final String CHANNEL_ID = "sonoro_updates";
    private static final String KEY_NOTIFIED_SRV = "notified_srv_ver";
    private static final String KEY_NOTIFIED_APP = "notified_app_code";

    public static void schedule(Context ctx) {
        if (ctx == null) return;
        try {
            JobScheduler js = ctx.getSystemService(JobScheduler.class);
            if (js == null) return;
            JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, SonoroUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .build();
            js.schedule(info);
        } catch (Throwable ignored) {
        }
    }

    public static void checkAndNotify(Context ctx, boolean onlyWhenOutsideApp) {
        if (ctx == null) return;
        if (onlyWhenOutsideApp && MainActivity.isAppForeground()) return;

        SharedPreferences prefs = ctx.getSharedPreferences("wuwaconnect_prefs", MODE_PRIVATE);
        int method = prefs.getInt("method", 1);

        // Check remote server version
        String[] vers = LocalServerManager.probeServerVersions(method);
        String ghSrvVer = vers[0];
        String localVer = !vers[3].isEmpty() ? vers[3] : vers[2];
        if (!ghSrvVer.isEmpty() && !localVer.isEmpty() && !ghSrvVer.equals(localVer)) {
            String prevNotified = prefs.getString(KEY_NOTIFIED_SRV, "");
            if (!ghSrvVer.equals(prevNotified) && (!onlyWhenOutsideApp || !MainActivity.isAppForeground())) {
                prefs.edit().putString(KEY_NOTIFIED_SRV, ghSrvVer).apply();
                postUpdateNotif(ctx, 103,
                    "SonoroServer Update v" + ghSrvVer,
                    "New server build available on GitHub (installed " + localVer + ").");
            }
        }

        // Check launcher release metadata
        JSONObject appJson = LocalServerManager.checkAppUpdate();
        if (appJson != null) {
            long remoteCode = appJson.optLong("versionCode", 0);
            String remoteName = appJson.optString("versionName", "").trim();
            long curCode = 1;
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                curCode = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            } catch (Throwable ignored) {
            }
            if (remoteCode > curCode) {
                long prevCode = prefs.getLong(KEY_NOTIFIED_APP, 0);
                if (remoteCode > prevCode && (!onlyWhenOutsideApp || !MainActivity.isAppForeground())) {
                    prefs.edit().putLong(KEY_NOTIFIED_APP, remoteCode).apply();
                    postUpdateNotif(ctx, 104,
                        "Sonoro Update v" + remoteName,
                        "Tap to open About and view the new launcher update.");
                }
            }
        }
    }

    private static void postUpdateNotif(Context ctx, int id, String title, String text) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Sonoro Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                nm.createNotificationChannel(ch);
            }
            int piFlags = Build.VERSION.SDK_INT >= 23
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent openPi = PendingIntent.getActivity(
                ctx, id,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                piFlags
            );
            Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
            nb.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(openPi);
            nm.notify(id, nb.build());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                checkAndNotify(getApplicationContext(), true);
            } finally {
                jobFinished(params, false);
            }
        }, "sonoro-upd-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
