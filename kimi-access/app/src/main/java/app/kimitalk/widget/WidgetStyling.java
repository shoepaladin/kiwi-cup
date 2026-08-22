package app.kimitalk.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Shared widget cosmetics. Kept separate from the providers so both widget
 * variants get identical theming.
 */
final class WidgetStyling {

    private WidgetStyling() {
        // static helpers only
    }

    /**
     * On Android 12+ (API 31), tint the widget background with the system's
     * Material You accent color so the widget matches the wallpaper palette.
     * The tint preserves the rounded/pill shape of the background drawable.
     * Older devices keep the flat warm-ink surface untouched.
     */
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
