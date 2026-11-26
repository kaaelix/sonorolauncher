package id.local.wuwaconnect;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.icehome.up.AppEntrance;
import com.icehome.up.GlassBar;
import com.icehome.up.Icons;
import com.icehome.up.LiveBackground;
import com.icehome.up.Motion;
import com.icehome.up.SonoroLogoView;
import com.icehome.up.SplitTitle;
import com.icehome.up.TabReveal;
import com.icehome.up.TopBlur;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String TAG = "SonoroConnect";
    private static final String PKG = "com.kurogame.wutheringwaves.global";
    private static final String INI_REL = "files/UE4Game/Client/Client/Saved/Config/Android/Engine.ini";
    private static final String CONNS_FILE = "connections.json";
    private static final String PREFS_NAME = "sonoro_prefs";
    private static final String KEY_ONBOARDED = "onboarded";
    private static final String KEY_METHOD = "priv_method";
    private static final String KEY_SERVER_MODE = "server_mode";
    private static final String KEY_LOCAL_COOP = "local_coop";
    private static final String KEY_LOCAL_ADV_IP = "local_adv_ip";
    private static final String KEY_SAVED_CONNS = "saved_conns_json";
    private static final String KEY_ACTIVE_TARGET = "active_target";
    private static final int REQ_VPN = 4102;
    private static final int LOCAL_MIXED_PORT = 10808;

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xCC0B1019;
    private static final int CARD_RIM_TOP = 0x2EFFFFFF;
    private static final int CARD_RIM_BOT = 0x14FFFFFF;
    private static final int OUTLINE = 0xFF2B313C;
    private static final int DIVIDER = 0x26FFFFFF;
    private static final int PRESSED = 0x26FFFFFF;
    private static final int TEXT = 0xFFECEFF4;
    private static final int TEXT2 = 0xFF9AA2B0;
    private static final int TEXT3 = 0xFF636B78;

    private static final int HERO_ACTIVE = 0xCC263142;
    private static final int HERO_IDLE = 0xC4161C26;
    private static final int HERO_ERR = 0xCC3D1F24;
    private static final int HERO_FG = 0xFFE2ECFF;
    private static final int HERO_SUB = 0xFFB0BED4;

    private static final int PLATE = 0xCC33435C;
    private static final int PLATE_FG = 0xFFD6E3FF;
    private static final int BLUE_BTN = 0xDE2962FF;
    private static final int BLUE_SOFT = 0xFF9AB8F5;
    private static final int SWITCH_ON = 0xFFADC6FF;

    private static final int STOP_BG = 0xE6F2B8B5;
    private static final int STOP_FG = 0xFF410E0B;
    private static final int ERR_FG = 0xFFFFB4AB;
    private static final int OK_GREEN = 0xFF81C995;

    private static final class PlayerRow {
        long playerId;
        String name;
        long level;
        long exp;
        long coin;
        long rareCoin;
        long cashCoin;
        long sex;
        long worldLevel;
    }

    private static final class SavedConn {
        String name;
        String kind;
        String target;
        SavedConn(String name, String kind, String target) {
            this.name = name;
            this.kind = kind;
            this.target = target;
        }
    }

    private float dp;
    private int insetTop, insetBottom, barSpace;
    private FrameLayout root, stack;
    private TopBlur topVeil;
    private GlassBar bar;
    private final List<ScrollView> pages = new ArrayList<>();
    private int currentTab = 0;

    private final ExecutorService bgExec = Executors.newSingleThreadExecutor();

    // App state
    private SharedPreferences prefs;
    private boolean onboarded = false;
    private int wizardSlide = 0;
    private boolean wizardLogoPlayed = false;
    private ValueAnimator wizardSwapAnim;
    private boolean playersLoadedOnce = false;
    private boolean playersLoading = false;
    private String chkAbiText = "";
    private boolean chkAbiOk = true;
    private String chkSpaceText = "";
    private double chkFreeGb = -1;
    private boolean chkSpaceOk = true;
    private int chkGameUid = 0;
    private boolean chkGameOk = false;
    private boolean chkShReady = false;
    private boolean chkShDet = false;
    private boolean chkSuBin = false;
    private String chkMgrFound = "";
    private boolean chkPrivOk = false;
    private boolean pendingStart = false;
    private int pendingScrollY = 0;

    private int serverMode = 0; // 0 = Connect Server, 1 = Local Server
    private boolean localCoop = false;
    private String localAdvIp = "";
    private String localIpKind = "Detecting...";
    private String localStatusLine = "Not checked";
    private String githubServerVer = "Checking...";
    private String githubAssetsVer = "";
    private String localBuiltVer = "";
    private String rustCacheSizeText = "—";
    private boolean compilerReady = false;
    private TextView liveHomeLogTv;
    private ScrollView liveHomeLogScroll;
    private TextView liveSysLogTv;
    private ScrollView liveSysLogScroll;

    private LinearLayout homeCol, homeHeroCard, homeModeCard, homeDynSlot, homeStepsSlot, homeLogSlot, homeSrvUpdSlot;
    private FrameLayout homeHeroCircle;
    private ImageView homeHeroIc;
    private TextView homeHeroTitle, homeHeroSub, homeFactModeVal, homeFactStateVal;
    private TextView homeStartPill, homeStopPill, homeCheckPill;
    private LinearLayout sysCol, sysModeCard, sysSrvCard;
    private TextView sysPrivHeader, sysSrvHeader;
    private View sysPrivStorageDiv, sysPrivStorageRow;
    private static volatile boolean appForeground = false;

    public static boolean isAppForeground() {
        return appForeground;
    }

    private final List<PlayerRow> playerList = new ArrayList<>();
    private final List<File> dbBackups = new ArrayList<>();
    private String playerDbMsg = "";
    private long playerDbBytes = 0;
    private boolean editingOpen = false;
    private boolean editingIsNew = false;
    private String editUid = "";
    private String editName = "";
    private String editLevel = "1";
    private String editExp = "0";
    private String editCoin = "0";
    private String editRareCoin = "0";
    private String editCashCoin = "0";
    private long editSex = 1;
    private String editWorldLevel = "1";

    private String activeTarget = "";
    private String draftInput = "";
    private String draftBadge = "";
    private boolean draftOk = true;
    private String connMsg = "";
    private final List<SavedConn> savedConns = new ArrayList<>();

    private int method = 1; // 0 = Root, 1 = Shizuku (default Shizuku)
    private int methodState = 0; // 0 idle, 1 checking, 2 ready, 3 failed
    private String methodStatus = "Not checked";

    private String parseBadge = "--";
    private String parseSummary = "No server";
    private boolean parseOk = true;

    private String appUidText = "--";
    private String ownerStatus = "Not checked";
    private int statusState = 0; // 0 ready, 1 working, 2 active, 3 failed
    private String statusText = "Ready";
    private boolean busy = false;
    private volatile boolean flowCancelRequested = false;
    private volatile Thread activeFlowThread = null;
    private ValueAnimator modeSwapAnim;
    private ValueAnimator updatePulseAnim;
    private ValueAnimator heroPulseAnim;
    private final int[] stepStates = {0, 0, 0, 0};
    private final int[] prevStepStates = {0, 0, 0, 0};
    private final StringBuilder logBuf = new StringBuilder();

    private String appVerName = "0.1.1";
    private long appVerCode = 2;
    private boolean updateAvailable = false;
    private boolean updateChecking = false;
    private String remoteVerName = "";
    private long remoteVerCode = 0;
    private String remoteChangelog = "";
    private String remoteUpdateUrl = "https://github.com/kaaelix/sonorolauncher";

    private FrameLayout.LayoutParams barLp;
    private final String[] TAB_CONNECT_TITLES = {"Home", "Connection", "System", "About"};
    private static final int[] TAB_CONNECT_ICONS = {Icons.HOME, Icons.GRID, Icons.SLIDERS, Icons.INFO};
    private final String[] TAB_LOCAL_TITLES = {"Home", "Database", "System", "About"};
    private static final int[] TAB_LOCAL_ICONS = {Icons.HOME, Icons.DATABASE, Icons.SLIDERS, Icons.INFO};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            writeCrashLog("Uncaught " + t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });

        try {
            PrivilegedHelper.init(this);
        } catch (Throwable t) {
            writeCrashLog("init", t);
        }

        dp = getResources().getDisplayMetrics().density;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi.versionName != null && !pi.versionName.isEmpty()) appVerName = pi.versionName;
            appVerCode = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Throwable ignored) {
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false);
        method = prefs.getInt(KEY_METHOD, 1);
        serverMode = prefs.getInt(KEY_SERVER_MODE, 0);
        localCoop = prefs.getBoolean(KEY_LOCAL_COOP, false);
        localAdvIp = LocalServerManager.detectLanIp();
        localIpKind = tr("detecting_pub_ip");
        methodStatus = tr("not_checked");
        ownerStatus = tr("not_checked");
        localStatusLine = tr("not_checked");
        statusText = tr("ready");

        loadSavedConns(false);
        detectActiveTarget();

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        stack = new FrameLayout(this);
        root.addView(stack, new FrameLayout.LayoutParams(-1, -1));

        topVeil = new TopBlur(this, BG);
        topVeil.setSource(stack);
        final FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(-1, px(40), Gravity.TOP);
        root.addView(topVeil, vlp);

        barLp = new FrameLayout.LayoutParams(-1, px(66), Gravity.BOTTOM);
        barLp.leftMargin = px(10);
        barLp.rightMargin = px(10);
        installBar(0);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int top = in.getSystemWindowInsetTop();
                int bottom = in.getSystemWindowInsetBottom();
                insetBottom = bottom;
                barLp.bottomMargin = bottom + px(2);
                bar.setLayoutParams(barLp);
                vlp.height = top + px(44);
                topVeil.setLayoutParams(vlp);
                if (top != insetTop || barSpace == 0) {
                    insetTop = top;
                    barSpace = bottom + px(2) + bar.heightFor(66);
                    for (ScrollView p : pages) pad(p);
                }
                return in;
            }
        });

        setContentView(root);
        if (!onboarded) {
            bar.setVisibility(View.GONE);
            wizardSlide = 0;
            wizardLogoPlayed = false;
            runPhoneCheckAsync();
            showWizard(0, true);
        } else {
            bar.setVisibility(View.VISIBLE);
            showTab(0, false);
            bar.select(0, false);
            if (b == null && !pages.isEmpty()) {
                AppEntrance.play(root, pages.get(0), bar, dp);
            }
        }

        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 201);
        }
        SonoroUpdateJobService.schedule(this);
        probeStartupState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appForeground = true;
        reconcileVpnStateOnResume();
    }

    private void reconcileVpnStateOnResume() {
        if (busy) return;
        if (statusState != 0 && statusState != 3 && !SonoroVpnService.isVpnActive()) {
            statusState = 0;
            statusText = tr("ready");
            for (int i = 0; i < 4; i++) stepStates[i] = 0;
            final int useMethod = method;
            new Thread(() -> {
                SonoroBuildService.stop(MainActivity.this);
                PrivilegedHelper.stopCore(MainActivity.this, useMethod == 0);
                LocalServerManager.stopLocalServer(useMethod);
                execPriv(useMethod, "settings put global http_proxy ':0'");
            }, "sonoro-vpn-reconcile").start();
            updateHomeStatusUi(true);
        }
    }

    @Override
    protected void onPause() {
        appForeground = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        appForeground = false;
        new Thread(() -> SonoroUpdateJobService.checkAndNotify(getApplicationContext(), true), "sonoro-bg-chk").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            boolean resume = pendingStart;
            pendingStart = false;
            if (resultCode == RESULT_OK && resume) {
                startFlow();
            } else if (resume) {
                failStep(2, "VPN permission denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalServerManager.stopRuntimeLogTailer();
        bgExec.shutdownNow();
    }

    private void probeStartupState() {
        bgExec.execute(() -> {
            try {
                Thread.sleep(1150);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            final int uid = PrivilegedHelper.getAppUid(this, PKG);
            final boolean vpnOn = SonoroVpnService.isVpnActive();
            final String tcReady = LocalServerManager.checkToolchainReady(method);
            if (LocalServerManager.hasStorageAccess()) {
                LocalServerManager.ensureDirs(this);
            }
            final boolean connsRestored = syncSavedConnsExternalOnBg();
            final String[] bestIp = LocalServerManager.detectBestCoopIp();
            final String[] vers = LocalServerManager.probeServerVersions(method);
            final String rustSz = LocalServerManager.measureRustCacheSize(this, method);
            final JSONObject appUpd = LocalServerManager.checkAppUpdate();
            if (method == 1) {
                String sh = PrivilegedHelper.checkShizuku(false);
                boolean shOk = sh.startsWith("OK:");
                if (!vpnOn && shOk) {
                    PrivilegedHelper.stopCore(this, false);
                    LocalServerManager.stopLocalServer(1);
                }
                final boolean activeOnOpen = vpnOn;
                final String stText = stripPrefix(sh);
                runOnUiThread(() -> {
                    if (uid > 0) appUidText = String.valueOf(uid);
                    rustCacheSizeText = rustSz;
                    localAdvIp = bestIp[0];
                    localIpKind = bestIp[1];
                    if (prefs != null) prefs.edit().putString(KEY_LOCAL_ADV_IP, localAdvIp).apply();
                    githubServerVer = vers[0].isEmpty() ? (vers[2].isEmpty() ? "Offline" : vers[2]) : vers[0];
                    githubAssetsVer = vers[1];
                    localBuiltVer = vers[3];
                    applyAppUpdateJson(appUpd);
                    compilerReady = tcReady != null;
                    refreshLocalSummaryText();
                    if (method == 1) {
                        methodState = shOk ? 2 : 3;
                        methodStatus = stText;
                    }
                    if (activeOnOpen && statusState == 0) {
                        statusState = 2;
                        statusText = tr("active");
                    } else if (!activeOnOpen && statusState == 2 && !busy) {
                        statusState = 0;
                        statusText = tr("ready");
                        for (int i = 0; i < 4; i++) stepStates[i] = 0;
                    }
                    if (!onboarded || connsRestored || currentTab == 0 || currentTab == 1 || currentTab == 2 || currentTab == 3) refreshCurrentTab();
                });
            } else {
                if (!vpnOn) {
                    PrivilegedHelper.stopCore(this, true);
                    LocalServerManager.stopLocalServer(0);
                }
                runOnUiThread(() -> {
                    if (uid > 0) appUidText = String.valueOf(uid);
                    rustCacheSizeText = rustSz;
                    localAdvIp = bestIp[0];
                    localIpKind = bestIp[1];
                    if (prefs != null) prefs.edit().putString(KEY_LOCAL_ADV_IP, localAdvIp).apply();
                    githubServerVer = vers[0].isEmpty() ? (vers[2].isEmpty() ? "Offline" : vers[2]) : vers[0];
                    githubAssetsVer = vers[1];
                    localBuiltVer = vers[3];
                    applyAppUpdateJson(appUpd);
                    compilerReady = tcReady != null;
                    refreshLocalSummaryText();
                    if (vpnOn && statusState == 0) {
                        statusState = 2;
                        statusText = tr("active");
                    } else if (!vpnOn && statusState == 2 && !busy) {
                        statusState = 0;
                        statusText = tr("ready");
                        for (int i = 0; i < 4; i++) stepStates[i] = 0;
                    }
                    if (!onboarded || connsRestored || (currentTab == 0 && (uid > 0 || vpnOn || serverMode == 1)) || currentTab == 1 || currentTab == 3) refreshCurrentTab();
                });
            }
        });
    }

    private static String stripPrefix(String s) {
        if (s == null) return "";
        if (s.startsWith("OK:")) return s.substring(3);
        if (s.startsWith("ERR:")) return s.substring(4);
        return s;
    }

    private void loadSavedConns(boolean writeBack) {
        savedConns.clear();
        String savedTarget = prefs != null ? prefs.getString(KEY_ACTIVE_TARGET, "") : "";
        String[] sources = new String[]{
            PrivilegedHelper.loadData(this, CONNS_FILE),
            prefs != null ? prefs.getString(KEY_SAVED_CONNS, "") : "",
            LocalServerManager.readExternalText(LocalServerManager.connectionsStateFile()),
            LocalServerManager.readExternalText(LocalServerManager.connectionsRootFile())
        };
        java.util.LinkedHashSet<String> seenTargets = new java.util.LinkedHashSet<>();
        String parsedActive = "";
        for (String raw : sources) {
            if (raw == null || raw.trim().isEmpty()) continue;
            String trimmed = raw.trim();
            try {
                JSONArray arr;
                if (trimmed.startsWith("{")) {
                    JSONObject wrap = new JSONObject(trimmed);
                    if (parsedActive.isEmpty()) parsedActive = wrap.optString("activeTarget", "");
                    arr = wrap.optJSONArray("items");
                } else {
                    arr = new JSONArray(trimmed);
                }
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String tgt = o.optString("target", "").trim();
                    if (!tgt.isEmpty() && seenTargets.add(tgt)) {
                        savedConns.add(new SavedConn(
                            o.optString("name", tgt),
                            o.optString("kind", "Proxy"),
                            tgt
                        ));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String chosen = !savedTarget.trim().isEmpty() ? savedTarget.trim() : parsedActive.trim();
        if (!chosen.isEmpty()) {
            activeTarget = chosen;
        } else if (!savedConns.isEmpty()) {
            activeTarget = savedConns.get(0).target;
        } else {
            activeTarget = "";
        }
        if (writeBack && !savedConns.isEmpty()) {
            persistSavedConns(false);
        }
    }

    private boolean syncSavedConnsExternalOnBg() {
        if (!LocalServerManager.hasStorageAccess()) return false;
        String extState = LocalServerManager.readExternalText(LocalServerManager.connectionsStateFile());
        String extRoot = LocalServerManager.readExternalText(LocalServerManager.connectionsRootFile());
        final int beforeCount = savedConns.size();
        if ((!extState.trim().isEmpty() || !extRoot.trim().isEmpty())) {
            final boolean[] changed = {false};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            runOnUiThread(() -> {
                try {
                    loadSavedConns(true);
                    detectActiveTarget();
                    changed[0] = savedConns.size() != beforeCount || !savedConns.isEmpty();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(1200, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return changed[0];
        } else if (!savedConns.isEmpty()) {
            runOnUiThread(() -> persistSavedConns(false));
        }
        return false;
    }

    private void persistSavedConns() {
        persistSavedConns(true);
    }

    private void persistSavedConns(boolean userModified) {
        try {
            JSONArray arr = new JSONArray();
            for (SavedConn c : savedConns) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("kind", c.kind);
                o.put("target", c.target);
                arr.put(o);
            }
            JSONObject wrap = new JSONObject();
            wrap.put("activeTarget", activeTarget != null ? activeTarget : "");
            wrap.put("items", arr);
            String raw = wrap.toString();
            PrivilegedHelper.saveData(this, CONNS_FILE, raw);
            if (prefs != null) {
                prefs.edit()
                    .putString(KEY_SAVED_CONNS, raw)
                    .putString(KEY_ACTIVE_TARGET, activeTarget != null ? activeTarget : "")
                    .commit();
            }
            if (userModified || !savedConns.isEmpty()) {
                LocalServerManager.writeExternalText(LocalServerManager.connectionsStateFile(), raw);
                LocalServerManager.writeExternalText(LocalServerManager.connectionsRootFile(), raw);
            }
        } catch (Throwable ignored) {
        }
    }

    private JSONObject parseViaRust(String raw) {
        try {
            return new JSONObject(RustCore.parseProfile(raw != null ? raw : ""));
        } catch (Throwable t) {
            JSONObject fallback = new JSONObject();
            try {
                fallback.put("ok", false);
                fallback.put("badge", "Error");
                fallback.put("summary", t.getMessage() != null ? t.getMessage() : "Parse error");
            } catch (Throwable ignored) {
            }
            return fallback;
        }
    }

    private void detectActiveTarget() {
        if (activeTarget == null || activeTarget.trim().isEmpty()) {
            parseOk = true;
            parseBadge = "--";
            parseSummary = tr("no_server");
            return;
        }
        JSONObject res = parseViaRust(activeTarget);
        parseOk = res.optBoolean("ok", false);
        parseBadge = res.optString("badge", "--");
        parseSummary = res.optString("summary", "");
    }

    private void detectDraft() {
        if (draftInput.trim().isEmpty()) {
            draftBadge = "";
            draftOk = true;
            return;
        }
        JSONObject res = parseViaRust(draftInput);
        draftOk = res.optBoolean("ok", false);
        draftBadge = res.optString("badge", "Invalid");
    }

    private void refreshCurrentTab() {
        pendingScrollY = !pages.isEmpty() ? pages.get(0).getScrollY() : 0;
        if (!onboarded) {
            showWizard(wizardSlide, false);
            return;
        }
        showTab(currentTab, false);
    }

    private void showWizard(int slide, boolean animate) {
        final int prevSlide = wizardSlide;
        wizardSlide = slide;
        TabReveal.finish();
        if (wizardSwapAnim != null) {
            wizardSwapAnim.cancel();
            wizardSwapAnim = null;
        }
        boolean playIntro = (slide == 0 && !wizardLogoPlayed);
        final List<ScrollView> old = new ArrayList<>(pages);
        pages.clear();
        final ScrollView p = wizardTab();
        pages.add(p);
        stack.addView(p);

        int w = stack.getWidth() > 0 ? stack.getWidth() : getResources().getDisplayMetrics().widthPixels;
        if (animate && !old.isEmpty() && !playIntro && ValueAnimator.areAnimatorsEnabled()) {
            final boolean forward = slide >= prevSlide;
            final float inFrom = forward ? w : -w * 0.36f;
            final float outTo = forward ? -w * 0.36f : w;
            if (forward) {
                p.bringToFront();
            } else {
                for (ScrollView o : old) o.bringToFront();
            }
            p.setTranslationX(inFrom);
            p.setAlpha(forward ? 1f : 0.72f);

            final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(440);
            va.setInterpolator(Motion.EXPO_OUT);
            va.addUpdateListener(a -> {
                float f = (Float) a.getAnimatedValue();
                float blurX = (float) Math.sin(Math.PI * f) * 18f * dp;
                p.setTranslationX(inFrom * (1f - f));
                if (!forward) p.setAlpha(0.72f + 0.28f * f);
                Motion.motionBlur(p, blurX, 0.5f);
                for (ScrollView o : old) {
                    o.setTranslationX(outTo * f);
                    Motion.motionBlur(o, blurX * 0.7f, 0.5f);
                    if (forward) {
                        float sc = 1f - 0.05f * f;
                        o.setScaleX(sc);
                        o.setScaleY(sc);
                        o.setAlpha(1f - 0.55f * f);
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    if (wizardSwapAnim == va) wizardSwapAnim = null;
                    p.setTranslationX(0f);
                    p.setAlpha(1f);
                    Motion.motionBlur(p, 0f, 0f);
                    for (ScrollView o : old) {
                        Motion.motionBlur(o, 0f, 0f);
                        o.setTranslationX(0f);
                        o.setScaleX(1f);
                        o.setScaleY(1f);
                        o.setAlpha(1f);
                        stack.removeView(o);
                    }
                }
            });
            wizardSwapAnim = va;
            va.start();
        } else {
            for (ScrollView o : old) stack.removeView(o);
        }
    }

    private String tr(String key) {
        return I18n.t(this, key);
    }

    private void finishWizard() {
        onboarded = true;
        prefs.edit()
            .putBoolean(KEY_ONBOARDED, true)
            .putInt(KEY_METHOD, method)
            .putInt(KEY_SERVER_MODE, serverMode)
            .apply();
        if (wizardSwapAnim != null) {
            wizardSwapAnim.cancel();
            wizardSwapAnim = null;
        }
        final List<ScrollView> old = new ArrayList<>(pages);
        pages.clear();
        currentTab = 0;

        bar.setVisibility(View.VISIBLE);
        installBar(0);
        final ScrollView home = homeTab();
        pages.add(home);
        stack.addView(home);

        int w = stack.getWidth() > 0 ? stack.getWidth() : getResources().getDisplayMetrics().widthPixels;
        if (!old.isEmpty() && ValueAnimator.areAnimatorsEnabled()) {
            home.bringToFront();
            home.setTranslationX(w * 0.92f);
            home.setAlpha(0f);
            final float barDrop = px(120);
            bar.setTranslationY(barDrop);
            Motion.cascade((ViewGroup) home.getChildAt(0), 120, px(20));

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(520);
            va.setInterpolator(Motion.EXPO_OUT);
            va.addUpdateListener(a -> {
                float f = (Float) a.getAnimatedValue();
                float blurX = (float) Math.sin(Math.PI * f) * 16f * dp;
                home.setTranslationX(w * 0.92f * (1f - f));
                home.setAlpha(Math.min(1f, f * 1.4f));
                Motion.motionBlur(home, blurX, 0.5f);
                float sp = f >= 1f ? 1f : Motion.BACK_OUT.getInterpolation(Math.max(0f, Math.min(1f, (f - 0.15f) / 0.85f)));
                bar.setTranslationY(barDrop * (1f - sp));
                bar.invalidate();
                for (ScrollView o : old) {
                    o.setTranslationX(-w * 0.34f * f);
                    o.setScaleX(1f - 0.06f * f);
                    o.setScaleY(1f - 0.06f * f);
                    o.setAlpha(1f - f);
                    Motion.motionBlur(o, blurX * 0.7f, 0.5f);
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    home.setTranslationX(0f);
                    home.setAlpha(1f);
                    Motion.motionBlur(home, 0f, 0f);
                    bar.setTranslationY(0f);
                    bar.invalidate();
                    for (ScrollView o : old) {
                        Motion.motionBlur(o, 0f, 0f);
                        o.setTranslationX(0f);
                        o.setScaleX(1f);
                        o.setScaleY(1f);
                        o.setAlpha(1f);
                        stack.removeView(o);
                    }
                }
            });
            va.start();
        } else {
            for (ScrollView o : old) stack.removeView(o);
            if (!pages.isEmpty()) AppEntrance.play(root, pages.get(0), bar, dp);
        }
    }

    private void reopenWizard() {
        onboarded = false;
        wizardSlide = 0;
        wizardLogoPlayed = false;
        prefs.edit().putBoolean(KEY_ONBOARDED, false).apply();
        bar.setVisibility(View.GONE);
        runPhoneCheckAsync();
        showWizard(0, true);
    }

    private void runPhoneCheckAsync() {
        bgExec.execute(() -> {
            String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "unknown";
            boolean arm64 = "arm64-v8a".equals(abi);
            String abiOut = abi + " · Android " + Build.VERSION.RELEASE;

            long availBytes = 0L;
            try {
                StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
                availBytes = sf.getAvailableBytes();
            } catch (Throwable ignored) {
            }
            double freeGb = availBytes / (1024.0 * 1024.0 * 1024.0);
            boolean spaceOk = freeGb >= 2.0;
            String spaceOut = String.format(java.util.Locale.US, "%.1f GB free · /0/sonoro", freeGb);

            int uid = PrivilegedHelper.getAppUid(this, PKG);
            boolean gameOk = uid > 0;
            String gameOut = gameOk ? ("Installed · UID " + uid) : ("Missing: " + PKG);

            String sh = PrivilegedHelper.checkShizuku(false);
            boolean shReady = sh.startsWith("OK:");
            boolean shDet = shReady || sh.contains("detected");
            boolean suBin = false;
            for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/su/bin/su"}) {
                if (new File(p).exists()) {
                    suBin = true;
                    break;
                }
            }
            String mgrFound = detectInstalledMgrNames();
            boolean privOk = shReady || shDet || suBin || !mgrFound.isEmpty();
            String privOut = (shReady ? "Shizuku ready" : shDet ? "Shizuku detected" : "Shizuku not running")
                + " · " + (suBin ? "su present" : (!mgrFound.isEmpty() ? mgrFound : "No su"));

            final double fFreeGb = freeGb;
            final boolean fShReady = shReady;
            final boolean fShDet = shDet;
            final boolean fSuBin = suBin;
            final String fMgrFound = mgrFound;
            runOnUiThread(() -> {
                chkAbiOk = arm64;
                chkAbiText = abiOut;
                chkFreeGb = fFreeGb;
                chkSpaceOk = spaceOk;
                chkSpaceText = spaceOut;
                chkGameUid = uid;
                chkGameOk = gameOk;
                chkShReady = fShReady;
                chkShDet = fShDet;
                chkSuBin = fSuBin;
                chkMgrFound = fMgrFound;
                chkPrivOk = privOk;
                if (uid > 0) appUidText = String.valueOf(uid);
                if (!onboarded && wizardSlide == 1) refreshCurrentTab();
            });
        });
    }

    private String detectInstalledMgrNames() {
        StringBuilder sb = new StringBuilder();
        android.content.pm.PackageManager pm = getPackageManager();
        String[][] known = {
            {"com.rifsxd.ksunext", "KSU Next"},
            {"me.weishu.kernelsu", "KernelSU"},
            {"com.topjohnwu.magisk", "Magisk"},
            {"me.bmax.apatch", "APatch"},
            {"com.termux", "Termux"}
        };
        for (String[] k : known) {
            try {
                pm.getPackageInfo(k[0], 0);
                if (sb.length() > 0) sb.append(", ");
                sb.append(k[1]);
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    private LinearLayout wizardDots(int activeSlide) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, px(2), 0, px(14));
        for (int i = 0; i < 3; i++) {
            final int sIdx = i;
            boolean on = (i == activeSlide);
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(on ? BLUE_BTN : 0x38FFFFFF);
            g.setCornerRadius(px(4));
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(on ? px(20) : px(7), px(7));
            if (i < 2) lp.rightMargin = px(7);
            dot.setOnClickListener(v -> {
                if (sIdx == wizardSlide) return;
                if (sIdx == 1) runPhoneCheckAsync();
                showWizard(sIdx, true);
            });
            row.addView(dot, lp);
        }
        return row;
    }

    private ScrollView wizardTab() {
        LinearLayout dots = wizardDots(wizardSlide);

        if (wizardSlide == 0) {
            return wizardIntroPage(dots);
        }

        LinearLayout col = column();
        col.addView(dots);

        if (wizardSlide == 1) {
            LinearLayout chkCard = borderedCard();
            String abiVal = chkAbiText.isEmpty() ? tr("checking_gh").replace("GitHub", "").trim() : chkAbiText;
            chkCard.addView(listRow(tr("wiz_cpu"), chkAbiText.isEmpty() ? "..." : chkAbiText,
                chkAbiOk ? tr("wiz_arm64_req") : tr("wiz_arm64_bad"),
                chkAbiOk ? OK_GREEN : ERR_FG, null, false, true, false, null));
            boolean stGranted = LocalServerManager.hasStorageAccess();
            String spaceDisplay = chkFreeGb >= 0
                ? String.format(java.util.Locale.US, "%.1f GB · /0/sonoro", chkFreeGb)
                : (chkSpaceText.isEmpty() ? "..." : chkSpaceText);
            chkCard.addView(listRow(tr("storage_dir"), spaceDisplay,
                stGranted ? tr("wiz_all_files_ok") : tr("wiz_needed_local"),
                chkSpaceOk && stGranted ? OK_GREEN : TEXT2,
                stGranted ? "OK" : tr("allow"), !stGranted, true, false, v -> grantStorageAsync()));
            String gameVal = chkFreeGb < 0 && !chkGameOk ? "..."
                : chkGameOk ? (tr("wiz_installed") + " · UID " + chkGameUid)
                : (tr("wiz_missing_pkg") + ": " + PKG);
            chkCard.addView(listRow(tr("wiz_game_client"), gameVal,
                chkGameOk ? "Wuthering Waves 3.8" : tr("wiz_install_wuwa"),
                chkGameOk ? BLUE_SOFT : ERR_FG, null, false, true, false, null));
            String privVal = (chkFreeGb < 0) ? "..."
                : ((chkShReady ? tr("wiz_shizuku_ok") : chkShDet ? tr("wiz_shizuku_det") : tr("wiz_shizuku_off"))
                    + " · " + (chkSuBin ? tr("wiz_su_ok") : (!chkMgrFound.isEmpty() ? chkMgrFound : tr("wiz_no_su"))));
            chkCard.addView(listRow("Shizuku / Root", privVal,
                tr("wiz_priv_sub"),
                chkPrivOk ? BLUE_SOFT : TEXT2, tr("recheck"), true, true, true, v -> runPhoneCheckAsync()));
            col.addView(chkCard, cardLp(0, 12));

            col.addView(sectionLabel(tr("wiz_req_hdr")));
            LinearLayout reqCard = borderedCard();
            reqCard.addView(listRow(tr("connect_srv"), tr("wiz_req_conn"), null, TEXT2,
                null, false, true, false, null));
            reqCard.addView(listRow(tr("local_srv"), tr("wiz_req_loc"), null, TEXT2,
                null, false, true, true, null));
            col.addView(reqCard, cardLp(0, 14));

            LinearLayout pills = new LinearLayout(this);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            View bBack = actionPill(tr("back"), 2, true, v -> showWizard(0, true));
            View bNext = actionPill(tr("next"), 0, true, v -> showWizard(2, true));
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, px(46), 1f);
            p1.rightMargin = px(8);
            pills.addView(bBack, p1);
            pills.addView(bNext, new LinearLayout.LayoutParams(0, px(46), 1f));
            col.addView(pills, cardLp(0, 12));
            return page(tr("wiz_checking"), col);
        }

        col.addView(sectionLabel(tr("access")));
        LinearLayout accCard = borderedCard();
        accCard.addView(methodRow(Icons.TERMINAL, "Shizuku",
            method == 1 ? (methodState == 0 ? tr("wiz_tap_chk") : methodStatus) : tr("wiz_no_root_rec"),
            method == 1 ? (methodState == 2 ? BLUE_SOFT : (methodState == 3 ? ERR_FG : TEXT2)) : TEXT2,
            method == 1, false, v -> checkAccess(1, true)));
        accCard.addView(hDiv(16));
        accCard.addView(methodRow(Icons.SHIELD, "Root (su)",
            method == 0 ? (methodState == 0 ? tr("wiz_tap_grant") : methodStatus) : "Magisk / KernelSU",
            method == 0 ? (methodState == 2 ? BLUE_SOFT : (methodState == 3 ? ERR_FG : TEXT2)) : TEXT2,
            method == 0, true, v -> checkAccess(0, true)));
        col.addView(accCard, cardLp(0, 12));

        col.addView(sectionLabel(tr("mode")));
        LinearLayout mCard = borderedCard();
        mCard.addView(methodRow(Icons.GLOBE, tr("connect_srv"),
            tr("wiz_conn_short"),
            TEXT2,
            serverMode == 0, false, v -> {
                serverMode = 0;
                refreshCurrentTab();
            }));
        mCard.addView(hDiv(16));
        mCard.addView(methodRow(Icons.TERMINAL, tr("local_srv"),
            tr("wiz_loc_short"),
            TEXT2,
            serverMode == 1, true, v -> {
                serverMode = 1;
                refreshCurrentTab();
            }));
        col.addView(mCard, cardLp(0, 8));

        TextView note = text(tr("wiz_switch_note"), 12, TEXT2);
        note.setPadding(px(6), px(2), px(6), px(12));
        col.addView(note);

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        View bBack = actionPill(tr("back"), 2, true, v -> showWizard(1, true));
        View bGo = actionPill(tr("cont"), 0, true, v -> finishWizard());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, px(46), 1f);
        p1.rightMargin = px(8);
        pills.addView(bBack, p1);
        pills.addView(bGo, new LinearLayout.LayoutParams(0, px(46), 1f));
        col.addView(pills, cardLp(0, 12));

        return page(tr("wiz_setup"), col);
    }

    private ScrollView wizardIntroPage(LinearLayout dots) {
        LinearLayout col = column();
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        final SonoroLogoView logo = new SonoroLogoView(this);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(px(140), px(140));
        lLp.topMargin = px(14);
        lLp.bottomMargin = px(8);
        col.addView(logo, lLp);

        final SplitTitle titleView = new SplitTitle(this, "Sonoro", 36, TEXT, 700);
        titleView.setCentered(true);
        titleView.setPadding(px(4), 0, px(4), px(6));
        col.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        final TextView tagTv = text(tr("wiz_tagline"), 14f, TEXT2);
        tagTv.setGravity(Gravity.CENTER);
        tagTv.setPadding(px(8), 0, px(8), px(18));
        col.addView(tagTv, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout feat = borderedCard();
        feat.addView(listRow(tr("connect_srv"), tr("wiz_conn_sub"), null, TEXT2,
            null, false, true, false, null));
        feat.addView(listRow(tr("local_srv"), tr("wiz_loc_sub"), null, TEXT2,
            null, false, true, false, null));
        feat.addView(listRow(tr("wiz_ports_title"), tr("wiz_ports_sub"), null, TEXT2,
            null, false, true, true, null));
        col.addView(feat, cardLp(0, 16));

        col.addView(dots, new LinearLayout.LayoutParams(-1, -2));

        final View bNext = actionPill(tr("wiz_check_btn"), 0, true, v -> {
            runPhoneCheckAsync();
            showWizard(1, true);
        });
        col.addView(bNext, new LinearLayout.LayoutParams(-1, px(48)));

        ScrollView sv = LiveBackground.scrollView(this);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LiveBackground.ambient(BG, dp, true).attachTo(sv);
        sv.addView(col);
        pad(sv);

        if (!wizardLogoPlayed) {
            wizardLogoPlayed = true;
            final View[] below = {tagTv, feat, dots, bNext};
            titleView.hideForReveal();
            for (View v : below) {
                v.setAlpha(0f);
                v.setTranslationY(px(28));
            }
            logo.setAlpha(0f);
            col.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    col.getViewTreeObserver().removeOnPreDrawListener(this);
                    int vh = stack.getHeight() > 0 ? stack.getHeight() : getResources().getDisplayMetrics().heightPixels;
                    int[] stackPos = new int[2];
                    int[] logoPos = new int[2];
                    stack.getLocationInWindow(stackPos);
                    logo.getLocationInWindow(logoPos);
                    float curCenterY = logoPos[1] + logo.getHeight() * 0.5f;
                    float targetCenterY = stackPos[1] + vh * 0.44f;
                    final float startDy = Math.max(0f, targetCenterY - curCenterY);
                    logo.setTranslationY(startDy);
                    logo.setScaleX(1.30f);
                    logo.setScaleY(1.30f);
                    logo.setAlpha(1f);
                    logo.play(() -> {
                        if (startDy > 1f) {
                            final ValueAnimator whoosh = ValueAnimator.ofFloat(0f, 1f);
                            whoosh.setDuration(680);
                            whoosh.setInterpolator(Motion.EXPO_OUT);
                            whoosh.addUpdateListener(wa -> {
                                float f = (Float) wa.getAnimatedValue();
                                float s = 1.30f - 0.30f * f;
                                float stretch = 1f + 0.14f * (float) Math.sin(Math.PI * f);
                                float blurY = (float) Math.sin(Math.PI * f) * 14f * dp;
                                logo.setTranslationY(startDy * (1f - f));
                                logo.setScaleX(s);
                                logo.setScaleY(s * stretch);
                                Motion.motionBlur(logo, 0.5f, blurY);
                            });
                            whoosh.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    logo.setTranslationY(0f);
                                    logo.setScaleX(1f);
                                    logo.setScaleY(1f);
                                    Motion.motionBlur(logo, 0f, 0f);
                                }
                            });
                            whoosh.start();
                        } else {
                            logo.setScaleX(1f);
                            logo.setScaleY(1f);
                            Motion.motionBlur(logo, 0f, 0f);
                        }
                        titleView.play(70);
                        long[] delays = {140, 210, 290, 360};
                        for (int i = 0; i < below.length; i++) {
                            View v = below[i];
                            ObjectAnimator rev = ObjectAnimator.ofPropertyValuesHolder(
                                v,
                                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, px(28), 0f)
                            );
                            rev.setStartDelay(delays[i]);
                            rev.setDuration(640);
                            rev.setInterpolator(Motion.POWER4_OUT);
                            rev.start();
                        }
                    });
                    return true;
                }
            });
        }
        return sv;
    }

    private void installBar(int selectIdx) {
        if (bar != null) root.removeView(bar);
        String[] titles = {
            tr("tab_home"),
            tr(serverMode == 1 ? "tab_db" : "tab_conn"),
            tr("tab_sys"),
            tr("tab_about")
        };
        int[] icons = serverMode == 1 ? TAB_LOCAL_ICONS : TAB_CONNECT_ICONS;
        bar = new GlassBar(this, titles, icons, BG, TEXT2, BLUE_SOFT);
        barLp.height = bar.heightFor(66);
        barLp.bottomMargin = insetBottom + px(2);
        root.addView(bar, barLp);
        bar.setSource(stack);
        bar.setOnSelect(new GlassBar.OnSelect() {
            @Override
            public void onSelect(int index) {
                showTab(index, true);
            }
        });
        bar.select(selectIdx, false);
    }

    private void showTab(int index, boolean animate) {
        if (animate) pendingScrollY = 0;
        TabReveal.finish();
        currentTab = index;
        final List<ScrollView> old = new ArrayList<>(pages);
        pages.clear();

        ScrollView p;
        switch (index) {
            case 1:
                if (serverMode == 1) {
                    if (!playersLoadedOnce && !playersLoading && LocalServerManager.hasStorageAccess()) {
                        reloadPlayersAsync(false);
                    }
                    p = playersTab();
                } else {
                    p = connectionTab();
                }
                break;
            case 2:
                p = systemTab();
                break;
            case 3:
                p = aboutTab();
                break;
            default:
                p = homeTab();
                break;
        }
        pages.add(p);
        stack.addView(p);

        boolean wave = animate && !old.isEmpty() && stack.getWidth() > 0 && bar.getWidth() > 0;
        if (animate) Motion.cascade((ViewGroup) p.getChildAt(0), wave ? 90 : 0, px(22));
        if (wave) {
            float seg = bar.getWidth() / (float) TAB_CONNECT_TITLES.length;
            float cx = bar.getX() + seg * (index + 0.5f);
            float cy = bar.getY() + bar.getHeight() / 2f;
            TabReveal.play(root, stack, old, p, cx, cy, new Runnable() {
                @Override
                public void run() {
                    for (ScrollView o : old) stack.removeView(o);
                    bar.invalidate();
                }
            });
        } else {
            for (ScrollView o : old) stack.removeView(o);
        }
    }

    private ScrollView page(String title, LinearLayout content) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipToPadding(false);
        list.setClipChildren(false);
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.setFocusableInTouchMode(false);

        final LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setMinimumHeight(px(48));
        top.setPadding(px(16), 0, px(16), 0);
        top.setTag(Motion.PINNED);
        top.setTranslationZ(px(1));
        top.setOutlineProvider(null);

        GradientDrawable topLine = new GradientDrawable();
        topLine.setColor(0x17FFFFFF);
        final LayerDrawable topBg = new LayerDrawable(new Drawable[]{new ColorDrawable(BG), topLine});
        topBg.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        topBg.setLayerHeight(1, Math.max(1, px(0.8f)));
        topBg.setAlpha(0);
        top.setBackground(topBg);

        final TextView mini = text(title, 17, TEXT);
        mini.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
        mini.setPadding(px(4), 0, px(8), 0);
        mini.setAlpha(0f);
        top.addView(mini, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.leftMargin = tlp.rightMargin = -px(16);
        list.addView(top, tlp);

        View bigTitle = new SplitTitle(this, title, 36, TEXT, 700);
        bigTitle.setPadding(px(4), px(6), px(4), px(12));
        list.addView(bigTitle, new LinearLayout.LayoutParams(-1, -2));
        list.addView(content);

        ScrollView sv = LiveBackground.scrollView(this);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        if (currentTab == 0) {
            LiveBackground.ambient(BG, dp, true).attachTo(sv);
        } else {
            LiveBackground.dots(BG, dp).attachTo(sv);
        }
        sv.addView(list);

        final int targetScrollY = pendingScrollY;
        pendingScrollY = 0;
        final boolean[] restored = {targetScrollY <= 0};

        final View parallax = bigTitle;
        final float fade = px(120);
        final ScrollView pageView = sv;
        final Runnable syncHeader = new Runnable() {
            @Override
            public void run() {
                int y = pageView.getScrollY();
                float f = Math.min(1f, y / fade);
                parallax.setTranslationY(y * 0.45f);
                parallax.setAlpha(1f - f);
                parallax.setPivotX(0f);
                parallax.setScaleX(1f - 0.12f * f);
                parallax.setScaleY(1f - 0.12f * f);

                top.setTranslationY(y);
                float g = Motion.clamp01((y - fade * 0.55f) / (fade * 0.35f));
                topBg.setAlpha(Math.round(255 * g));
                mini.setAlpha(g);
                mini.setTranslationY(px(8) * (1f - g));

                Motion.scrub(pageView, list, insetTop + px(8), dp);
            }
        };
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                syncHeader.run();
                bar.onPageScroll(y, y - oy);
                bar.invalidate();
                topVeil.invalidate();
            }
        });
        list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (!restored[0] && targetScrollY > 0) {
                    restored[0] = true;
                    pageView.scrollTo(0, targetScrollY);
                }
                syncHeader.run();
            }
        });
        pad(sv);
        sv.post(() -> {
            if (targetScrollY > 0) {
                sv.scrollTo(0, targetScrollY);
                restored[0] = true;
            } else {
                sv.scrollTo(0, 0);
            }
            syncHeader.run();
            bar.invalidate();
            topVeil.invalidate();
        });
        return sv;
    }

    private void pad(ScrollView sv) {
        View list = sv.getChildAt(0);
        if (list != null) {
            int bot = onboarded ? barSpace + px(18) : insetBottom + px(24);
            list.setPadding(px(16), insetTop + px(4), px(16), bot);
        }
    }

    private TransitionSet makeModeTransition() {
        TransitionSet ts = new TransitionSet().setOrdering(TransitionSet.ORDERING_TOGETHER);
        ts.addTransition(new Fade(Fade.OUT).setDuration(150));
        ts.addTransition(new ChangeBounds().setDuration(420).setInterpolator(Motion.EXPO_OUT));
        ts.addTransition(new Fade(Fade.IN).setStartDelay(90).setDuration(300));
        return ts;
    }

    private void switchServerMode(int newMode, int returnTabIdx) {
        if (busy || serverMode == newMode) return;
        serverMode = newMode;
        prefs.edit().putInt(KEY_SERVER_MODE, newMode).apply();
        for (int i = 0; i < 4; i++) stepStates[i] = 0;
        if (newMode == 1) refreshLocalSummaryText();

        if (bar != null) {
            bar.morphTab(1, tr(newMode == 1 ? "tab_db" : "tab_conn"),
                newMode == 1 ? Icons.DATABASE : Icons.GRID);
        }

        if (returnTabIdx == 0 && homeCol != null) {
            TransitionManager.beginDelayedTransition(homeCol, makeModeTransition());
            if (homeFactModeVal != null) {
                homeFactModeVal.setText(tr(serverMode == 1 ? "mode_local" : "mode_connect"));
            }
            populateHomeModeCard(homeModeCard);
            populateHomeDynSlot(homeDynSlot, true);
            populateHomeStepsCard(homeStepsSlot);
            populateHomeSrvUpdSlot(homeSrvUpdSlot);
            if (homeLogSlot != null) {
                if (serverMode == 1) {
                    homeLogSlot.removeAllViews();
                    homeLogSlot.addView(buildLogCard(true, 160));
                    homeLogSlot.setVisibility(View.VISIBLE);
                } else {
                    homeLogSlot.setVisibility(View.GONE);
                }
            }
            return;
        }
        if (returnTabIdx == 2 && sysCol != null) {
            TransitionManager.beginDelayedTransition(sysCol, makeModeTransition());
            populateSysModeCard(sysModeCard);
            if (sysPrivHeader != null) {
                sysPrivHeader.setText((serverMode == 1 ? tr("priv_stor") : tr("priv")).toUpperCase(java.util.Locale.ROOT));
            }
            int vis = serverMode == 1 ? View.VISIBLE : View.GONE;
            if (sysPrivStorageDiv != null) sysPrivStorageDiv.setVisibility(vis);
            if (sysPrivStorageRow != null) sysPrivStorageRow.setVisibility(vis);
            if (sysSrvHeader != null) sysSrvHeader.setVisibility(vis);
            if (sysSrvCard != null) sysSrvCard.setVisibility(vis);
            return;
        }
        installBar(returnTabIdx);
        refreshCurrentTab();
    }

    // ---------------------------------------------------------------- HOME TAB
    private ScrollView homeTab() {
        LinearLayout col = column();
        homeCol = col;

        String heroTitle = statusState == 2 ? tr("connected")
            : statusState == 1 ? tr("starting")
            : statusState == 3 ? tr("failed")
            : tr("ready");
        String heroSub = statusState == 1 ? (serverMode == 1 ? tr("prep_local") : tr("start_vpn_proxy"))
            : statusState == 2 ? (serverMode == 1 ? (tr("local_active_pfx") + " · " + (localCoop ? localAdvIp : "127.0.0.1")) : (tr("vpn_active_pfx") + " · " + parseSummary))
            : statusState == 3 ? tr("open_sys_log")
            : methodStatus;

        int heroBg = statusState == 3 ? HERO_ERR : (statusState == 2 || statusState == 1 ? HERO_ACTIVE : HERO_IDLE);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(px(18), px(16), px(18), px(16));
        hero.setBackground(glassBg(heroBg, statusState == 2 ? 0x55B4C8FF : CARD_RIM_TOP, CARD_RIM_BOT, 24));
        homeHeroCard = hero;

        FrameLayout circle = new FrameLayout(this);
        int circleFill = statusState == 3 ? 0x26FFB4AB : 0x24E2ECFF;
        int circleBorder = statusState == 3 ? 0x66FFB4AB : 0x55E2ECFF;
        circle.setBackground(cardBg(circleFill, circleBorder, 27));
        homeHeroCircle = circle;
        ImageView heroIc = new ImageView(this);
        heroIc.setImageDrawable(new Icons(method == 1 && statusState != 2 ? Icons.TERMINAL : Icons.SHIELD, statusState == 3 ? ERR_FG : HERO_FG));
        homeHeroIc = heroIc;
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER);
        circle.addView(heroIc, hlp);
        hero.addView(circle, new LinearLayout.LayoutParams(px(52), px(52)));

        LinearLayout htCol = column();
        htCol.setPadding(px(16), 0, 0, 0);
        TextView hTitle = text(heroTitle, 20, statusState == 3 ? ERR_FG : HERO_FG);
        hTitle.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        homeHeroTitle = hTitle;
        TextView hSub = text(heroSub, 13, statusState == 3 ? 0xBBFFB4AB : HERO_SUB);
        homeHeroSub = hSub;
        htCol.addView(hTitle);
        htCol.addView(hSub);
        hero.addView(htCol, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(hero, cardLp(0, 12));
        syncHeroPulse();

        if (updateAvailable) {
            LinearLayout updRow = borderedCard();
            updRow.setBackground(glassBg(HERO_ACTIVE, 0x669AB8F5, 0x222D5CE0, 20));
            updRow.addView(listRow(tr("upd_avail") + " · v" + remoteVerName,
                remoteChangelog.isEmpty() ? tr("tab_about") : remoteChangelog,
                null, BLUE_SOFT, tr("view"), true, true, true, v -> {
                    bar.select(3, true);
                    showTab(3, true);
                }));
            col.addView(updRow, cardLp(0, 12));
        }

        homeSrvUpdSlot = column();
        populateHomeSrvUpdSlot(homeSrvUpdSlot);
        col.addView(homeSrvUpdSlot, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        facts.setPadding(px(6), px(14), px(6), px(14));
        facts.setBackground(glassBg(CARD, CARD_RIM_TOP, CARD_RIM_BOT, 24));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, -2, 1f);
        facts.addView(factCol(tr("access"), method == 0 ? tr("root") : "Shizuku", TEXT), flp);
        facts.addView(vDiv());
        LinearLayout modeFactCol = factCol(tr("mode"), tr(serverMode == 1 ? "mode_local" : "mode_connect"), BLUE_SOFT);
        homeFactModeVal = (TextView) modeFactCol.getChildAt(1);
        facts.addView(modeFactCol, new LinearLayout.LayoutParams(0, -2, 1f));
        facts.addView(vDiv());
        String liveStateLabel = statusState == 2 ? tr("active")
            : statusState == 1 ? tr("working")
            : statusState == 3 ? tr("failed")
            : tr("ready");
        LinearLayout stateFactCol = factCol(tr("state"), liveStateLabel, TEXT);
        homeFactStateVal = (TextView) stateFactCol.getChildAt(1);
        facts.addView(stateFactCol, new LinearLayout.LayoutParams(0, -2, 1f));
        facts.addView(vDiv());
        facts.addView(factCol(tr("uid"), appUidText, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(facts, cardLp(0, 12));

        boolean startEnabled = !busy && statusState != 1 && statusState != 2;
        boolean stopEnabled = statusState != 0;
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        TextView bStart = actionPill(statusState == 1 ? tr("starting_btn") : tr("start"), 0, startEnabled, v -> startFlow());
        TextView bStop = actionPill(tr("stop"), 1, stopEnabled, v -> stopFlow());
        TextView bCheck = actionPill(method == 1 ? tr("grant") : tr("root"), 2, !busy, v -> checkAccess(method, true));
        homeStartPill = bStart;
        homeStopPill = bStop;
        homeCheckPill = bCheck;
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, px(46), 1f);
        p1.rightMargin = px(8);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, px(46), 1f);
        p2.rightMargin = px(8);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, px(46), 1f);
        pills.addView(bStart, p1);
        pills.addView(bStop, p2);
        pills.addView(bCheck, p3);
        col.addView(pills, cardLp(0, 14));

        homeModeCard = borderedCard();
        homeModeCard.setTag(Motion.CASCADE);
        populateHomeModeCard(homeModeCard);
        col.addView(homeModeCard, cardLp(0, 12));

        homeDynSlot = column();
        populateHomeDynSlot(homeDynSlot, false);
        col.addView(homeDynSlot, cardLp(0, 12));

        homeStepsSlot = borderedCard();
        homeStepsSlot.setPadding(px(16), px(12), px(16), px(12));
        homeStepsSlot.setTag(Motion.CASCADE);
        populateHomeStepsCard(homeStepsSlot);
        col.addView(homeStepsSlot, cardLp(0, 12));

        LinearLayout devCard = borderedCard();
        devCard.setTag(Motion.CASCADE);
        devCard.addView(listRow(tr("priv"), method == 0 ? "Root (su)" : "Shizuku", methodStatus,
            methodState == 2 ? BLUE_SOFT : (methodState == 3 ? ERR_FG : TEXT2),
            tr("switch"), false, !busy, false, v -> {
                method = method == 0 ? 1 : 0;
                checkAccess(method, true);
            }));
        devCard.addView(listRow(tr("engine_owner"), ownerStatus, null, TEXT2,
            tr("check"), true, !busy, true, v -> checkOwner()));
        col.addView(devCard, cardLp(0, 12));

        homeLogSlot = column();
        if (serverMode == 1) {
            homeLogSlot.addView(buildLogCard(true, 160));
            homeLogSlot.setVisibility(View.VISIBLE);
        } else {
            homeLogSlot.setVisibility(View.GONE);
        }
        col.addView(homeLogSlot, cardLp(0, 0));

        return page("Sonoro", col);
    }

    private void syncHeroPulse() {
        if (heroPulseAnim != null) {
            heroPulseAnim.cancel();
            heroPulseAnim = null;
        }
        if (homeHeroCircle == null) return;
        if (statusState == 1 || statusState == 2) {
            final FrameLayout c = homeHeroCircle;
            final float amp = statusState == 1 ? 0.11f : 0.06f;
            ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(statusState == 1 ? 680 : 1400);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.addUpdateListener(a -> {
                float f = (Float) a.getAnimatedValue();
                float s = 1f + amp * f;
                c.setScaleX(s);
                c.setScaleY(s);
                c.setAlpha(statusState == 1 ? (0.72f + 0.28f * f) : (0.88f + 0.12f * f));
            });
            pulse.start();
            heroPulseAnim = pulse;
        } else {
            homeHeroCircle.setScaleX(1f);
            homeHeroCircle.setScaleY(1f);
            homeHeroCircle.setAlpha(1f);
        }
    }

    private void updateHomeStatusUi(boolean animate) {
        if (currentTab != 0 || homeCol == null || homeHeroCard == null) {
            if (currentTab == 0) refreshCurrentTab();
            return;
        }
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(homeCol, makeModeTransition());
        }
        String heroTitle = statusState == 2 ? tr("connected")
            : statusState == 1 ? tr("starting")
            : statusState == 3 ? tr("failed")
            : tr("ready");
        String heroSub = statusState == 1 ? (serverMode == 1 ? tr("prep_local") : tr("start_vpn_proxy"))
            : statusState == 2 ? (serverMode == 1 ? (tr("local_active_pfx") + " · " + (localCoop ? localAdvIp : "127.0.0.1")) : (tr("vpn_active_pfx") + " · " + parseSummary))
            : statusState == 3 ? tr("open_sys_log")
            : methodStatus;
        int heroBg = statusState == 3 ? HERO_ERR : (statusState == 2 || statusState == 1 ? HERO_ACTIVE : HERO_IDLE);
        homeHeroCard.setBackground(glassBg(heroBg, statusState == 2 ? 0x55B4C8FF : CARD_RIM_TOP, CARD_RIM_BOT, 24));

        if (homeHeroCircle != null) {
            int circleFill = statusState == 3 ? 0x26FFB4AB : 0x24E2ECFF;
            int circleBorder = statusState == 3 ? 0x66FFB4AB : 0x55E2ECFF;
            homeHeroCircle.setBackground(cardBg(circleFill, circleBorder, 27));
            if (homeHeroIc != null) {
                homeHeroIc.setImageDrawable(new Icons(method == 1 && statusState != 2 ? Icons.TERMINAL : Icons.SHIELD, statusState == 3 ? ERR_FG : HERO_FG));
            }
            if (animate) {
                homeHeroCircle.setScaleX(0.82f);
                homeHeroCircle.setScaleY(0.82f);
                homeHeroCircle.animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(Motion.BACK_OUT).start();
            }
        }
        if (homeHeroTitle != null) {
            homeHeroTitle.setText(heroTitle);
            homeHeroTitle.setTextColor(statusState == 3 ? ERR_FG : HERO_FG);
        }
        if (homeHeroSub != null) {
            homeHeroSub.setText(heroSub);
            homeHeroSub.setTextColor(statusState == 3 ? 0xBBFFB4AB : HERO_SUB);
        }
        if (homeFactStateVal != null) {
            String liveStateLabel = statusState == 2 ? tr("active")
                : statusState == 1 ? tr("working")
                : statusState == 3 ? tr("failed")
                : tr("ready");
            homeFactStateVal.setText(liveStateLabel);
        }

        boolean startEnabled = !busy && statusState != 1 && statusState != 2;
        boolean stopEnabled = statusState != 0;
        if (homeStartPill != null) {
            styleActionPill(homeStartPill, statusState == 1 ? tr("starting_btn") : tr("start"), 0, startEnabled);
            if (animate && statusState == 1) {
                homeStartPill.setScaleX(0.95f);
                homeStartPill.setScaleY(0.95f);
                homeStartPill.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(Motion.BACK_OUT).start();
            }
        }
        if (homeStopPill != null) {
            styleActionPill(homeStopPill, tr("stop"), 1, stopEnabled);
            if (animate && statusState == 0) {
                homeStopPill.setScaleX(0.94f);
                homeStopPill.setScaleY(0.94f);
                homeStopPill.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(Motion.BACK_OUT).start();
            }
        }
        if (homeCheckPill != null) {
            styleActionPill(homeCheckPill, method == 1 ? tr("grant") : tr("root"), 2, !busy);
        }
        if (homeStepsSlot != null) {
            populateHomeStepsCard(homeStepsSlot);
        }
        syncHeroPulse();
    }

    private boolean isServerUpdateAvailable() {
        return serverMode == 1
            && !githubServerVer.isEmpty()
            && !localBuiltVer.isEmpty()
            && !"Checking...".equals(githubServerVer)
            && !tr("checking_gh").equals(githubServerVer)
            && !"Offline".equals(githubServerVer)
            && !githubServerVer.equals(localBuiltVer);
    }

    private void populateHomeSrvUpdSlot(LinearLayout slot) {
        if (slot == null) return;
        slot.removeAllViews();
        if (!isServerUpdateAvailable()) {
            slot.setVisibility(View.GONE);
            return;
        }
        slot.setVisibility(View.VISIBLE);
        LinearLayout card = borderedCard();
        card.setBackground(glassBg(HERO_ACTIVE, 0x669AB8F5, 0x222D5CE0, 20));
        String sub = String.format(java.util.Locale.ROOT, tr("srv_installed_sub"), "v" + localBuiltVer);
        card.addView(listRow(tr("srv_upd_avail") + " · v" + githubServerVer,
            sub, null, BLUE_SOFT, tr("update_btn"), true, !busy, true, v -> updateServerNowAsync()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(12);
        slot.addView(card, lp);
    }

    private void populateHomeModeCard(LinearLayout card) {
        if (card == null) return;
        card.removeAllViews();
        card.addView(methodRow(Icons.GLOBE, tr("connect_srv"),
            parseSummary,
            parseOk ? BLUE_SOFT : ERR_FG,
            serverMode == 0, false, v -> switchServerMode(0, 0)));
        card.addView(hDiv(16));
        card.addView(methodRow(Icons.TERMINAL, tr("local_srv"),
            localCoop ? (tr("coop") + " · " + localAdvIp) : (tr("solo") + " · 127.0.0.1"),
            TEXT2,
            serverMode == 1, true, v -> switchServerMode(1, 0)));
    }

    private void populateHomeDynSlot(LinearLayout slot, boolean animateNewButtons) {
        if (slot == null) return;
        slot.removeAllViews();
        if (serverMode == 0) {
            LinearLayout connCard = borderedCard();
            connCard.setPadding(px(16), px(14), px(16), px(16));

            LinearLayout connHdr = new LinearLayout(this);
            connHdr.setOrientation(LinearLayout.HORIZONTAL);
            connHdr.setGravity(Gravity.CENTER_VERTICAL);
            connHdr.addView(iconPlate(Icons.GLOBE, PLATE, PLATE_FG), new LinearLayout.LayoutParams(px(44), px(44)));
            LinearLayout ctCol = column();
            ctCol.setPadding(px(12), 0, px(8), 0);
            TextView cTitle = text(tr("tab_conn"), 15, TEXT);
            cTitle.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
            final TextView cSub = text(parseSummary, 12, parseOk ? BLUE_SOFT : ERR_FG);
            cSub.setSingleLine(true);
            cSub.setEllipsize(TextUtils.TruncateAt.END);
            ctCol.addView(cTitle);
            ctCol.addView(cSub);
            connHdr.addView(ctCol, new LinearLayout.LayoutParams(0, -2, 1f));
            View bList = miniPill(tr("list"), true, true, v -> {
                bar.select(1, true);
                showTab(1, true);
            });
            connHdr.addView(bList);
            connCard.addView(connHdr);

            final EditText targetEdit = darkInput(activeTarget, true, tr("home_input_hint"));
            targetEdit.setEnabled(!busy);
            targetEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    activeTarget = s.toString();
                    if (prefs != null) prefs.edit().putString(KEY_ACTIVE_TARGET, activeTarget).apply();
                    detectActiveTarget();
                    cSub.setText(parseSummary);
                    cSub.setTextColor(parseOk ? BLUE_SOFT : ERR_FG);
                }
            });
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, px(44));
            elp.topMargin = px(12);
            connCard.addView(targetEdit, elp);
            slot.addView(connCard);
            if (animateNewButtons) Motion.cascade(connCard, 40, px(14));
        } else {
            LinearLayout locCard = borderedCard();
            locCard.setPadding(px(16), px(14), px(16), px(16));

            LinearLayout hdr = new LinearLayout(this);
            hdr.setOrientation(LinearLayout.HORIZONTAL);
            hdr.setGravity(Gravity.CENTER_VERTICAL);
            hdr.addView(iconPlate(Icons.TERMINAL, PLATE, PLATE_FG), new LinearLayout.LayoutParams(px(44), px(44)));
            LinearLayout lc = column();
            lc.setPadding(px(12), 0, px(8), 0);
            TextView lTitle = text(tr("host_mode"), 15, TEXT);
            lTitle.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
            refreshLocalSummaryText();
            TextView lSub = text(localStatusLine, 12, BLUE_SOFT);
            lSub.setSingleLine(true);
            lSub.setEllipsize(TextUtils.TruncateAt.END);
            lc.addView(lTitle);
            lc.addView(lSub);
            hdr.addView(lc, new LinearLayout.LayoutParams(0, -2, 1f));
            View soloBtn = miniPill(tr("solo"), !localCoop, !busy, v -> {
                localCoop = false;
                prefs.edit().putBoolean(KEY_LOCAL_COOP, false).apply();
                TransitionManager.beginDelayedTransition(homeCol != null ? homeCol : slot, makeModeTransition());
                populateHomeModeCard(homeModeCard);
                populateHomeDynSlot(homeDynSlot, true);
            });
            View coopBtn = miniPill(tr("coop"), localCoop, !busy, v -> {
                localCoop = true;
                prefs.edit().putBoolean(KEY_LOCAL_COOP, true).apply();
                TransitionManager.beginDelayedTransition(homeCol != null ? homeCol : slot, makeModeTransition());
                populateHomeModeCard(homeModeCard);
                populateHomeDynSlot(homeDynSlot, true);
                new Thread(() -> {
                    final String[] best = LocalServerManager.detectBestCoopIp();
                    runOnUiThread(() -> {
                        localAdvIp = best[0];
                        localIpKind = best[1];
                        prefs.edit().putString(KEY_LOCAL_ADV_IP, localAdvIp).apply();
                        if (currentTab == 0 && serverMode == 1 && homeDynSlot != null) {
                            TransitionManager.beginDelayedTransition(homeCol != null ? homeCol : homeDynSlot, makeModeTransition());
                            populateHomeModeCard(homeModeCard);
                            populateHomeDynSlot(homeDynSlot, false);
                        }
                    });
                }, "sonoro-coop-ip").start();
            });
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, px(34));
            sLp.rightMargin = px(6);
            hdr.addView(soloBtn, sLp);
            hdr.addView(coopBtn);
            locCard.addView(hdr);

            String verSummary = "GitHub " + githubServerVer
                + (githubAssetsVer.isEmpty() ? "" : (" · assets " + githubAssetsVer))
                + " · " + tr("mode_local") + " " + (localBuiltVer.isEmpty() ? tr("not_built") : ("v" + localBuiltVer));
            TextView verTv = text(verSummary, 11.5f, localBuiltVer.isEmpty() ? TEXT2 : BLUE_SOFT);
            verTv.setPadding(px(2), px(10), px(2), 0);
            locCard.addView(verTv);

            if (localCoop) {
                LinearLayout ipBox = new LinearLayout(this);
                ipBox.setOrientation(LinearLayout.HORIZONTAL);
                ipBox.setGravity(Gravity.CENTER_VERTICAL);
                ipBox.setPadding(px(14), px(10), px(10), px(10));
                ipBox.setBackground(cardBg(0xFF0D1016, OUTLINE, 14));

                LinearLayout ipCol = column();
                TextView ipVal = text(localAdvIp.isEmpty() ? tr("detecting") : localAdvIp, 14, TEXT);
                ipVal.setTypeface(Typeface.MONOSPACE);
                ipVal.setSingleLine(true);
                TextView ipSub = text(localIpKind + " · Ports 5500/TCP · 8080/TCP · 13100/UDP", 11, TEXT2);
                ipCol.addView(ipVal);
                ipCol.addView(ipSub);
                ipBox.addView(ipCol, new LinearLayout.LayoutParams(0, -2, 1f));
                ipBox.addView(miniPill(tr("copy"), true, !localAdvIp.isEmpty(),
                    v -> PrivilegedHelper.copyText(this, localAdvIp)));

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
                ilp.topMargin = px(10);
                locCard.addView(ipBox, ilp);
            } else {
                TextView pathTv = text("Storage: /0/sonoro · Gateway :5500 / :8080", 11.5f, TEXT2);
                pathTv.setPadding(px(2), px(4), px(2), 0);
                locCard.addView(pathTv);
            }
            slot.addView(locCard);
            if (animateNewButtons) Motion.cascade(locCard, 40, px(14));
        }
    }

    private void populateHomeStepsCard(LinearLayout stepsCard) {
        if (stepsCard == null) return;
        stepsCard.removeAllViews();
        String[] keys = serverMode == 1
            ? new String[]{"step_l1", "step_l2", "step_l3", "step_l4"}
            : new String[]{"step_c1", "step_c2", "step_c3", "step_c4"};
        for (int i = 0; i < keys.length; i++) {
            boolean popDot = stepStates[i] != prevStepStates[i] && stepStates[i] > 0;
            prevStepStates[i] = stepStates[i];
            View row = stepRow(tr(keys[i]), stepStates[i], i == keys.length - 1);
            if (popDot && row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0) {
                View dot = ((ViewGroup) row).getChildAt(0);
                dot.setScaleX(0.55f);
                dot.setScaleY(0.55f);
                dot.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(Motion.BACK_OUT).start();
            }
            stepsCard.addView(row);
        }
    }

    // ---------------------------------------------------------------- CONNECTION TAB
    private ScrollView connectionTab() {
        LinearLayout col = column();

        col.addView(sectionLabel(tr("add_conn")));
        LinearLayout addCard = borderedCard();
        addCard.setPadding(px(16), px(16), px(16), px(16));

        final LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView detPill = new TextView(this);
        detPill.setTextSize(11.5f);
        detPill.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        detPill.setPadding(px(10), px(4), px(10), px(4));
        final TextView msgTv = text(connMsg, 12, TEXT2);
        msgTv.setPadding(px(8), 0, 0, 0);
        badgeRow.addView(detPill);
        badgeRow.addView(msgTv, new LinearLayout.LayoutParams(0, -2, 1f));
        updateBadgePill(detPill, msgTv);
        addCard.addView(badgeRow);

        final EditText draftEdit = darkInput(draftInput, false, tr("conn_hint"));
        draftEdit.setEnabled(!busy);
        draftEdit.setMinLines(4);
        draftEdit.setMaxLines(6);
        draftEdit.setGravity(Gravity.TOP | Gravity.START);
        draftEdit.setTypeface(Typeface.MONOSPACE);
        draftEdit.setTextSize(12.5f);
        draftEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                draftInput = s.toString();
                connMsg = "";
                detectDraft();
                updateBadgePill(detPill, msgTv);
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = px(8);
        addCard.addView(draftEdit, dlp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, px(12), 0, 0);
        View bAdd = miniPill(tr("add"), true, !busy, v -> addConnection(draftInput));
        View bPaste = miniPill(tr("paste"), false, !busy, v -> {
            String clip = PrivilegedHelper.pasteText(this);
            if (clip == null || clip.trim().isEmpty()) {
                connMsg = tr("clip_empty");
                updateBadgePill(detPill, msgTv);
            } else {
                draftEdit.setText(clip.trim());
            }
        });
        View bClear = miniPill(tr("clear"), false, !busy, v -> {
            draftEdit.setText("");
            connMsg = "";
            draftBadge = "";
            updateBadgePill(detPill, msgTv);
        });
        LinearLayout.LayoutParams mr = new LinearLayout.LayoutParams(-2, px(34));
        mr.rightMargin = px(8);
        btnRow.addView(bAdd, mr);
        btnRow.addView(bPaste, mr);
        btnRow.addView(bClear, new LinearLayout.LayoutParams(-2, px(34)));
        addCard.addView(btnRow);
        col.addView(addCard, cardLp(0, 16));

        col.addView(sectionLabel(tr("saved")));
        LinearLayout listCard = borderedCard();
        listCard.setTag(Motion.CASCADE);
        if (savedConns.isEmpty()) {
            TextView empty = text(tr("no_saved_conns"), 13, TEXT2);
            empty.setPadding(px(18), px(16), px(18), px(16));
            listCard.addView(empty);
        } else {
            for (int i = 0; i < savedConns.size(); i++) {
                final int idx = i;
                final SavedConn sc = savedConns.get(i);
                final boolean sel = sc.target.trim().equals(activeTarget.trim());

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(px(16), px(12), px(10), px(12));
                row.setBackground(rowBg(sel ? 0xFF111824 : Color.TRANSPARENT));
                row.setOnClickListener(v -> {
                    activeTarget = sc.target;
                    detectActiveTarget();
                    persistSavedConns(true);
                    connMsg = tr("selected");
                    refreshCurrentTab();
                });

                TextView kindTv = new TextView(this);
                kindTv.setText(sc.kind);
                kindTv.setTextColor(sel ? PLATE_FG : TEXT2);
                kindTv.setTextSize(11f);
                kindTv.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
                kindTv.setGravity(Gravity.CENTER);
                kindTv.setPadding(px(10), px(5), px(10), px(5));
                kindTv.setMinWidth(px(58));
                kindTv.setBackground(cardBg(sel ? PLATE : 0xFF151922, 0, 10));
                row.addView(kindTv);

                LinearLayout mid = column();
                mid.setPadding(px(12), 0, px(8), 0);
                TextView nTv = text(sc.name, 14, sel ? HERO_FG : TEXT);
                nTv.setTypeface(Typeface.create(Typeface.DEFAULT, sel ? 600 : 500, false));
                nTv.setSingleLine(true);
                nTv.setEllipsize(TextUtils.TruncateAt.END);
                TextView tTv = text(sc.target, 11.5f, TEXT2);
                tTv.setSingleLine(true);
                tTv.setEllipsize(TextUtils.TruncateAt.END);
                mid.addView(nTv);
                mid.addView(tTv);
                row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView del = new TextView(this);
                del.setText("×");
                del.setTextColor(TEXT2);
                del.setTextSize(18f);
                del.setGravity(Gravity.CENTER);
                del.setBackground(rowBg(Color.TRANSPARENT));
                del.setOnClickListener(v -> {
                    if (idx < savedConns.size()) {
                        savedConns.remove(idx);
                        persistSavedConns();
                        refreshCurrentTab();
                    }
                });
                row.addView(del, new LinearLayout.LayoutParams(px(36), px(36)));

                listCard.addView(row);
                if (i < savedConns.size() - 1) listCard.addView(hDiv(16));
            }
        }
        col.addView(listCard, cardLp(0, 0));

        return page(tr("tab_conn"), col);
    }

    private void updateBadgePill(TextView pill, TextView msgTv) {
        if (draftBadge.isEmpty()) {
            pill.setVisibility(View.GONE);
        } else {
            pill.setVisibility(View.VISIBLE);
            pill.setText(draftBadge);
            pill.setTextColor(draftOk ? PLATE_FG : ERR_FG);
            pill.setBackground(cardBg(draftOk ? PLATE : HERO_ERR, 0, 12));
        }
        msgTv.setText(connMsg);
        msgTv.setVisibility(connMsg.isEmpty() && draftBadge.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- DATABASE TAB
    private ScrollView playersTab() {
        LinearLayout col = column();
        // Prevent Motion.scrub from 3D-tilting or fading the page when IME opens on form inputs.
        col.setTag(Motion.PINNED);

        col.addView(sectionLabel(tr("file_lbl")));
        LinearLayout dbCard = borderedCard();
        dbCard.setPadding(px(16), px(14), px(16), px(14));

        String dbInfo = LocalServerManager.hasStorageAccess()
            ? (playerList.size() + " " + tr("players_suffix") + " · " + (playerDbBytes / 1024) + " KB")
            : tr("stor_perm_needed");
        LinearLayout dbHdr = new LinearLayout(this);
        dbHdr.setOrientation(LinearLayout.HORIZONTAL);
        dbHdr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout dc = column();
        TextView dTitle = text("/0/sonoro/state/gamesv.db", 13.5f, TEXT);
        dTitle.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        dTitle.setSingleLine(true);
        dTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        dc.addView(dTitle);
        dc.addView(text(dbInfo, 12, BLUE_SOFT));
        if (!playerDbMsg.isEmpty()) {
            dc.addView(text(playerDbMsg, 11.5f, playerDbMsg.startsWith("ERR") ? ERR_FG : OK_GREEN));
        }
        dbHdr.addView(dc, new LinearLayout.LayoutParams(0, -2, 1f));
        dbCard.addView(dbHdr);

        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setPadding(0, px(12), 0, 0);
        View bRef = miniPill(tr("refresh"), false, !busy, v -> reloadPlayersAsync(true));
        View bNew = miniPill(tr("new_btn"), true, !busy, v -> openPlayerEditor(null));
        View bBak = miniPill(tr("backup"), false, !busy, v -> backupDbAsync());
        LinearLayout.LayoutParams br1 = new LinearLayout.LayoutParams(-2, px(34));
        br1.rightMargin = px(8);
        LinearLayout.LayoutParams br2 = new LinearLayout.LayoutParams(-2, px(34));
        br2.rightMargin = px(8);
        actRow.addView(bRef, br1);
        actRow.addView(bNew, br2);
        actRow.addView(bBak, new LinearLayout.LayoutParams(-2, px(34)));
        if (!LocalServerManager.hasStorageAccess()) {
            View bAllow = miniPill(tr("allow_storage"), true, true, v -> grantStorageAsync());
            LinearLayout.LayoutParams ba = new LinearLayout.LayoutParams(-2, px(34));
            ba.leftMargin = px(8);
            actRow.addView(bAllow, ba);
        }
        dbCard.addView(actRow);
        col.addView(dbCard, cardLp(0, 14));

        if (editingOpen) {
            col.addView(sectionLabel(tr(editingIsNew ? "new_player" : "edit_player")));
            LinearLayout edCard = borderedCard();
            edCard.setPadding(px(16), px(14), px(16), px(16));

            edCard.addView(text(tr("uid_name"), 12, TEXT2));
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            EditText uidEd = numInput(editUid, tr("auto_uid"), s -> editUid = s);
            EditText nameEd = darkInput(editName, true, "Rover");
            nameEd.addTextChangedListener(simpleWatcher(s -> editName = s));
            LinearLayout.LayoutParams lpU1 = new LinearLayout.LayoutParams(0, px(44), 1f);
            lpU1.rightMargin = px(8);
            row1.addView(uidEd, lpU1);
            row1.addView(nameEd, new LinearLayout.LayoutParams(0, px(44), 1.4f));
            LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
            r1p.topMargin = px(6);
            edCard.addView(row1, r1p);

            edCard.addView(padTopLabel(tr("ul_wl"), 10));
            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            EditText lvEd = numInput(editLevel, tr("level_lbl"), s -> editLevel = s);
            EditText wlEd = numInput(editWorldLevel, tr("world_lv_lbl"), s -> editWorldLevel = s);
            LinearLayout.LayoutParams lpU2 = new LinearLayout.LayoutParams(0, px(44), 1f);
            lpU2.rightMargin = px(8);
            row2.addView(lvEd, lpU2);
            row2.addView(wlEd, new LinearLayout.LayoutParams(0, px(44), 1f));
            LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
            r2p.topMargin = px(6);
            edCard.addView(row2, r2p);

            edCard.addView(padTopLabel(tr("credits_astrite"), 10));
            LinearLayout row3 = new LinearLayout(this);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            EditText coinEd = numInput(editCoin, tr("shell_credits"), s -> editCoin = s);
            EditText rareEd = numInput(editRareCoin, tr("astrite"), s -> editRareCoin = s);
            LinearLayout.LayoutParams lpU3 = new LinearLayout.LayoutParams(0, px(44), 1f);
            lpU3.rightMargin = px(8);
            row3.addView(coinEd, lpU3);
            row3.addView(rareEd, new LinearLayout.LayoutParams(0, px(44), 1f));
            LinearLayout.LayoutParams r3p = new LinearLayout.LayoutParams(-1, -2);
            r3p.topMargin = px(6);
            edCard.addView(row3, r3p);

            edCard.addView(padTopLabel(tr("lunite_exp"), 10));
            LinearLayout row4 = new LinearLayout(this);
            row4.setOrientation(LinearLayout.HORIZONTAL);
            EditText cashEd = numInput(editCashCoin, tr("lunite"), s -> editCashCoin = s);
            EditText expEd = numInput(editExp, "EXP", s -> editExp = s);
            LinearLayout.LayoutParams lpU4 = new LinearLayout.LayoutParams(0, px(44), 1f);
            lpU4.rightMargin = px(8);
            row4.addView(cashEd, lpU4);
            row4.addView(expEd, new LinearLayout.LayoutParams(0, px(44), 1f));
            LinearLayout.LayoutParams r4p = new LinearLayout.LayoutParams(-1, -2);
            r4p.topMargin = px(6);
            edCard.addView(row4, r4p);

            LinearLayout sexRow = new LinearLayout(this);
            sexRow.setOrientation(LinearLayout.HORIZONTAL);
            sexRow.setGravity(Gravity.CENTER_VERTICAL);
            sexRow.setPadding(0, px(12), 0, px(4));
            sexRow.addView(text(tr("rover_sex"), 13.5f, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView[] sxBtns = new TextView[2];
            sxBtns[0] = miniPill(tr("male"), editSex == 1, true, null);
            sxBtns[1] = miniPill(tr("female"), editSex == 2, true, null);
            Runnable syncSexPills = () -> {
                for (int k = 0; k < 2; k++) {
                    boolean acc = (editSex == (k + 1));
                    int bg = acc ? PLATE : 0xBD151A24;
                    int fg = acc ? PLATE_FG : TEXT;
                    int rt = acc ? 0x5E9AB8F5 : CARD_RIM_TOP;
                    int rb = acc ? 0x229AB8F5 : CARD_RIM_BOT;
                    sxBtns[k].setTextColor(fg);
                    sxBtns[k].setBackground(new RippleDrawable(ColorStateList.valueOf(PRESSED), glassBg(bg, rt, rb, 17), null));
                }
            };
            sxBtns[0].setOnClickListener(v -> { editSex = 1; syncSexPills.run(); });
            sxBtns[1].setOnClickListener(v -> { editSex = 2; syncSexPills.run(); });
            LinearLayout.LayoutParams sxLp = new LinearLayout.LayoutParams(-2, px(34));
            sxLp.rightMargin = px(8);
            sexRow.addView(sxBtns[0], sxLp);
            sexRow.addView(sxBtns[1], new LinearLayout.LayoutParams(-2, px(34)));
            edCard.addView(sexRow);

            LinearLayout saveRow = new LinearLayout(this);
            saveRow.setOrientation(LinearLayout.HORIZONTAL);
            saveRow.setPadding(0, px(12), 0, 0);
            View bSave = actionPill(tr("save"), 0, true, v -> saveEditingPlayer());
            View bCancel = actionPill(tr("cancel"), 2, true, v -> {
                editingOpen = false;
                refreshCurrentTab();
            });
            LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(0, px(42), 1f);
            sp1.rightMargin = px(8);
            saveRow.addView(bSave, sp1);
            if (!editingIsNew) {
                LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, px(42), 1f);
                sp2.rightMargin = px(8);
                saveRow.addView(bCancel, sp2);
                saveRow.addView(actionPill(tr("delete"), 1, true, v -> deleteEditingPlayer()),
                    new LinearLayout.LayoutParams(0, px(42), 1f));
            } else {
                saveRow.addView(bCancel, new LinearLayout.LayoutParams(0, px(42), 1f));
            }
            edCard.addView(saveRow);
            col.addView(edCard, cardLp(0, 14));
        }

        col.addView(sectionLabel(tr("players")));
        LinearLayout listCard = borderedCard();
        if (playerList.isEmpty()) {
            TextView empty = text(tr("no_players"), 13, TEXT2);
            empty.setPadding(px(18), px(16), px(18), px(16));
            listCard.addView(empty);
        } else {
            for (int i = 0; i < playerList.size(); i++) {
                final PlayerRow pr = playerList.get(i);
                String sx = pr.sex == 2 ? tr("female") : tr("male");
                String sub = "UID " + pr.playerId + " · UL " + pr.level + " · SOL3 " + pr.worldLevel + " · " + sx;
                String note = pr.coin + " Cr · " + pr.rareCoin + " Ast · " + pr.cashCoin + " Lun";
                listCard.addView(listRow(pr.name.isEmpty() ? "Rover" : pr.name, sub, note, BLUE_SOFT,
                    tr("edit"), true, !busy, i == playerList.size() - 1, v -> openPlayerEditor(pr)));
            }
        }
        col.addView(listCard, cardLp(0, 14));

        if (!dbBackups.isEmpty()) {
            col.addView(sectionLabel(tr("backups")));
            LinearLayout bCard = borderedCard();
            for (int i = 0; i < dbBackups.size(); i++) {
                final File bf = dbBackups.get(i);
                bCard.addView(listRow(bf.getName(), (bf.length() / 1024) + " KB", null, TEXT2,
                    tr("restore"), false, !busy, i == dbBackups.size() - 1, v -> restoreDbAsync(bf)));
            }
            col.addView(bCard, cardLp(0, 0));
        }

        return page(tr("tab_db"), col);
    }

    // ---------------------------------------------------------------- SYSTEM TAB
    private ScrollView systemTab() {
        LinearLayout col = column();
        sysCol = col;

        col.addView(sectionLabel(tr("mode")));
        sysModeCard = borderedCard();
        sysModeCard.setTag(Motion.CASCADE);
        populateSysModeCard(sysModeCard);
        col.addView(sysModeCard, cardLp(0, 16));

        sysPrivHeader = sectionLabel(serverMode == 1 ? tr("priv_stor") : tr("priv"));
        col.addView(sysPrivHeader);
        LinearLayout accCard = borderedCard();
        accCard.setTag(Motion.CASCADE);
        accCard.addView(methodRow(Icons.TERMINAL, "Shizuku",
            method == 1 ? methodStatus : tr("no_root_chk"),
            method == 1 ? (methodState == 2 ? BLUE_SOFT : (methodState == 3 ? ERR_FG : TEXT2)) : TEXT2,
            method == 1, false, v -> checkAccess(1, true)));
        accCard.addView(hDiv(16));
        accCard.addView(methodRow(Icons.SHIELD, "Root (su)",
            method == 0 ? methodStatus : tr("magisk_grant"),
            method == 0 ? (methodState == 2 ? BLUE_SOFT : (methodState == 3 ? ERR_FG : TEXT2)) : TEXT2,
            method == 0, false, v -> checkAccess(0, true)));
        sysPrivStorageDiv = hDiv(16);
        accCard.addView(sysPrivStorageDiv);
        boolean stOk = LocalServerManager.hasStorageAccess();
        sysPrivStorageRow = listRow(tr("storage_dir"), stOk ? tr("granted") : tr("perm_needed"), null,
            stOk ? BLUE_SOFT : ERR_FG, stOk ? "OK" : tr("allow"), !stOk, !busy, false, v -> grantStorageAsync());
        accCard.addView(sysPrivStorageRow);
        if (serverMode != 1) {
            sysPrivStorageDiv.setVisibility(View.GONE);
            sysPrivStorageRow.setVisibility(View.GONE);
        }
        accCard.addView(hDiv(16));
        accCard.addView(listRow(tr("engine_owner"), ownerStatus, null, TEXT2,
            tr("check"), true, !busy, false, v -> checkOwner()));
        accCard.addView(hDiv(16));
        accCard.addView(listRow(tr("clean_rust"), rustCacheSizeText + " · " + tr("clean_rust_sub"), null, TEXT2,
            tr("clean_btn"), false, !busy, true, v -> promptCleanRustCache()));
        col.addView(accCard, cardLp(0, 16));

        sysSrvHeader = sectionLabel(tr("local_compiler"));
        col.addView(sysSrvHeader);
        sysSrvCard = borderedCard();
        sysSrvCard.setTag(Motion.CASCADE);
        sysSrvCard.addView(listRow(tr("sync_assets_db"), localStatusLine, null, BLUE_SOFT,
            tr("sync"), true, !busy, false, v -> syncAssetsAsync()));
        sysSrvCard.addView(hDiv(16));
        sysSrvCard.addView(listRow(tr("rust_compiler"), compilerReady ? tr("ready") : tr("not_installed"), null,
            compilerReady ? BLUE_SOFT : TEXT2, compilerReady ? tr("rebuild") : tr("install"), true, !busy, true, v -> buildServerManualAsync()));
        col.addView(sysSrvCard, cardLp(0, 16));
        if (serverMode != 1) {
            sysSrvHeader.setVisibility(View.GONE);
            sysSrvCard.setVisibility(View.GONE);
        }

        col.addView(sectionLabel(tr("log_lbl")));
        col.addView(buildLogCard(false, 200), cardLp(0, 0));

        return page(tr("tab_sys"), col);
    }

    private void populateSysModeCard(LinearLayout card) {
        if (card == null) return;
        card.removeAllViews();
        card.addView(methodRow(Icons.GLOBE, tr("connect_srv"),
            tr("remote_ip_sub"),
            TEXT2,
            serverMode == 0, false, v -> switchServerMode(0, 2)));
        card.addView(hDiv(16));
        card.addView(methodRow(Icons.TERMINAL, tr("local_srv"),
            localCoop ? (tr("coop") + " · " + localAdvIp) : (tr("solo") + " · /0/sonoro + Database"),
            TEXT2,
            serverMode == 1, false, v -> switchServerMode(1, 2)));
        card.addView(hDiv(16));
        card.addView(listRow(tr("lang_title"), I18n.currentLabel(this), null, BLUE_SOFT,
            tr("change"), false, !busy, false, v -> I18n.showPicker(this, () -> {
                installBar(currentTab);
                refreshCurrentTab();
            })));
        card.addView(hDiv(16));
        card.addView(listRow(tr("setup_wiz"), tr("setup_wiz_sub"), null, TEXT2,
            tr("open"), false, !busy, true, v -> reopenWizard()));
    }

    // ---------------------------------------------------------------- ABOUT TAB
    private ScrollView aboutTab() {
        if (updatePulseAnim != null) {
            updatePulseAnim.cancel();
            updatePulseAnim = null;
        }
        LinearLayout col = column();
        col.setClipChildren(false);
        col.setClipToPadding(false);

        // Top-centered app emblem & app name
        LinearLayout hdrBox = new LinearLayout(this);
        hdrBox.setOrientation(LinearLayout.VERTICAL);
        hdrBox.setGravity(Gravity.CENTER_HORIZONTAL);
        hdrBox.setPadding(0, px(6), 0, px(16));

        final SonoroLogoView logo = new SonoroLogoView(this);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(px(118), px(118));
        lLp.bottomMargin = px(8);
        hdrBox.addView(logo, lLp);
        logo.post(logo::play);

        final SplitTitle titleView = new SplitTitle(this, "Sonoro", 32, TEXT, 700);
        titleView.setCentered(true);
        hdrBox.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView verTv = text(tr("version_lbl") + " " + appVerName + " (" + appVerCode + ") · arm64-v8a", 12.5f, TEXT2);
        verTv.setGravity(Gravity.CENTER);
        verTv.setPadding(0, px(4), 0, 0);
        hdrBox.addView(verTv);
        col.addView(hdrBox, new LinearLayout.LayoutParams(-1, -2));

        if (updateAvailable) {
            final LinearLayout updCard = new LinearLayout(this);
            updCard.setOrientation(LinearLayout.VERTICAL);
            updCard.setPadding(px(16), px(16), px(16), px(16));
            updCard.setBackground(glassBg(HERO_ACTIVE, 0x779AB8F5, 0x332D5CE0, 24));

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            final FrameLayout icRing = new FrameLayout(this);
            icRing.setBackground(cardBg(0x289AB8F5, 0x889AB8F5, 24));
            final ImageView dlIc = new ImageView(this);
            dlIc.setImageDrawable(new Icons(Icons.DOWNLOAD, HERO_FG));
            icRing.addView(dlIc, new FrameLayout.LayoutParams(px(22), px(22), Gravity.CENTER));
            topRow.addView(icRing, new LinearLayout.LayoutParams(px(46), px(46)));

            LinearLayout txtCol = column();
            txtCol.setPadding(px(14), 0, px(8), 0);
            TextView uTitle = text(tr("upd_avail") + " · v" + remoteVerName, 16, HERO_FG);
            uTitle.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
            TextView uSub = text(remoteChangelog.isEmpty() ? String.format(java.util.Locale.ROOT, tr("build_ready"), remoteVerCode) : remoteChangelog, 12.5f, HERO_SUB);
            txtCol.addView(uTitle);
            txtCol.addView(uSub);
            topRow.addView(txtCol, new LinearLayout.LayoutParams(0, -2, 1f));

            final TextView newBadge = new TextView(this);
            newBadge.setText(tr("new_badge"));
            newBadge.setTextColor(PLATE_FG);
            newBadge.setTextSize(10.5f);
            newBadge.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
            newBadge.setPadding(px(9), px(4), px(9), px(4));
            newBadge.setBackground(cardBg(PLATE, 0x669AB8F5, 10));
            topRow.addView(newBadge);
            updCard.addView(topRow);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, px(14), 0, 0);
            View bGet = actionPill(tr("download"), 0, true, v -> openUrl(remoteUpdateUrl));
            btnRow.addView(bGet, new LinearLayout.LayoutParams(-1, px(44)));
            updCard.addView(btnRow);

            // Entrance pop + breathing pulse + blur clear on update card
            updCard.setAlpha(0f);
            updCard.setTranslationY(px(22));
            updCard.setScaleX(0.95f);
            updCard.setScaleY(0.95f);
            ValueAnimator enter = ValueAnimator.ofFloat(0f, 1f);
            enter.setStartDelay(180);
            enter.setDuration(560);
            enter.setInterpolator(Motion.EXPO_OUT);
            enter.addUpdateListener(a -> {
                float f = (Float) a.getAnimatedValue();
                float s = 0.95f + 0.05f * f;
                updCard.setAlpha(f);
                updCard.setTranslationY(px(22) * (1f - f));
                updCard.setScaleX(s);
                updCard.setScaleY(s);
                Motion.motionBlur(updCard, 0.5f, (float) Math.sin(Math.PI * f) * 8f * dp);
            });
            enter.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    Motion.motionBlur(updCard, 0f, 0f);
                    if (dlIc.getDrawable() instanceof Icons) ((Icons) dlIc.getDrawable()).play();
                }
            });
            enter.start();

            ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(1500);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.addUpdateListener(a -> {
                float f = (Float) a.getAnimatedValue();
                float s = 1f + 0.09f * f;
                icRing.setScaleX(s);
                icRing.setScaleY(s);
                newBadge.setAlpha(0.76f + 0.24f * f);
            });
            pulse.start();
            updatePulseAnim = pulse;

            col.addView(updCard, cardLp(0, 14));
        }

        col.addView(sectionLabel(tr("updates")));
        LinearLayout chkUpdCard = borderedCard();
        chkUpdCard.setTag(Motion.CASCADE);
        String updMsg = updateChecking ? tr("checking_gh")
            : updateAvailable ? (tr("upd_avail") + ": v" + remoteVerName + " (" + remoteVerCode + ")")
            : (!remoteVerName.isEmpty() ? (tr("up_to_date") + " · v" + remoteVerName) : tr("tap_check"));
        chkUpdCard.addView(listRow(tr("app_ver"), updMsg, null,
            updateAvailable ? BLUE_SOFT : TEXT2,
            updateChecking ? "..." : tr("check"), true, !updateChecking, true, v -> checkAppUpdateAsync()));
        col.addView(chkUpdCard, cardLp(0, 14));

        col.addView(sectionLabel(tr("app_sec")));
        LinearLayout infoCard = borderedCard();
        infoCard.setTag(Motion.CASCADE);
        infoCard.addView(listRow("Sonoro", tr("wiz_tagline"), null, TEXT2,
            null, false, true, false, null));
        infoCard.addView(listRow(tr("developer_lbl"), "Kaaelix", null, TEXT2,
            null, false, true, false, null));
        infoCard.addView(listRow(tr("repo_launcher"), "github.com/kaaelix/sonorolauncher", null, BLUE_SOFT,
            tr("open"), false, true, false, v -> openUrl("https://github.com/kaaelix/sonorolauncher")));
        infoCard.addView(listRow(tr("repo_server"), "github.com/kaaelix/sonoroserver", null, BLUE_SOFT,
            tr("open"), false, true, false, v -> openUrl("https://github.com/kaaelix/sonoroserver")));
        infoCard.addView(listRow(tr("setup_wiz"), tr("setup_wiz_sub"), null, TEXT2,
            tr("open"), false, !busy, true, v -> reopenWizard()));
        col.addView(infoCard, cardLp(0, 0));

        return page(tr("tab_about"), col);
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private void applyAppUpdateJson(JSONObject j) {
        if (j == null) return;
        String vn = j.optString("versionName", "").trim();
        long vc = j.optLong("versionCode", 0);
        String ch = j.optString("changelog", "").trim();
        String u = j.optString("url", "").trim();
        if (!vn.isEmpty()) remoteVerName = vn;
        if (vc > 0) remoteVerCode = vc;
        if (!ch.isEmpty()) remoteChangelog = ch;
        if (!u.isEmpty()) remoteUpdateUrl = u;
        updateAvailable = (remoteVerCode > appVerCode)
            || (!remoteVerName.isEmpty() && !remoteVerName.equals(appVerName));
    }

    private void checkAppUpdateAsync() {
        updateChecking = true;
        refreshCurrentTab();
        new Thread(() -> {
            final JSONObject j = LocalServerManager.checkAppUpdate();
            runOnUiThread(() -> {
                updateChecking = false;
                if (j != null) applyAppUpdateJson(j);
                refreshCurrentTab();
            });
        }, "sonoro-upd-chk").start();
    }

    private LinearLayout buildLogCard(boolean isHome, int heightDp) {
        LinearLayout logCard = borderedCard();
        logCard.setPadding(px(14), px(12), px(14), px(14));

        LinearLayout lhdr = new LinearLayout(this);
        lhdr.setOrientation(LinearLayout.HORIZONTAL);
        lhdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView ot = text(tr(isHome ? "log_title" : "output"), 12, TEXT2);
        lhdr.addView(ot, new LinearLayout.LayoutParams(0, -2, 1f));
        View cp = miniPill(tr("copy"), true, true, v -> PrivilegedHelper.copyText(this, logBuf.toString()));
        View cl = miniPill(tr("clear"), false, true, v -> {
            logBuf.setLength(0);
            syncLiveLogViews();
        });
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-2, px(34));
        cpLp.rightMargin = px(8);
        lhdr.addView(cp, cpLp);
        lhdr.addView(cl);
        logCard.addView(lhdr);

        ScrollView logSv = new ScrollView(this);
        logSv.setBackground(cardBg(0xFF0D1016, DIVIDER, 14));
        logSv.setVerticalScrollBarEnabled(true);
        logSv.setScrollbarFadingEnabled(false);
        logSv.setNestedScrollingEnabled(true);
        logSv.setOnTouchListener((v, ev) -> {
            if (v.getParent() != null) {
                int act = ev.getActionMasked();
                boolean disallow = act != MotionEvent.ACTION_UP && act != MotionEvent.ACTION_CANCEL;
                v.getParent().requestDisallowInterceptTouchEvent(disallow);
            }
            return false;
        });

        TextView logTv = new TextView(this);
        logTv.setText(logBuf.length() == 0 ? tr("waiting_start") : logBuf.toString());
        logTv.setTextColor(logBuf.length() == 0 ? TEXT3 : 0xFFC5CDD9);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setTextSize(11.5f);
        logTv.setPadding(px(12), px(10), px(12), px(10));
        logSv.addView(logTv, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams ltvLp = new LinearLayout.LayoutParams(-1, px(heightDp));
        ltvLp.topMargin = px(10);
        logCard.addView(logSv, ltvLp);

        if (isHome) {
            liveHomeLogTv = logTv;
            liveHomeLogScroll = logSv;
        } else {
            liveSysLogTv = logTv;
            liveSysLogScroll = logSv;
        }
        logSv.post(() -> logSv.fullScroll(View.FOCUS_DOWN));
        return logCard;
    }

    private boolean isLogAtBottom(ScrollView sv) {
        if (sv == null || sv.getChildCount() == 0) return true;
        View child = sv.getChildAt(0);
        return (child.getBottom() - (sv.getHeight() + sv.getScrollY())) <= px(48);
    }

    private void updateLogTarget(ScrollView sv, TextView tv, String txt, int col) {
        if (tv == null) return;
        boolean stickBottom = isLogAtBottom(sv);
        tv.setText(txt);
        tv.setTextColor(col);
        if (sv != null && stickBottom) {
            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void syncLiveLogViews() {
        String txt = logBuf.length() == 0 ? tr("empty") : logBuf.toString();
        int col = logBuf.length() == 0 ? TEXT3 : 0xFFC5CDD9;
        updateLogTarget(liveHomeLogScroll, liveHomeLogTv, txt, col);
        updateLogTarget(liveSysLogScroll, liveSysLogTv, txt, col);
    }

    // ---------------------------------------------------------------- BACKEND ACTIONS
    private void appendLog(String line) {
        if (line == null) return;
        final String clean = line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "").replace("\r", "");
        if (busy && serverMode == 1) {
            SonoroBuildService.update(this, "Sonoro", clean);
        }
        runOnUiThread(() -> {
            logBuf.append(clean).append('\n');
            if (logBuf.length() > 180_000) {
                logBuf.delete(0, logBuf.length() - 140_000);
            }
            syncLiveLogViews();
        });
    }

    private void checkAccess(int m, boolean allowOpenManager) {
        method = m;
        if (prefs != null) prefs.edit().putInt(KEY_METHOD, m).apply();
        methodState = 1;
        methodStatus = m == 0 ? "Requesting root..." : "Requesting Shizuku...";
        refreshCurrentTab();

        bgExec.execute(() -> {
            String res = (m == 0) ? PrivilegedHelper.checkRoot() : PrivilegedHelper.checkShizuku(true);
            String finalMsg = stripPrefix(res);
            boolean ok = res.startsWith("OK:");
            if (!ok && m == 0 && allowOpenManager) {
                String mgr = PrivilegedHelper.openRootManager(MainActivity.this);
                if (mgr != null) {
                    finalMsg = "Opened " + mgr + " — allow Root";
                }
            }
            final String msgOut = finalMsg;
            runOnUiThread(() -> {
                methodState = ok ? 2 : 3;
                methodStatus = msgOut;
                if (!onboarded || currentTab == 0 || currentTab == 2) refreshCurrentTab();
            });
        });
    }

    private void refreshLocalSummaryText() {
        boolean hasStorage = LocalServerManager.hasStorageAccess();
        boolean hasDb = hasStorage && new File(LocalServerManager.assetsDbPath()).length() > 0;
        if (!hasStorage) {
            localStatusLine = tr("stor_needed_sub");
        } else {
            localStatusLine = tr(hasDb ? "assets_ok" : "assets_needed")
                + " · " + tr(compilerReady ? "rust_ready" : "auto_rust");
        }
    }

    private void grantStorageAsync() {
        bgExec.execute(() -> {
            boolean ok = LocalServerManager.ensureStorageAccess(MainActivity.this, method);
            if (ok) LocalServerManager.ensureDirs(MainActivity.this);
            final boolean restored = ok && syncSavedConnsExternalOnBg();
            runOnUiThread(() -> {
                refreshLocalSummaryText();
                if (restored && currentTab == 0) detectActiveTarget();
                if (currentTab == 1 && serverMode == 1) reloadPlayersAsync(false);
                refreshCurrentTab();
            });
        });
    }

    private void reloadPlayersAsync(boolean showMsg) {
        if (playersLoading) return;
        playersLoading = true;
        bgExec.execute(() -> {
            if (!LocalServerManager.hasStorageAccess()) {
                LocalServerManager.ensureStorageAccess(MainActivity.this, method);
            }
            LocalServerManager.ensureDirs(MainActivity.this);
            String raw = RustCore.playerDbList(LocalServerManager.gamesvDbPath());
            List<PlayerRow> parsedRows = new ArrayList<>();
            long bytes = 0;
            String err = "";
            try {
                JSONObject j = new JSONObject(raw);
                if (j.optBoolean("ok", false)) {
                    bytes = j.optLong("size", 0);
                    JSONArray arr = j.optJSONArray("players");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            PlayerRow r = new PlayerRow();
                            r.playerId = o.optLong("playerId", 0);
                            r.name = o.optString("name", "Rover");
                            r.level = o.optLong("level", 1);
                            r.exp = o.optLong("exp", 0);
                            r.coin = o.optLong("coin", 0);
                            r.rareCoin = o.optLong("rareCoin", 0);
                            r.cashCoin = o.optLong("cashCoin", 0);
                            r.sex = o.optLong("sex", 1);
                            r.worldLevel = o.optLong("worldLevel", 1);
                            parsedRows.add(r);
                        }
                    }
                } else {
                    err = "ERR:" + j.optString("error", "Read failed");
                }
            } catch (Throwable t) {
                err = "ERR:" + t.getMessage();
            }
            List<File> backups = LocalServerManager.listBackups();
            final long fBytes = bytes;
            final String fMsg = !err.isEmpty() ? err : (showMsg ? "Loaded " + parsedRows.size() + " players" : "");
            runOnUiThread(() -> {
                playersLoadedOnce = true;
                playersLoading = false;
                playerList.clear();
                playerList.addAll(parsedRows);
                dbBackups.clear();
                dbBackups.addAll(backups);
                playerDbBytes = fBytes;
                playerDbMsg = fMsg;
                refreshCurrentTab();
            });
        });
    }

    private void openPlayerEditor(PlayerRow row) {
        editingOpen = true;
        editingIsNew = (row == null);
        if (row == null) {
            editUid = "";
            editName = "Rover";
            editLevel = "1";
            editExp = "0";
            editCoin = "100000";
            editRareCoin = "16000";
            editCashCoin = "1600";
            editSex = 1;
            editWorldLevel = "1";
        } else {
            editUid = String.valueOf(row.playerId);
            editName = row.name;
            editLevel = String.valueOf(row.level);
            editExp = String.valueOf(row.exp);
            editCoin = String.valueOf(row.coin);
            editRareCoin = String.valueOf(row.rareCoin);
            editCashCoin = String.valueOf(row.cashCoin);
            editSex = row.sex == 2 ? 2 : 1;
            editWorldLevel = String.valueOf(row.worldLevel);
        }
        refreshCurrentTab();
    }

    private void saveEditingPlayer() {
        bgExec.execute(() -> {
            try {
                LocalServerManager.ensureDirs(MainActivity.this);
                JSONObject o = new JSONObject();
                o.put("playerId", parseLongSafe(editUid, 0));
                o.put("name", editName.trim().isEmpty() ? "Rover" : editName.trim());
                o.put("level", parseLongSafe(editLevel, 1));
                o.put("exp", parseLongSafe(editExp, 0));
                o.put("coin", parseLongSafe(editCoin, 0));
                o.put("rareCoin", parseLongSafe(editRareCoin, 0));
                o.put("cashCoin", parseLongSafe(editCashCoin, 0));
                o.put("sex", editSex);
                o.put("worldLevel", parseLongSafe(editWorldLevel, 1));
                String res = RustCore.playerDbUpsert(LocalServerManager.gamesvDbPath(), o.toString());
                if (res.startsWith("OK:")) {
                    editingOpen = false;
                    playerDbMsg = "Saved UID " + res.substring(3);
                    reloadPlayersAsync(false);
                } else {
                    playerDbMsg = res;
                    runOnUiThread(this::refreshCurrentTab);
                }
            } catch (Throwable t) {
                playerDbMsg = "ERR:" + t.getMessage();
                runOnUiThread(this::refreshCurrentTab);
            }
        });
    }

    private void deleteEditingPlayer() {
        final long pid = parseLongSafe(editUid, 0);
        if (pid <= 0) return;
        bgExec.execute(() -> {
            String res = RustCore.playerDbDelete(LocalServerManager.gamesvDbPath(), pid);
            editingOpen = false;
            playerDbMsg = "OK".equals(res) ? "Deleted UID " + pid : res;
            reloadPlayersAsync(false);
        });
    }

    private void backupDbAsync() {
        bgExec.execute(() -> {
            LocalServerManager.ensureDirs(MainActivity.this);
            String res = LocalServerManager.backupDatabase();
            playerDbMsg = "OK".equals(res) ? "Backup saved to /0/sonoro/backups" : res;
            reloadPlayersAsync(false);
        });
    }

    private void restoreDbAsync(File src) {
        bgExec.execute(() -> {
            String res = RustCore.playerDbCopy(src.getAbsolutePath(), LocalServerManager.gamesvDbPath());
            playerDbMsg = "OK".equals(res) ? "Restored " + src.getName() : res;
            reloadPlayersAsync(false);
        });
    }

    private void syncAssetsAsync() {
        LocalServerManager.resetCancel();
        busy = true;
        refreshCurrentTab();
        SonoroBuildService.start(this, "Sonoro", "Syncing server files & assets.db...");
        new Thread(() -> {
            try {
                LocalServerManager.ensureStorageAccess(MainActivity.this, method);
                LocalServerManager.ensureDirs(MainActivity.this);
                JSONObject manifest = LocalServerManager.fetchManifest();
                LocalServerManager.syncServerProject(MainActivity.this, manifest, this::appendLog);
                LocalServerManager.ensureAssetsDb(manifest, this::appendLog);
                final String[] vers = LocalServerManager.probeServerVersions(method);
                runOnUiThread(() -> {
                    githubServerVer = vers[0].isEmpty() ? githubServerVer : vers[0];
                    githubAssetsVer = vers[1];
                    localBuiltVer = vers[3];
                });
            } catch (Throwable t) {
                appendLog("Sync error: " + t.getMessage());
            } finally {
                SonoroBuildService.stop(MainActivity.this);
                runOnUiThread(() -> {
                    busy = false;
                    refreshLocalSummaryText();
                    refreshCurrentTab();
                });
            }
        }, "sonoro-sync").start();
    }

    private void promptCleanRustCache() {
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(tr("clean_rust"))
            .setMessage(tr("clean_rust_sub") + "\n\nCache: " + rustCacheSizeText)
            .setPositiveButton(tr("clean_rust_only"), (d, w) -> runCleanRustAsync(false))
            .setNeutralButton(tr("clean_all_srv"), (d, w) -> runCleanRustAsync(true))
            .setNegativeButton(tr("cancel"), null)
            .show();
    }

    private void runCleanRustAsync(final boolean includeAll) {
        busy = true;
        rustCacheSizeText = tr("cleaning");
        refreshCurrentTab();
        new Thread(() -> {
            LocalServerManager.cleanRustAndBuildCache(MainActivity.this, method, includeAll, this::appendLog);
            compilerReady = LocalServerManager.checkToolchainReady(method) != null;
            final String newSz = LocalServerManager.measureRustCacheSize(MainActivity.this, method);
            final String[] vers = LocalServerManager.probeServerVersions(method);
            runOnUiThread(() -> {
                rustCacheSizeText = newSz;
                localBuiltVer = vers[3];
                busy = false;
                refreshLocalSummaryText();
                refreshCurrentTab();
            });
        }, "sonoro-clean-rust").start();
    }

    private void buildServerManualAsync() {
        runServerUpdateOrBuild(compilerReady);
    }

    private void updateServerNowAsync() {
        runServerUpdateOrBuild(false);
    }

    private void runServerUpdateOrBuild(final boolean forceRebuild) {
        LocalServerManager.resetCancel();
        busy = true;
        refreshCurrentTab();
        SonoroBuildService.start(this, "Sonoro", "Syncing & building local server...");
        new Thread(() -> {
            try {
                LocalServerManager.ensureStorageAccess(MainActivity.this, method);
                LocalServerManager.ensureDirs(MainActivity.this);
                JSONObject manifest = LocalServerManager.fetchManifest();
                boolean changed = LocalServerManager.syncServerProject(MainActivity.this, manifest, this::appendLog);
                LocalServerManager.ensureAssetsDb(manifest, this::appendLog);
                boolean ok = LocalServerManager.buildLocalServer(MainActivity.this, method, changed || forceRebuild, this::appendLog);
                compilerReady = LocalServerManager.checkToolchainReady(method) != null;
                final String[] vers = LocalServerManager.probeServerVersions(method);
                runOnUiThread(() -> {
                    githubServerVer = vers[0].isEmpty() ? githubServerVer : vers[0];
                    githubAssetsVer = vers[1];
                    localBuiltVer = vers[3];
                });
                appendLog(ok ? "Local server updated" : "Build failed");
            } catch (Throwable t) {
                appendLog("Build error: " + t.getMessage());
            } finally {
                SonoroBuildService.stop(MainActivity.this);
                runOnUiThread(() -> {
                    busy = false;
                    refreshLocalSummaryText();
                    refreshCurrentTab();
                });
            }
        }, "sonoro-build").start();
    }

    private static long parseLongSafe(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private EditText numInput(String initial, String hint, java.util.function.Consumer<String> onChange) {
        EditText e = darkInput(initial, true, hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.addTextChangedListener(simpleWatcher(onChange));
        return e;
    }

    private TextWatcher simpleWatcher(java.util.function.Consumer<String> onChange) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                onChange.accept(s.toString());
            }
        };
    }

    private TextView padTopLabel(String txt, int topDp) {
        TextView t = text(txt, 12, TEXT2);
        t.setPadding(0, px(topDp), 0, 0);
        return t;
    }

    private void addConnection(String raw) {
        final String trimmed = raw != null ? raw.trim() : "";
        if (trimmed.isEmpty()) {
            connMsg = tr("paste_first");
            refreshCurrentTab();
            return;
        }
        connMsg = tr("detecting");
        refreshCurrentTab();

        bgExec.execute(() -> {
            String effective = trimmed;
            JSONObject initial = parseViaRust(trimmed);
            if (initial.optBoolean("subscription", false)) {
                String[] http = PrivilegedHelper.httpGet(trimmed);
                if (!"OK".equals(http[0])) {
                    runOnUiThread(() -> {
                        connMsg = http[1];
                        refreshCurrentTab();
                    });
                    return;
                }
                effective = http[1];
            }

            JSONObject parsed = parseViaRust(effective);
            if (!parsed.optBoolean("ok", false)) {
                String err = parsed.optString("error", tr("invalid_conn"));
                runOnUiThread(() -> {
                    connMsg = err;
                    refreshCurrentTab();
                });
                return;
            }

            String badge = parsed.optString("badge", "Proxy");
            String name = parsed.optString("displayName", effective);
            if (name.isEmpty()) name = effective;
            final String finalTarget = effective.trim();
            final String finalName = name;
            final String finalBadge = badge;

            runOnUiThread(() -> {
                int existing = -1;
                for (int i = 0; i < savedConns.size(); i++) {
                    if (savedConns.get(i).target.trim().equals(finalTarget)) {
                        existing = i;
                        break;
                    }
                }
                SavedConn sc = new SavedConn(finalName, finalBadge, finalTarget);
                if (existing >= 0) savedConns.set(existing, sc);
                else savedConns.add(0, sc);
                persistSavedConns();

                activeTarget = finalTarget;
                detectActiveTarget();
                draftInput = "";
                draftBadge = "";
                connMsg = tr("added");
                refreshCurrentTab();
            });
        });
    }

    private void checkOwner() {
        bgExec.execute(() -> {
            String chk = (method == 0) ? PrivilegedHelper.checkRoot() : PrivilegedHelper.checkShizuku(true);
            runOnUiThread(() -> {
                methodState = chk.startsWith("OK:") ? 2 : 3;
                methodStatus = stripPrefix(chk);
            });
            if (!chk.startsWith("OK:")) {
                String err = stripPrefix(chk);
                runOnUiThread(() -> {
                    ownerStatus = err;
                    refreshCurrentTab();
                });
                return;
            }

            int uid = resolveAppUid(method);
            if (uid <= 0) {
                runOnUiThread(() -> {
                    ownerStatus = "Game not installed";
                    refreshCurrentTab();
                });
                return;
            }
            try {
                IniState st = readIniState(method);
                String msg = !st.exists ? "UID " + uid + " · missing"
                    : (st.owner == uid ? "UID " + uid + " · matched"
                    : "UID " + uid + " · owner " + st.owner + " (mismatch)");
                appendLog(msg);
                runOnUiThread(() -> {
                    appUidText = String.valueOf(uid);
                    ownerStatus = msg;
                    refreshCurrentTab();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ownerStatus = e.getMessage();
                    refreshCurrentTab();
                });
            }
        });
    }

    private void stopFlow() {
        flowCancelRequested = true;
        final Thread running = activeFlowThread;
        final int useMethod = method;
        appendLog("Stopping processes...");
        if (running != null) {
            try {
                running.interrupt();
            } catch (Throwable ignored) {
            }
        }
        statusState = 0;
        statusText = tr("ready");
        busy = false;
        for (int i = 0; i < 4; i++) stepStates[i] = 0;
        updateHomeStatusUi(true);

        new Thread(() -> {
            SonoroBuildService.stop(MainActivity.this);
            LocalServerManager.requestCancel(useMethod);
            try {
                startService(new Intent(this, SonoroVpnService.class).setAction(SonoroVpnService.ACTION_STOP));
            } catch (Throwable ignored) {
            }
            boolean useRoot = (useMethod == 0);
            String coreRes = PrivilegedHelper.stopCore(this, useRoot);
            LocalServerManager.stopLocalServer(useMethod);
            execPriv(useMethod, "settings put global http_proxy ':0'");
            appendLog("Stopped · " + stripPrefix(coreRes));
            runOnUiThread(() -> {
                statusState = 0;
                statusText = tr("ready");
                busy = false;
                for (int i = 0; i < 4; i++) stepStates[i] = 0;
                updateHomeStatusUi(false);
            });
        }, "sonoro-force-stop").start();
    }

    private void startFlow() {
        final int useMode = serverMode;
        final String rawInput = activeTarget != null ? activeTarget.trim() : "";
        if (useMode == 0 && rawInput.isEmpty()) {
            logBuf.setLength(0);
            logBuf.append("No connection set\n");
            for (int i = 0; i < 4; i++) stepStates[i] = (i == 0 ? 3 : 0);
            statusState = 3;
            statusText = tr("failed");
            busy = false;
            updateHomeStatusUi(true);
            return;
        }
        Intent vpnPrep = VpnService.prepare(this);
        if (vpnPrep != null) {
            pendingStart = true;
            startActivityForResult(vpnPrep, REQ_VPN);
            return;
        }

        final int useMethod = method;
        flowCancelRequested = false;
        LocalServerManager.resetCancel();
        busy = true;
        logBuf.setLength(0);
        for (int i = 0; i < 4; i++) stepStates[i] = 0;
        statusState = 1;
        statusText = tr("working");
        updateHomeStatusUi(true);

        if (useMode == 1) {
            new Thread(() -> {
                activeFlowThread = Thread.currentThread();
                try {
                    runLocalFlow(useMethod);
                } finally {
                    if (activeFlowThread == Thread.currentThread()) activeFlowThread = null;
                }
            }, "sonoro-local-flow").start();
            return;
        }

        new Thread(() -> {
            activeFlowThread = Thread.currentThread();
            try {
                // Parse target and check reachable TCP endpoints (Hysteria/TUIC/WG use UDP/QUIC)
                setStep(0, 1);
                String effective = rawInput;
                JSONObject firstCheck = parseViaRust(effective);
                if (firstCheck.optBoolean("subscription", false)) {
                    appendLog("Fetching subscription...");
                    String[] res = PrivilegedHelper.httpGet(effective);
                    if (flowCancelRequested) return;
                    if (!"OK".equals(res[0])) {
                        failStep(0, res[1]);
                        return;
                    }
                    effective = res[1];
                }
                if (flowCancelRequested) return;

                JSONObject parsed = parseViaRust(effective);
                if (!parsed.optBoolean("ok", false)) {
                    failStep(0, parsed.optString("error", "Invalid connection"));
                    return;
                }
                String summary = parsed.optString("summary", "");
                String badge = parsed.optString("badge", "");
                String host = parsed.optString("host", "");
                int port = parsed.optInt("port", 0);
                boolean primaryTcp = parsed.optBoolean("primaryTcp", false);

                appendLog("Detected: " + summary);
                runOnUiThread(() -> {
                    parseBadge = badge;
                    parseSummary = summary;
                    parseOk = true;
                });

                if (primaryTcp && !host.isEmpty() && port > 0) {
                    appendLog("Checking TCP " + host + ":" + port + "...");
                    String tcp = RustCore.checkTcp(host, port);
                    if (flowCancelRequested) return;
                    if (!"OK".equals(tcp)) {
                        failStep(0, "Unreachable (" + host + ":" + port + "): " + stripPrefix(tcp));
                        return;
                    }
                    appendLog("TCP ok: " + host + ":" + port);
                    if ("HTTP".equals(badge)) {
                        String srvAlive = LocalServerManager.checkRemoteServerAlive(host);
                        if (flowCancelRequested) return;
                        if (srvAlive.startsWith("OK:")) appendLog(srvAlive.substring(3));
                    }
                } else if (!host.isEmpty() && port > 0) {
                    appendLog("UDP/QUIC endpoint: " + host + ":" + port);
                }
                setStep(0, 2);

                if (flowCancelRequested) return;
                setStep(1, 1);
                if (!ensurePrivAndPatchIni(useMethod, 1)) return;
                setStep(1, 2);

                if (flowCancelRequested) return;
                setStep(2, 1);
                String cfgRes = RustCore.buildConfig(effective, LOCAL_MIXED_PORT);
                if (!cfgRes.startsWith("OK:")) {
                    failStep(2, stripPrefix(cfgRes));
                    return;
                }
                if (!startSingboxAndVpn(useMethod, cfgRes.substring(3))) return;
                setStep(2, 2);

                if (flowCancelRequested) return;
                setStep(3, 1);
                launchGame(3);
            } catch (Throwable t) {
                if (flowCancelRequested) return;
                appendLog("Error: " + t.getMessage());
                runOnUiThread(() -> {
                    statusState = 3;
                    statusText = "Failed";
                    busy = false;
                    refreshCurrentTab();
                });
            } finally {
                if (activeFlowThread == Thread.currentThread()) activeFlowThread = null;
            }
        }, "sonoro-connect-flow").start();
    }

    private void runLocalFlow(int useMethod) {
        SonoroBuildService.start(this, "Sonoro Local Server", "Preparing downloads & compiler...");
        try {
            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            setStep(0, 1);
            String chk = (useMethod == 0) ? PrivilegedHelper.checkRoot() : PrivilegedHelper.checkShizuku(true);
            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            runOnUiThread(() -> {
                methodState = chk.startsWith("OK:") ? 2 : 3;
                methodStatus = stripPrefix(chk);
            });
            if (!chk.startsWith("OK:")) {
                failStep(0, stripPrefix(chk));
                return;
            }
            if (!LocalServerManager.ensureStorageAccess(MainActivity.this, useMethod)) {
                failStep(0, "Allow /0/sonoro All Files Access first");
                return;
            }
            LocalServerManager.ensureDirs(MainActivity.this);
            JSONObject manifest = LocalServerManager.fetchManifest();
            boolean srcChanged = LocalServerManager.syncServerProject(MainActivity.this, manifest, this::appendLog);
            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            if (!LocalServerManager.ensureAssetsDb(manifest, this::appendLog)) {
                if (flowCancelRequested || LocalServerManager.isCancelled()) return;
                failStep(0, "assets.db missing or failed download");
                return;
            }
            runOnUiThread(this::refreshLocalSummaryText);
            setStep(0, 2);

            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            setStep(1, 1);
            if (!LocalServerManager.buildLocalServer(MainActivity.this, useMethod, srcChanged, this::appendLog)) {
                if (flowCancelRequested || LocalServerManager.isCancelled()) return;
                failStep(1, "Local Rust compile failed (see Log)");
                return;
            }
            compilerReady = LocalServerManager.checkToolchainReady(useMethod) != null;
            final String[] vers = LocalServerManager.probeServerVersions(useMethod);
            runOnUiThread(() -> {
                githubServerVer = vers[0].isEmpty() ? githubServerVer : vers[0];
                githubAssetsVer = vers[1];
                localBuiltVer = vers[3];
                refreshLocalSummaryText();
            });
            setStep(1, 2);

            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            setStep(2, 1);
            String advIp = localCoop ? localAdvIp.trim() : "127.0.0.1";
            if (advIp.isEmpty()) advIp = LocalServerManager.detectLanIp();
            String srvRes = LocalServerManager.startLocalServer(MainActivity.this, useMethod, localCoop, advIp, this::appendLog);
            if (flowCancelRequested || LocalServerManager.isCancelled()) {
                LocalServerManager.stopLocalServer(useMethod);
                return;
            }
            if (!srvRes.startsWith("OK:")) {
                failStep(2, stripPrefix(srvRes));
                return;
            }
            String cfgRes = RustCore.buildLocalVpnConfig(LOCAL_MIXED_PORT, 8080);
            if (!cfgRes.startsWith("OK:")) {
                LocalServerManager.stopLocalServer(useMethod);
                failStep(2, stripPrefix(cfgRes));
                return;
            }
            if (!startSingboxAndVpn(useMethod, cfgRes.substring(3))) {
                LocalServerManager.stopLocalServer(useMethod);
                return;
            }
            setStep(2, 2);

            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            setStep(3, 1);
            if (!ensurePrivAndPatchIni(useMethod, 3)) return;
            launchGame(3);
        } catch (Throwable t) {
            if (flowCancelRequested || LocalServerManager.isCancelled()) return;
            appendLog("Local flow error: " + t.getMessage());
            runOnUiThread(() -> {
                statusState = 3;
                statusText = "Failed";
                busy = false;
                refreshCurrentTab();
            });
        } finally {
            SonoroBuildService.stop(MainActivity.this);
        }
    }

    private boolean ensurePrivAndPatchIni(int useMethod, int stepIdx) throws Exception {
        String chk = (useMethod == 0) ? PrivilegedHelper.checkRoot() : PrivilegedHelper.checkShizuku(true);
        runOnUiThread(() -> {
            methodState = chk.startsWith("OK:") ? 2 : 3;
            methodStatus = stripPrefix(chk);
        });
        if (!chk.startsWith("OK:")) {
            failStep(stepIdx, stripPrefix(chk));
            return false;
        }
        int appUid = resolveAppUid(useMethod);
        if (appUid <= 0) {
            failStep(stepIdx, "Game not installed: " + PKG);
            return false;
        }
        runOnUiThread(() -> appUidText = String.valueOf(appUid));
        appendLog("Game UID: " + appUid);

        IniState ini = readIniState(useMethod);
        JSONObject patchOut = new JSONObject(RustCore.patchIni(ini.content, ini.exists));
        boolean dirty = patchOut.optBoolean("dirty", true);
        String newText = patchOut.optString("content", ini.content);
        JSONArray pLogs = patchOut.optJSONArray("logs");
        if (pLogs != null) {
            for (int i = 0; i < pLogs.length(); i++) appendLog(pLogs.getString(i));
        }

        boolean ownerMismatch = (ini.owner != appUid);
        if (dirty || ownerMismatch) {
            int verified = writeAndEnforceOwner(useMethod, ini.target, appUid, ini.gid, dirty ? newText : null);
            appendLog("Owner UID verified: " + verified);
            runOnUiThread(() -> ownerStatus = "UID " + verified);
        } else {
            appendLog("Owner UID verified: " + appUid);
            runOnUiThread(() -> ownerStatus = "UID " + appUid);
        }
        return true;
    }

    private boolean startSingboxAndVpn(int useMethod, String cfgJson) {
        execPriv(useMethod, "settings put global http_proxy ':0'");
        String coreRes = PrivilegedHelper.startCore(this, cfgJson, useMethod == 0);
        if (!coreRes.startsWith("OK:")) {
            failStep(2, stripPrefix(coreRes));
            return false;
        }
        String coreBody = stripPrefix(coreRes);
        for (String ln : coreBody.split("\n")) {
            String t = ln.trim();
            if (!t.isEmpty()) appendLog(t);
        }
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Intent vpn = new Intent(MainActivity.this, SonoroVpnService.class)
            .setAction(SonoroVpnService.ACTION_START)
            .putExtra(SonoroVpnService.EXTRA_PORT, LOCAL_MIXED_PORT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(vpn);
        } else {
            startService(vpn);
        }
        boolean vpnUp = false;
        for (int i = 0; i < 12; i++) {
            if (SonoroVpnService.isVpnActive()) {
                vpnUp = true;
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!vpnUp) {
            try {
                startService(new Intent(MainActivity.this, SonoroVpnService.class)
                    .setAction(SonoroVpnService.ACTION_STOP));
            } catch (Throwable ignored) {
            }
            PrivilegedHelper.stopCore(MainActivity.this, useMethod == 0);
            failStep(2, "VPN tunnel failed to start");
            return false;
        }
        appendLog("VPN + sing-box active (all apps)");
        return true;
    }

    private void launchGame(int stepIdx) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(PKG);
        if (launch == null) {
            failStep(stepIdx, "Package not installed: " + PKG);
            return;
        }
        SonoroBuildService.stop(this);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        appendLog("Launched " + PKG);
        setStep(stepIdx, 2);
        runOnUiThread(() -> {
            statusState = 2;
            statusText = tr("active");
            busy = false;
            updateHomeStatusUi(true);
        });
    }

    private void setStep(int i, int st) {
        runOnUiThread(() -> {
            stepStates[i] = st;
            if (currentTab == 0 && homeStepsSlot != null) {
                populateHomeStepsCard(homeStepsSlot);
            } else if (currentTab == 0) {
                refreshCurrentTab();
            }
        });
    }

    private void failStep(int i, String msg) {
        SonoroBuildService.stop(this);
        appendLog(msg);
        runOnUiThread(() -> {
            stepStates[i] = 3;
            statusState = 3;
            statusText = tr("failed");
            busy = false;
            updateHomeStatusUi(true);
        });
    }

    private String[] execPriv(int m, String script) {
        return m == 0 ? PrivilegedHelper.execRoot(script) : PrivilegedHelper.execShizuku(script);
    }

    private int resolveAppUid(int m) {
        int uid = PrivilegedHelper.getAppUid(this, PKG);
        if (uid > 0) return uid;
        String[] res = execPriv(m, "cmd package list packages -U " + PKG + " 2>/dev/null || pm list packages -U " + PKG);
        for (String line : res[1].split("\n")) {
            if (line.contains(PKG) && line.contains("uid:")) {
                String tail = line.substring(line.indexOf("uid:") + 4).trim();
                try {
                    return Integer.parseInt(tail);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private static final class IniState {
        String target;
        int gid;
        boolean exists;
        int owner;
        String content;
    }

    private IniState readIniState(int m) throws Exception {
        String script = "PKG='" + PKG + "'\n"
            + "REL='" + INI_REL + "'\n"
            + "EXT=\"/storage/emulated/0/Android/data/$PKG/$REL\"\n"
            + "DATA=\"/data/media/0/Android/data/$PKG/$REL\"\n"
            + "if [ \"$(id -u)\" -eq 0 ] && [ -d \"/data/media/0/Android/data\" ]; then\n"
            + "  TARGET=\"$DATA\"\n"
            + "  BASE=\"/data/media/0/Android/data/$PKG\"\n"
            + "else\n"
            + "  TARGET=\"$EXT\"\n"
            + "  BASE=\"/storage/emulated/0/Android/data/$PKG\"\n"
            + "fi\n"
            + "printf 'TARGET=%s\\n' \"$TARGET\"\n"
            + "if [ -d \"$BASE\" ]; then GID=$(stat -c '%g' \"$BASE\" 2>/dev/null); else GID=$(stat -c '%g' \"$(dirname \"$BASE\")\" 2>/dev/null); fi\n"
            + "printf 'GID=%s\\n' \"$GID\"\n"
            + "if [ -f \"$TARGET\" ]; then\n"
            + "  OWNER=$(stat -c '%u' \"$TARGET\" 2>/dev/null)\n"
            + "  printf 'EXISTS=1\\nOWNER=%s\\nB64_START\\n' \"$OWNER\"\n"
            + "  base64 \"$TARGET\"\n"
            + "  printf '\\nB64_END\\n'\n"
            + "else\n"
            + "  printf 'EXISTS=0\\n'\n"
            + "fi\n";
        String[] res = execPriv(m, script);
        if (!"0".equals(res[0].trim())) {
            throw new IllegalStateException(res[2].trim().isEmpty() ? res[1].trim() : res[2].trim());
        }
        IniState st = new IniState();
        st.target = kv(res[1], "TARGET");
        st.gid = parseIntOr(kv(res[1], "GID"), -1);
        st.exists = "1".equals(kv(res[1], "EXISTS"));
        st.owner = parseIntOr(kv(res[1], "OWNER"), -1);
        if (st.exists) {
            int s = res[1].indexOf("B64_START\n");
            int e = res[1].indexOf("B64_END");
            if (s < 0 || e < 0) throw new IllegalStateException("Malformed base64 Engine.ini response");
            String b64 = res[1].substring(s + 10, e).replaceAll("\\s+", "");
            st.content = new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
        } else {
            st.content = "";
        }
        return st;
    }

    private int writeAndEnforceOwner(int m, String target, int appUid, int dirGid, String content) throws Exception {
        int useGid = dirGid > 0 ? dirGid : appUid;
        String base = target.startsWith("/data/media/0/")
            ? "/data/media/0/Android/data/" + PKG
            : "/storage/emulated/0/Android/data/" + PKG;
        String writeBlock = "";
        if (content != null) {
            String b64 = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            writeBlock = "mkdir -p \"$(dirname \"$TARGET\")\" || exit 1\n"
                + "P=\"$BASE\"\n"
                + "if [ -d \"$P\" ] && [ \"$(stat -c '%u' \"$P\" 2>/dev/null)\" != \"$APP_UID\" ]; then\n"
                + "  chown \"$APP_UID:$USE_GID\" \"$P\" 2>/dev/null || chown \"$APP_UID\" \"$P\" 2>/dev/null || true\n"
                + "fi\n"
                + "for SEG in files UE4Game Client Client Saved Config Android; do\n"
                + "  P=\"$P/$SEG\"\n"
                + "  if [ -d \"$P\" ] && [ \"$(stat -c '%u' \"$P\" 2>/dev/null)\" != \"$APP_UID\" ]; then\n"
                + "    chown \"$APP_UID:$USE_GID\" \"$P\" 2>/dev/null || chown \"$APP_UID\" \"$P\" 2>/dev/null || true\n"
                + "    chmod 770 \"$P\" 2>/dev/null || true\n"
                + "  fi\n"
                + "done\n"
                + "printf '%s' '" + b64 + "' | base64 -d > \"$TARGET\" || exit 1\n";
        }
        String script = "TARGET='" + target + "'\n"
            + "BASE='" + base + "'\n"
            + "APP_UID='" + appUid + "'\n"
            + "USE_GID='" + useGid + "'\n"
            + writeBlock
            + "CUR_OWNER=$(stat -c '%u' \"$TARGET\" 2>/dev/null)\n"
            + "if [ \"$CUR_OWNER\" != \"$APP_UID\" ]; then\n"
            + "  chown \"$APP_UID:$USE_GID\" \"$TARGET\" 2>/dev/null || chown \"$APP_UID\" \"$TARGET\" 2>/dev/null || true\n"
            + "fi\n"
            + "chmod 660 \"$TARGET\" 2>/dev/null || true\n"
            + "printf 'FINAL_OWNER=%s\\n' \"$(stat -c '%u' \"$TARGET\" 2>/dev/null)\"\n";
        String[] res = execPriv(m, script);
        if (!"0".equals(res[0].trim())) {
            throw new IllegalStateException(res[2].trim().isEmpty() ? res[1].trim() : res[2].trim());
        }
        int owner = parseIntOr(kv(res[1], "FINAL_OWNER"), -1);
        if (owner != appUid) {
            throw new IllegalStateException("Owner UID mismatch: " + owner + " != " + appUid + ". Switch to Root if Shizuku cannot chown.");
        }
        return owner;
    }

    private static String kv(String body, String key) {
        String p = key + "=";
        for (String l : body.split("\n")) {
            String t = l.trim();
            if (t.startsWith(p)) return t.substring(p.length()).trim();
        }
        return "";
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- UI PRIMITIVES
    private int px(float d) {
        return Math.round(d * dp);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 13, BLUE_SOFT);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        t.setPadding(px(6), 0, px(6), px(6));
        return t;
    }

    private LinearLayout borderedCard() {
        LinearLayout l = column();
        l.setBackground(glassBg(CARD, CARD_RIM_TOP, CARD_RIM_BOT, 24));
        l.setClipToOutline(true);
        return l;
    }

    private LinearLayout.LayoutParams cardLp(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(topDp);
        lp.bottomMargin = px(bottomDp);
        return lp;
    }

    private Drawable glassBg(int fill, int rimTop, int rimBot, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radiusDp));
        g.setStroke(Math.max(1, px(1)), rimTop);
        return g;
    }

    private GradientDrawable cardBg(int fill, int strokeCol, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radiusDp));
        if (strokeCol != 0) g.setStroke(Math.max(1, px(1)), strokeCol);
        return g;
    }

    private Drawable rowBg(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(PRESSED), new ColorDrawable(fill), new ColorDrawable(Color.WHITE));
    }

    private LinearLayout factCol(String k, String v, int vc) {
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView kt = text(k, 11, TEXT2);
        kt.setGravity(Gravity.CENTER);
        TextView vt = text(v, 13, vc);
        vt.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        vt.setGravity(Gravity.CENTER);
        vt.setSingleLine(true);
        vt.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(kt);
        c.addView(vt);
        return c;
    }

    private View vDiv() {
        View v = new View(this);
        v.setBackgroundColor(OUTLINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, px(1)), px(32)));
        return v;
    }

    private View hDiv(int insetDp) {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, px(1)));
        lp.leftMargin = px(insetDp);
        lp.rightMargin = px(insetDp);
        v.setLayoutParams(lp);
        return v;
    }

    private FrameLayout iconPlate(int iconKind, int bgCol, int fgCol) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(cardBg(bgCol, 0, 14));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icons(iconKind, fgCol));
        f.addView(iv, new FrameLayout.LayoutParams(px(22), px(22), Gravity.CENTER));
        return f;
    }

    private TextView actionPill(String label, int style, boolean enabled, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setTextSize(14);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        b.setGravity(Gravity.CENTER);
        styleActionPill(b, label, style, enabled);
        b.setOnClickListener(click);
        return b;
    }

    private void styleActionPill(TextView b, String label, int style, boolean enabled) {
        b.setText(label);
        int bg = !enabled ? 0xB8181A1F : (style == 0 ? BLUE_BTN : (style == 1 ? STOP_BG : 0xBD161C26));
        int fg = !enabled ? 0xFF5C626E : (style == 0 ? Color.WHITE : (style == 1 ? STOP_FG : TEXT));
        int rimTop = !enabled ? 0x1AFFFFFF : (style == 0 ? 0x7093C5FD : (style == 1 ? 0x80FFFFFF : CARD_RIM_TOP));
        int rimBot = !enabled ? 0x0AFFFFFF : (style == 0 ? 0x2693C5FD : (style == 1 ? 0x30FFFFFF : CARD_RIM_BOT));
        b.setTextColor(fg);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(PRESSED), glassBg(bg, rimTop, rimBot, 23), null));
        b.setEnabled(enabled);
    }

    private TextView miniPill(String label, boolean accent, boolean enabled, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(14), px(6), px(14), px(6));
        int bg = !enabled ? 0xB814171D : (accent ? PLATE : 0xBD151A24);
        int fg = !enabled ? TEXT3 : (accent ? PLATE_FG : TEXT);
        int rimTop = !enabled ? 0x14FFFFFF : (accent ? 0x5E9AB8F5 : CARD_RIM_TOP);
        int rimBot = !enabled ? 0x08FFFFFF : (accent ? 0x229AB8F5 : CARD_RIM_BOT);
        b.setTextColor(fg);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(PRESSED), glassBg(bg, rimTop, rimBot, 17), null));
        b.setEnabled(enabled);
        b.setOnClickListener(click);
        return b;
    }

    private EditText darkInput(String initial, boolean singleLine, String hint) {
        EditText e = new EditText(this);
        e.setText(initial);
        e.setHint(hint);
        e.setHintTextColor(TEXT3);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setSingleLine(singleLine);
        if (!singleLine) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        e.setPadding(px(14), px(10), px(14), px(10));
        e.setBackground(cardBg(0xFF0D1016, OUTLINE, 14));
        return e;
    }

    private View stepRow(String name, int st, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(6), 0, px(6));

        View dot = new View(this);
        int dBg = st == 2 ? SWITCH_ON : (st == 3 ? ERR_FG : BG);
        int dBrd = st >= 2 ? 0 : (st == 1 ? BLUE_BTN : OUTLINE);
        dot.setBackground(cardBg(dBg, dBrd, 7));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(13), px(13));
        dlp.rightMargin = px(12);
        row.addView(dot, dlp);

        TextView n = text(name, 13.5f, st == 0 ? TEXT2 : (st == 3 ? ERR_FG : TEXT));
        if (st == 1) n.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));

        if (st > 0) {
            TextView badge = text(tr(st == 1 ? "step_running" : (st == 2 ? "step_done" : "step_failed")), 12,
                st == 3 ? ERR_FG : (st == 2 ? OK_GREEN : BLUE_SOFT));
            badge.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
            row.addView(badge);
        }
        return row;
    }

    private View listRow(String k, String v, String note, int noteCol, String action,
                         boolean accent, boolean enabled, boolean last, View.OnClickListener click) {
        LinearLayout wrap = column();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(18), px(13), px(16), px(13));

        LinearLayout left = column();
        TextView kt = text(k, 14, TEXT);
        kt.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        TextView vt = text(v, 13, TEXT2);
        left.addView(kt);
        left.addView(vt);
        if (note != null && !note.isEmpty()) {
            left.addView(text(note, 12, noteCol));
        }
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        if (action != null && !action.isEmpty()) {
            row.addView(miniPill(action, accent, enabled, click));
        }
        wrap.addView(row);
        if (!last) wrap.addView(hDiv(18));
        return wrap;
    }

    private View methodRow(int icon, String title, String note, int noteCol,
                           boolean selected, boolean last, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(16), px(13), px(16), px(13));
        row.setBackground(rowBg(selected ? 0xFF111824 : Color.TRANSPARENT));
        row.setOnClickListener(click);

        row.addView(iconPlate(icon, selected ? PLATE : 0xFF151922, selected ? PLATE_FG : TEXT2),
            new LinearLayout.LayoutParams(px(44), px(44)));

        LinearLayout mid = column();
        mid.setPadding(px(14), 0, px(10), 0);
        TextView t = text(title, 15, selected ? HERO_FG : TEXT);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, selected ? 600 : 500, false));
        mid.addView(t);
        mid.addView(text(note, 12, noteCol));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        View radio = new View(this);
        radio.setBackground(cardBg(selected ? SWITCH_ON : BG, selected ? 0 : TEXT3, 10));
        row.addView(radio, new LinearLayout.LayoutParams(px(18), px(18)));
        return row;
    }

    private void writeCrashLog(String title, Throwable t) {
        StringWriter sw = new StringWriter();
        sw.append(title).append('\n');
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        Log.e(TAG, full);
        for (File dir : new File[]{getFilesDir(), getExternalFilesDir(null)}) {
            if (dir == null) continue;
            try {
                File out = new File(dir, "crash.txt");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(full.getBytes(StandardCharsets.UTF_8));
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
