package com.kiwicup.scheduledmessenger.ui.permissions

import android.content.Context
import com.kiwicup.scheduledmessenger.core.RoleAttempt

/**
 * Remembers what happened the last time the default-SMS-app role dialog was launched.
 *
 * This exists because every state the app can read beforehand says the role is obtainable —
 * `isRoleAvailable` is true, `createRequestRoleIntent` returns an intent — on a device that then
 * refuses to draw the dialog. The outcome of actually asking is the only reliable evidence, and it
 * has to survive the process so a report written later still carries it.
 */
class RoleAttemptLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(attempt: RoleAttempt) {
        prefs.edit()
            .putBoolean(LAUNCHED, attempt.launched)
            .putBoolean(FAILED, attempt.failedToLaunch)
            .putBoolean(ACCEPTED, attempt.accepted)
            .putLong(ELAPSED, attempt.elapsedMs)
            .apply()
    }

    fun last(): RoleAttempt = RoleAttempt(
        launched = prefs.getBoolean(LAUNCHED, false),
        failedToLaunch = prefs.getBoolean(FAILED, false),
        accepted = prefs.getBoolean(ACCEPTED, false),
        elapsedMs = prefs.getLong(ELAPSED, 0)
    )

    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "role_attempt"
        const val LAUNCHED = "launched"
        const val FAILED = "failed_to_launch"
        const val ACCEPTED = "accepted"
        const val ELAPSED = "elapsed_ms"
    }
}
