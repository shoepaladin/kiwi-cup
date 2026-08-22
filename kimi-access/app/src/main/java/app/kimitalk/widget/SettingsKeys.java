package app.kimitalk.widget;

/**
 * Keys for the SharedPreferences file storing the user's tap mode. Pure
 * constants, so tests can pin them without an Android runtime.
 */
public final class SettingsKeys {

    public static final String PREFS_NAME = "settings";
    public static final String KEY_MODE = "target_mode";
    /** Must parse to {@link TargetResolver.Mode#AUTO}. */
    public static final String DEFAULT_MODE = "auto";

    /** Project shortcut slots shown as pills on the 2x1 widget. */
    public static final String KEY_P1_NAME = "project1_name";
    public static final String KEY_P1_URL = "project1_url";
    public static final String KEY_P2_NAME = "project2_name";
    public static final String KEY_P2_URL = "project2_url";

    private SettingsKeys() {
        // constants only
    }
}
