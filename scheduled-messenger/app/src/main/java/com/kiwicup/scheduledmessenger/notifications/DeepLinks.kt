package com.kiwicup.scheduledmessenger.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kiwicup.scheduledmessenger.MainActivity

/** Intents that open a specific screen; the Compose navigation host reads these extras. */
object DeepLinks {
    const val ACTION_OPEN_THREAD = "com.kiwicup.scheduledmessenger.OPEN_THREAD"
    const val EXTRA_THREAD_ID = "threadId"
    const val EXTRA_REMINDER_ID = "reminderId"

    fun openThread(context: Context, threadId: Long, reminderId: Long? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_THREAD
            // Distinct data per target: PendingIntents compare intents without extras, so two
            // notifications for different threads (or a thread and a reminder) must not look equal.
            data = Uri.parse("scheduledmessenger://thread/$threadId" + (reminderId?.let { "?reminder=$it" } ?: ""))
            putExtra(EXTRA_THREAD_ID, threadId)
            if (reminderId != null) putExtra(EXTRA_REMINDER_ID, reminderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
