package com.kiwicup.scheduledmessenger.work

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.receivers.ExactAlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ExactAlarmsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadowAlarms get() = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))

    @Test
    fun schedulesAndCancelsDistinctAlarmsPerMessageAndReminder() {
        val alarms = ExactAlarms(context)
        assertTrue(alarms.canScheduleExact())

        alarms.scheduleSms(1, 5_000L)
        alarms.scheduleSms(2, 6_000L)
        alarms.scheduleReminder(1, 7_000L)
        assertEquals(3, shadowAlarms.scheduledAlarms.size)

        alarms.cancelSms(1)
        assertEquals(2, shadowAlarms.scheduledAlarms.size)
        assertEquals(listOf(6_000L, 7_000L), shadowAlarms.scheduledAlarms.map { it.triggerAtMs }.sorted())
    }

    @Test
    fun receiverIntentCarriesKindAndId() {
        val intent = ExactAlarmReceiver.intent(context, ExactAlarmReceiver.KIND_SMS, 42)
        assertEquals(ExactAlarmReceiver.ACTION, intent.action)
        assertEquals(42L, intent.getLongExtra(ExactAlarmReceiver.EXTRA_ID, -1))
        assertEquals("scheduledmessenger://alarm/sms/42", intent.dataString)
    }

    @Test
    @Config(sdk = [34])
    fun sameIdDifferentKindDoNotCollide() {
        val alarms = ExactAlarms(context)
        alarms.scheduleSms(7, 1_000L)
        alarms.scheduleReminder(7, 2_000L)
        assertEquals(2, shadowAlarms.scheduledAlarms.size)
    }
}
