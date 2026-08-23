package app.kimitalk.widget;

/**
 * Keys for the SharedPreferences file storing the user's settings. Pure
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

    /**
     * Widget theme colors, stored as ARGB ints.
     *
     * <ul>
     *   <li>{@link #KEY_FG} foreground: the K glyph and the pill background.
     *   <li>{@link #KEY_BG} background: the widget surface.
     *   <li>{@link #KEY_HL} highlight: the pill text.
     * </ul>
     *
     * {@link #COLOR_UNSET} means "no user choice": foreground falls back to
     * acid lime, background falls back to Material You (Android 12+) or warm
     * ink, and highlight is auto-derived for contrast against the foreground.
     */
    public static final String KEY_FG = "color_fg";
    public static final String KEY_BG = "color_bg";
    public static final String KEY_HL = "color_hl";
    public static final int COLOR_UNSET = Integer.MIN_VALUE;

    /** Default foreground: acid lime (Acid Wire mark). */
    public static final int DEFAULT_FG = 0xFFD4E932;
    /** Default surface when Material You is unavailable: warm ink. */
    public static final int DEFAULT_BG = 0xFF23201C;

    private SettingsKeys() {
        // constants only
    }
}
