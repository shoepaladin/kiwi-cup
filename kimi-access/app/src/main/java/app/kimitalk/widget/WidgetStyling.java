package app.kimitalk.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Shared widget cosmetics. Kept separate from the providers so both widget
 * variants get identical theming.
 *
 * Theme model — three user-selectable colors (see SettingsKeys):
 *   foreground  → K glyph, pill backgrounds, settings glyph
 *   background  → widget surface
 *   highlight   → pill text (auto-derived for contrast when unset)
 *
 * All tinting goes through ImageView.setColorFilter, which RemoteViews
 * supports on every API level we target; that is why every tinted surface
 * in the layouts is an ImageView carrying a shape drawable, never a
 * TextView background.
 */
final class WidgetStyling {

    private WidgetStyling() {
        // static helpers only
    }

    /** Resolved theme for one render pass. */
    static final class Theme {
        final int fg;
        final int bg;
        final int hl;
        final boolean custom;

        Theme(int fg, int bg, int hl, boolean custom) {
            this.fg = fg;
            this.bg = bg;
            this.hl = hl;
            this.custom = custom;
        }
    }

    /**
     * Resolve the effective theme from prefs. If the user has set ANY color,
     * their choices win everywhere and Material You stays out of the way.
     */
    static Theme resolveTheme(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        int fg = prefs.getInt(SettingsKeys.KEY_FG, SettingsKeys.COLOR_UNSET);
        int bg = prefs.getInt(SettingsKeys.KEY_BG, SettingsKeys.COLOR_UNSET);
        int hl = prefs.getInt(SettingsKeys.KEY_HL, SettingsKeys.COLOR_UNSET);

        boolean custom = fg != SettingsKeys.COLOR_UNSET
                || bg != SettingsKeys.COLOR_UNSET
                || hl != SettingsKeys.COLOR_UNSET;

        if (fg == SettingsKeys.COLOR_UNSET) fg = SettingsKeys.DEFAULT_FG;
        if (bg == SettingsKeys.COLOR_UNSET) {
            if (!custom && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Untouched widget: keep the original Material You behavior.
                bg = context.getColor(android.R.color.system_accent1_600);
            } else {
                bg = SettingsKeys.DEFAULT_BG;
            }
        }
        if (hl == SettingsKeys.COLOR_UNSET) hl = contrastOn(fg);
        return new Theme(fg, bg, hl, custom);
    }

    /**
     * Black or white — whichever reads better on {@code color} (WCAG
     * relative-luminance contrast). Guards against unreadable custom combos
     * like lime pills with white text.
     */
    static int contrastOn(int color) {
        return contrastRatio(color, Color.BLACK) >= contrastRatio(color, Color.WHITE)
                ? Color.BLACK : Color.WHITE;
    }

    private static double contrastRatio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * linear(Color.red(c) / 255.0)
                + 0.7152 * linear(Color.green(c) / 255.0)
                + 0.0722 * linear(Color.blue(c) / 255.0);
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * Divider tone derived from the background (small luminance shift toward
     * the foreground) so it stays visible on any user-picked surface instead
     * of being pinned to 15% cream.
     */
    static int dividerOn(int bg, int fg) {
        float alpha = 0.22f;
        int r = (int) (Color.red(fg) * alpha + Color.red(bg) * (1 - alpha));
        int g = (int) (Color.green(fg) * alpha + Color.green(bg) * (1 - alpha));
        int b = (int) (Color.blue(fg) * alpha + Color.blue(bg) * (1 - alpha));
        return Color.rgb(r, g, b);
    }

    // ---- RemoteViews appliers (ImageView.setColorFilter only) ----

    static void tint(RemoteViews views, int imageViewId, int color) {
        views.setInt(imageViewId, "setColorFilter", color);
    }

    static void applySurface(RemoteViews views, int bgImageViewId, int color) {
        tint(views, bgImageViewId, color);
    }

    static void applyGlyph(RemoteViews views, int kImageViewId, int fg) {
        tint(views, kImageViewId, fg);
    }

    static void applyPill(RemoteViews views, int pillBgImageViewId, int pillTextViewId,
                          Theme theme) {
        tint(views, pillBgImageViewId, theme.fg);
        views.setTextColor(pillTextViewId, theme.hl);
    }

    static void applyDivider(RemoteViews views, int dividerImageViewId, Theme theme) {
        tint(views, dividerImageViewId, dividerOn(theme.bg, theme.fg));
    }

    static void applySettingsGlyph(RemoteViews views, int settingsImageViewId, Theme theme) {
        tint(views, settingsImageViewId, contrastOn(theme.bg));
    }

    /**
     * Legacy entry point kept for reference — no longer called. Custom user
     * colors must not be overridden by Material You, so tinting now flows
     * through {@link #resolveTheme(Context)} exclusively.
     */
    @Deprecated
    static void applyMaterialYou(Context context, RemoteViews views, int backgroundViewId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int accent = context.getColor(android.R.color.system_accent1_600);
            views.setColorStateList(
                    backgroundViewId,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(accent));
        }
    }
}
