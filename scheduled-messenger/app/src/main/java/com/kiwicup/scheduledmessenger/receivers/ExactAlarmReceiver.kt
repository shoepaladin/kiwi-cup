package com.kiwicup.scheduledmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kiwicup.scheduledmessenger.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** An exact alarm went off: run the corresponding job immediately. */
@AndroidEntryPoint
class ExactAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_SMS -> scheduler.runSmsNow(id)
            KIND_REMINDER -> scheduler.runReminderNow(id)
        }
    }

    companion object {
        const val ACTION = "com.kiwicup.scheduledmessenger.EXACT_ALARM"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val KIND_SMS = "sms"
        const val KIND_REMINDER = "reminder"

        /** Distinct data URI per (kind, id) so PendingIntents never collide. */
        fun intent(context: Context, kind: String, id: Long): Intent =
            Intent(context, ExactAlarmReceiver::class.java)
                .setAction(ACTION)
                .setData(Uri.parse("scheduledmessenger://alarm/$kind/$id"))
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_ID, id)
    }
}
