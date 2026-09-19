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
import com.kiwicup.scheduledmessenger.core.AttachmentCodec
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.system.ContactNames
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** "New message" notifications, posted only when this app is the default SMS app. */
@Singleton
class IncomingMessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactNames
) {
    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    fun canNotify(): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionOk && manager.areNotificationsEnabled()
    }

    fun notifyNewMessage(message: SmsMessage) {
        if (!canNotify()) return
        if (VisibleThread.current == message.threadId) return // the user is looking at it
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_messages))
                .setDescription(context.getString(R.string.channel_messages_description))
                .build()
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_BASE + (message.threadId % 100_000).toInt(),
            DeepLinks.openThread(context, message.threadId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val preview = message.body.ifBlank {
            if (AttachmentCodec.decode(message.attachments).isNotEmpty()) context.getString(R.string.preview_attachment) else ""
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(contacts.displayName(message.address))
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            manager.notify(notificationId(message.threadId), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun cancelForThread(threadId: Long) = manager.cancel(notificationId(threadId))

    companion object {
        const val CHANNEL_ID = "messages"
        private const val ID_BASE = 20_000
        private const val REQUEST_BASE = 2_000_000
        fun notificationId(threadId: Long): Int = ID_BASE + (threadId % 100_000).toInt()
    }
}
