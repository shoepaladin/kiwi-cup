package app.kimitalk.widget;

/**
 * Release metadata shown in the About popup. Pure constants so tests can pin
 * them (including keeping the manifest versionName in sync).
 */
public final class VersionInfo {

    /** Must match android:versionName in AndroidManifest.xml — pinned by test. */
    public static final String VERSION_NAME = "0.2.0";

    /** Release date, ISO-8601. */
    public static final String VERSION_DATE = "2026-08-23";

    /** The project's home in the kiwi-cup repo. */
    public static final String REPO_URL = "https://github.com/shoepaladin/kiwi-cup/tree/main/kimi-access";

    private VersionInfo() {
        // constants only
    }
}
