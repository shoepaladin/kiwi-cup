package app.kimitalk.widget;

/**
 * Decides where a widget tap should go, given the user's mode and what is
 * installed on the device.
 *
 * <p>This class is deliberately free of Android dependencies: it takes plain
 * values and returns a plain plan. All Android glue (PackageManager queries,
 * SharedPreferences, building Intents) lives in the activities, which keeps
 * the decision logic hermetically unit-testable on a desktop JVM.
 */
public final class TargetResolver {

    /** What a tap should do. */
    public enum Action {
        /** Launch the official Kimi app. */
        KIMI_APP,
        /** Open kimi.com in a browser. */
        WEB_CHAT,
        /** Both targets exist — ask the user which one they want. */
        ASK_USER,
        /** Nothing on the device can handle the request. */
        UNAVAILABLE
    }

    /** User-selectable routing preference. */
    public enum Mode {
        /** Prefer the Kimi app; fall back to the web. */
        AUTO,
        /** Kimi app first (falls back to web if missing). */
        APP,
        /** Browser first (falls back to the app if no browser). */
        WEB,
        /** Ask every time when both targets are available. */
        ASK;

        /** Parses a stored preference value; unknown or null maps to AUTO. */
        public static Mode fromString(String stored) {
            if (stored == null) {
                return AUTO;
            }
            switch (stored) {
                case "app": return APP;
                case "web": return WEB;
                case "ask": return ASK;
                default:  return AUTO;
            }
        }
    }

    /** An immutable decision: the action plus its payload. */
    public static final class Plan {
        public final Action action;
        /** Package name for KIMI_APP, URL for WEB_CHAT, null otherwise. */
        public final String payload;

        Plan(Action action, String payload) {
            this.action = action;
            this.payload = payload;
        }
    }

    /**
     * Resolution matrix. Modes only reorder preference — they never strand
     * the user: if the chosen target is missing, the other one is used, and
     * UNAVAILABLE is returned only when nothing can handle the tap.
     */
    public static Plan resolve(Mode mode, boolean kimiAppInstalled, boolean webBrowserAvailable) {
        switch (mode) {
            case APP:
                if (kimiAppInstalled) return app();
                if (webBrowserAvailable) return web();
                return unavailable();
            case WEB:
                if (webBrowserAvailable) return web();
                if (kimiAppInstalled) return app();
                return unavailable();
            case ASK:
                if (kimiAppInstalled && webBrowserAvailable) {
                    return new Plan(Action.ASK_USER, null);
                }
                if (kimiAppInstalled) return app();
                if (webBrowserAvailable) return web();
                return unavailable();
            case AUTO:
            default:
                if (kimiAppInstalled) return app();
                if (webBrowserAvailable) return web();
                return unavailable();
        }
    }

    private static Plan app() {
        return new Plan(Action.KIMI_APP, KimiTargets.KIMI_PACKAGE);
    }

    private static Plan web() {
        return new Plan(Action.WEB_CHAT, KimiTargets.KIMI_WEB_URL);
    }

    private static Plan unavailable() {
        return new Plan(Action.UNAVAILABLE, null);
    }

    private TargetResolver() {
        // static entry points only
    }
}
