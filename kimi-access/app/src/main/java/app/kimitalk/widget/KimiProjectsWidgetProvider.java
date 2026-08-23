package app.kimitalk.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/**
 * The 2x1 widget: K on the left (opens Kimi via the saved mode), up to two
 * project pills on the right, and a narrow full-height settings strip on the
 * far right edge. Pill contents and all three theme colors come from
 * SharedPreferences, so the widget re-renders via
 * {@link #updateAllAppWidgets(Context)} whenever settings are saved.
 */
public class KimiProjectsWidgetProvider extends AppWidgetProvider {

    private static final int REQ_KIMI = 100;
    private static final int REQ_SETTINGS = 101;
    private static final int REQ_PILL_1 = 102;
    private static final int REQ_PILL_2 = 103;

    /** Below this rendered height (dp) pills use compact text. */
    private static final int COMPACT_HEIGHT_DP = 90;
    private static final float PILL_TEXT_SP = 14f;
    private static final float PILL_TEXT_COMPACT_SP = 11f;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs =
                context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String p1Name = prefs.getString(SettingsKeys.KEY_P1_NAME, "").trim();
        String p1Url = prefs.getString(SettingsKeys.KEY_P1_URL, "").trim();
        String p2Name = prefs.getString(SettingsKeys.KEY_P2_NAME, "").trim();
        String p2Url = prefs.getString(SettingsKeys.KEY_P2_URL, "").trim();
        WidgetStyling.Theme theme = WidgetStyling.resolveTheme(context);

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views =
                    new RemoteViews(context.getPackageName(), R.layout.widget_projects);

            views.setOnClickPendingIntent(R.id.widget_btn_kimi, activity(context, REQ_KIMI,
                    new Intent(context, LaunchActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            views.setOnClickPendingIntent(R.id.widget_btn_settings, activity(context, REQ_SETTINGS,
                    new Intent(context, SettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));

            boolean has1 = p1Name.length() > 0;
            boolean has2 = p2Name.length() > 0;

            if (!has1 && !has2) {
                // Empty state: one hint pill that leads straight to settings.
                // It stretches to fill the cell because the second pill is
                // GONE and weights redistribute.
                views.setTextViewText(R.id.pill_1,
                        context.getString(R.string.widget_empty_projects));
                setFrameVisible(views, R.id.pill_1_frame, true);
                setFrameVisible(views, R.id.pill_2_frame, false);
                views.setOnClickPendingIntent(R.id.pill_1_frame, activity(context, REQ_PILL_1,
                        new Intent(context, SettingsActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            } else {
                bindPill(views, R.id.pill_1_frame, R.id.pill_1, REQ_PILL_1,
                        context, has1, p1Name, p1Url);
                bindPill(views, R.id.pill_2_frame, R.id.pill_2, REQ_PILL_2,
                        context, has2, p2Name, p2Url);
            }

            // Progressive text size: taller widget, larger pill text.
            float textSp = pillTextSp(appWidgetManager, appWidgetId);
            views.setTextViewTextSize(R.id.pill_1, TypedValue.COMPLEX_UNIT_SP, textSp);
            views.setTextViewTextSize(R.id.pill_2, TypedValue.COMPLEX_UNIT_SP, textSp);

            applyTheme(views, theme);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /** Re-render when the launcher stretches/shrinks the widget. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        onUpdate(context, appWidgetManager, new int[]{appWidgetId});
    }

    private static float pillTextSp(AppWidgetManager mgr, int appWidgetId) {
        Bundle opts = mgr.getAppWidgetOptions(appWidgetId);
        int minHeight = opts != null
                ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) : 0;
        return (minHeight > 0 && minHeight < COMPACT_HEIGHT_DP)
                ? PILL_TEXT_COMPACT_SP : PILL_TEXT_SP;
    }

    private static void applyTheme(RemoteViews views, WidgetStyling.Theme theme) {
        WidgetStyling.applySurface(views, R.id.widget_bg_image, theme.bg);
        WidgetStyling.applyGlyph(views, R.id.widget_k_glyph, theme.fg);
        WidgetStyling.applyDivider(views, R.id.widget_divider, theme);
        WidgetStyling.applySettingsGlyph(views, R.id.widget_settings_glyph, theme);
        WidgetStyling.applyPill(views, R.id.pill_1_bg, R.id.pill_1, theme);
        WidgetStyling.applyPill(views, R.id.pill_2_bg, R.id.pill_2, theme);
    }

    private static void setFrameVisible(RemoteViews views, int frameId, boolean visible) {
        views.setViewVisibility(frameId, visible ? View.VISIBLE : View.GONE);
    }

    private static void bindPill(RemoteViews views, int frameId, int textId, int requestCode,
                                 Context context, boolean present, String name, String url) {
        if (!present) {
            setFrameVisible(views, frameId, false);
            return;
        }
        setFrameVisible(views, frameId, true);
        views.setTextViewText(textId, name);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(KimiTargets.normalizeUrl(url)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(frameId, activity(context, requestCode, open));
    }

    private static PendingIntent activity(Context context, int requestCode, Intent intent) {
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Called by SettingsActivity after saving so pills refresh immediately. */
    public static void updateAllAppWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(
                new ComponentName(context, KimiProjectsWidgetProvider.class));
        if (ids.length > 0) {
            Intent update = new Intent(context, KimiProjectsWidgetProvider.class);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(update);
        }
    }
}
