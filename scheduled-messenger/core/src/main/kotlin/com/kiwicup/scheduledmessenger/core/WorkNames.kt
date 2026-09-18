package com.kiwicup.scheduledmessenger.core

/**
 * Unique WorkManager work names. Using the database id makes enqueue idempotent
 * (ExistingWorkPolicy.REPLACE) so a reboot re-enqueue can never create duplicates.
 */
object WorkNames {
    const val SCHEDULED_SMS_PREFIX = "scheduled-sms-"
    const val REMINDER_PREFIX = "reminder-"
    const val TAG_SCHEDULED_SMS = "scheduled-sms"
    const val TAG_REMINDER = "reminder"

    fun scheduledSms(messageId: Long): String = "$SCHEDULED_SMS_PREFIX$messageId"
    fun reminder(reminderId: Long): String = "$REMINDER_PREFIX$reminderId"

    fun messageIdFrom(workName: String): Long? =
        workName.removePrefix(SCHEDULED_SMS_PREFIX).takeIf { it != workName }?.toLongOrNull()

    fun reminderIdFrom(workName: String): Long? =
        workName.removePrefix(REMINDER_PREFIX).takeIf { it != workName }?.toLongOrNull()
}
