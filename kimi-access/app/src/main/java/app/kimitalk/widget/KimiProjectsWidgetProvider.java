package app.kimitalk.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

/**
 * The 2x1 widget: mic on the left (opens Kimi via the saved mode), up to two
 * project pills on the right, and a three-dot affordance that opens settings.
 * Pill contents come from SharedPreferences, so the widget re-renders via
 * {@link #updateAllAppWidgets(Context)} whenever settings are saved.
 */
public class KimiProjectsWidgetProvider extends AppWidgetProvider {

    private static final int REQ_KIMI = 100;
    private static final int REQ_SETTINGS = 101;
    private static final int REQ_PILL_1 = 102;
    private static final int REQ_PILL_2 = 103;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs =
                context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String p1Name = prefs.getString(SettingsKeys.KEY_P1_NAME, "").trim();
        String p1Url = prefs.getString(SettingsKeys.KEY_P1_URL, "").trim();
        String p2Name = prefs.getString(SettingsKeys.KEY_P2_NAME, "").trim();
        String p2Url = prefs.getString(SettingsKeys.KEY_P2_URL, "").trim();

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
                views.setTextViewText(R.id.pill_1,
                        context.getString(R.string.widget_empty_projects));
                views.setViewVisibility(R.id.pill_1, View.VISIBLE);
                views.setViewVisibility(R.id.pill_2, View.GONE);
                views.setOnClickPendingIntent(R.id.pill_1, activity(context, REQ_PILL_1,
                        new Intent(context, SettingsActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            } else {
                bindPill(views, R.id.pill_1, REQ_PILL_1, context, has1, p1Name, p1Url);
                bindPill(views, R.id.pill_2, REQ_PILL_2, context, has2, p2Name, p2Url);
            }

            WidgetStyling.applyMaterialYou(context, views, R.id.widget_root);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private static void bindPill(RemoteViews views, int viewId, int requestCode,
                                 Context context, boolean present, String name, String url) {
        if (!present) {
            views.setViewVisibility(viewId, View.GONE);
            return;
        }
        views.setViewVisibility(viewId, View.VISIBLE);
        views.setTextViewText(viewId, name);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(KimiTargets.normalizeUrl(url)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(viewId, activity(context, requestCode, open));
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
