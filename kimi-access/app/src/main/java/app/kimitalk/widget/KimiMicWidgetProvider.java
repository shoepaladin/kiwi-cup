package app.kimitalk.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 1x1 mic-only widget. Tap routing is identical to the split widget: the tap
 * lands in {@link LaunchActivity}, which honors the user's saved mode.
 * Themed with the same foreground/background colors as the 2x1 widget.
 */
public class KimiMicWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetStyling.Theme theme = WidgetStyling.resolveTheme(context);
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_mic);

            Intent tap = new Intent(context, LaunchActivity.class);
            tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);
            WidgetStyling.applySurface(views, R.id.widget_bg_image, theme.bg);
            WidgetStyling.applyGlyph(views, R.id.widget_mic, theme.fg);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /** Called by SettingsActivity after saving so colors refresh immediately. */
    public static void updateAllAppWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(
                new ComponentName(context, KimiMicWidgetProvider.class));
        if (ids.length > 0) {
            Intent update = new Intent(context, KimiMicWidgetProvider.class);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(update);
        }
    }
}
