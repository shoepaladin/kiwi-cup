package app.kimitalk.widget;

/**
 * Well-known identifiers for reaching Kimi.
 *
 * <p>Kept free of Android imports so the constants are usable in plain JVM
 * unit tests.
 */
public final class KimiTargets {

    /** Official Kimi Android app, published by Beijing Moonshot AI. */
    public static final String KIMI_PACKAGE = "com.moonshot.kimichat";

    /** Kimi on the web. Voice input is available after sign-in. */
    public static final String KIMI_WEB_URL = "https://www.kimi.com/";

    /**
     * Normalizes a user-entered project link. Users type "example.com/x"
     * as often as "https://example.com/x", and an ACTION_VIEW intent on a
     * scheme-less URI resolves to nothing — so a missing scheme gets
     * https:// prepended. Empty input falls back to the Kimi web app.
     */
    public static String normalizeUrl(String raw) {
        if (raw == null) {
            return KIMI_WEB_URL;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return KIMI_WEB_URL;
        }
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private KimiTargets() {
        // constants only
    }
}
