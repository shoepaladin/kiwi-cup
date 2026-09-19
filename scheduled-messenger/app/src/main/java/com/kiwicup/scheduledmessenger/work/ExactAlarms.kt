package com.kiwicup.scheduledmessenger.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms wake the device at the chosen minute even in Doze. Android 12+ lets the user
 * deny this ("Alarms & reminders" permission); when denied we silently rely on WorkManager.
 */
@Singleton
class ExactAlarms @Inject constructor(@ApplicationContext private val context: Context) {

    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean {
        val am = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun scheduleSms(messageId: Long, atMillis: Long) = schedule(ExactAlarmReceiver.KIND_SMS, messageId, atMillis)
    fun scheduleReminder(reminderId: Long, atMillis: Long) = schedule(ExactAlarmReceiver.KIND_REMINDER, reminderId, atMillis)
    fun cancelSms(messageId: Long) = cancel(ExactAlarmReceiver.KIND_SMS, messageId)
    fun cancelReminder(reminderId: Long) = cancel(ExactAlarmReceiver.KIND_REMINDER, reminderId)

    private fun schedule(kind: String, id: Long, atMillis: Long) {
        val am = alarmManager ?: return
        if (!canScheduleExact()) return
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(kind, id)) }
    }

    private fun cancel(kind: String, id: Long) {
        val am = alarmManager ?: return
        runCatching { am.cancel(pendingIntent(kind, id)) }
    }

    private fun pendingIntent(kind: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            ExactAlarmReceiver.intent(context, kind, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        /** Settings screen where the user can allow exact alarms on Android 12+. */
        fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
            } else null
    }
}
