package id.local.wuwaconnect;

public final class RustCore {
    static {
        System.loadLibrary("wuwa_connect");
    }

    public static native String parseProfile(String raw);
    public static native String buildConfig(String raw, int mixedPort);
    public static native String buildLocalVpnConfig(int mixedPort, int proxyPort);
    public static native String patchIni(String existingContent, boolean existed);
    public static native String checkTcp(String host, int port);

    public static native String startLocalGateway(
        String hostOverride, int loginPort, int proxyPort, int gatewayPort,
        String stateDir, String proxyDir
    );
    public static native String stopLocalGateway();

    public static native String resolveDebPackages(String packagesIndex, String rootsCsv);
    public static native String extractDeb(String debPath, String destDir, String runtimePrefix);
    public static native String sha256File(String filePath);

    public static native String playerDbList(String dbPath);
    public static native String playerDbUpsert(String dbPath, String playerJson);
    public static native String playerDbDelete(String dbPath, long playerId);
    public static native String playerDbCopy(String srcPath, String dstPath);

    private RustCore() {}
}
