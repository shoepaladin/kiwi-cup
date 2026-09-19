package com.kiwicup.scheduledmessenger.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kiwicup.scheduledmessenger.R
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts the reminder notification with a deep link into the conversation. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    /** True when the OS will actually show a notification we post. */
    fun canNotify(): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && manager.areNotificationsEnabled()
    }

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_reminders))
                .setDescription(context.getString(R.string.channel_reminders_description))
                .build()
        )
    }

    /** Posts the notification. Caller must have checked [canNotify]. */
    fun notify(reminder: Reminder, threadTitle: String?) {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_BASE + (reminder.id % 100_000).toInt(),
            DeepLinks.openThread(context, reminder.threadId, reminder.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(threadTitle ?: context.getString(R.string.reminder_title_default))
            .setContentText(reminder.reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.reminderText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            manager.notify(notificationId(reminder.id), notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post; nothing more we can do.
        }
    }

    fun cancel(reminderId: Long) = manager.cancel(notificationId(reminderId))

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val ID_BASE = 10_000
        private const val REQUEST_BASE = 1_000_000
        fun notificationId(reminderId: Long): Int = ID_BASE + (reminderId % 100_000).toInt()
    }
}
