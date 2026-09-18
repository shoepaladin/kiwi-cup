package com.kiwicup.scheduledmessenger.notifications

import android.content.Context
import android.content.Intent
import com.kiwicup.scheduledmessenger.MainActivity

/** Intents that open a specific screen; the Compose navigation host reads these extras. */
object DeepLinks {
    const val ACTION_OPEN_THREAD = "com.kiwicup.scheduledmessenger.OPEN_THREAD"
    const val EXTRA_THREAD_ID = "threadId"
    const val EXTRA_REMINDER_ID = "reminderId"

    fun openThread(context: Context, threadId: Long, reminderId: Long? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_THREAD
            putExtra(EXTRA_THREAD_ID, threadId)
            if (reminderId != null) putExtra(EXTRA_REMINDER_ID, reminderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
