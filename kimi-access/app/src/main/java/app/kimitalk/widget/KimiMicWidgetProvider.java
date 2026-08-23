package app.kimitalk.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 1x1 mic-only widget. Tap routing is identical to the split widget: the tap
 * lands in {@link LaunchActivity}, which honors the user's saved mode.
 */
public class KimiMicWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
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
            WidgetStyling.applyMaterialYou(context, views, R.id.widget_button);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
