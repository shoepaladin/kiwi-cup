package com.kiwicup.scheduledmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kiwicup.scheduledmessenger.work.RearmWorker

/**
 * After a reboot (or an app update) WorkManager's own queue survives, but delays are wall-clock
 * relative and can be lost if the app was force-stopped. Re-arming from Room is cheap and
 * idempotent, so we always do it.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED_ACTIONS) RearmWorker.enqueue(context)
    }

    companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Delays are relative to the clock at enqueue time; re-arm when the user changes it.
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
