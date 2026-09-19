package com.kiwicup.scheduledmessenger.ui

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.ui.permissions.PermissionAskLog
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionAskLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var log: PermissionAskLog

    @Before
    fun setUp() {
        log = PermissionAskLog(context)
        log.reset()
    }

    @Test
    fun anUnaskedPermissionCountsZero() {
        assertEquals(0, log.timesAsked(Manifest.permission.SEND_SMS))
    }

    @Test
    fun eachRequestIncrementsEveryPermissionInTheBatch() {
        val batch = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
        log.recordAsked(batch)
        log.recordAsked(batch)

        assertEquals(2, log.timesAsked(Manifest.permission.SEND_SMS))
        assertEquals(2, log.timesAsked(Manifest.permission.READ_SMS))
    }

    @Test
    fun permissionsOutsideTheBatchAreUntouched() {
        log.recordAsked(listOf(Manifest.permission.SEND_SMS))
        assertEquals(0, log.timesAsked(Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun aDuplicateInTheBatchStillCountsAsOneAsk() {
        // One request that happens to name a permission twice is still one dialog; double
        // counting it would push the diagnosis past the first-ask rule and hide a restriction.
        log.recordAsked(listOf(Manifest.permission.SEND_SMS, Manifest.permission.SEND_SMS))
        assertEquals(1, log.timesAsked(Manifest.permission.SEND_SMS))
    }

    @Test
    fun countsSurviveANewInstanceSoTheyOutliveProcessDeath() {
        // The first-ask rule is worthless if the count resets when Android kills the process.
        log.recordAsked(listOf(Manifest.permission.SEND_SMS))
        assertEquals(1, PermissionAskLog(context).timesAsked(Manifest.permission.SEND_SMS))
    }

    @Test
    fun resetClearsEverything() {
        log.recordAsked(listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS))
        log.reset()
        assertEquals(0, log.timesAsked(Manifest.permission.SEND_SMS))
        assertEquals(0, log.timesAsked(Manifest.permission.READ_SMS))
    }

    @Test
    fun anEmptyBatchChangesNothing() {
        log.recordAsked(emptyList())
        assertEquals(0, log.timesAsked(Manifest.permission.SEND_SMS))
    }
}
