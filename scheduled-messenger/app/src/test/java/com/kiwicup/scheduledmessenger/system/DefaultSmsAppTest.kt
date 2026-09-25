package com.kiwicup.scheduledmessenger.system

import android.content.Context
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import android.app.role.RoleManager
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTelephony

@RunWith(AndroidJUnit4::class)
class DefaultSmsAppTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun freshInstallIsNotDefault() {
        assertFalse(DefaultSmsApp.isDefault(context))
    }

    @Test
    @Config(sdk = [28])
    fun legacyDevicesUseTheChangeDefaultIntent() {
        val intent = DefaultSmsApp.requestIntent(context)
        assertNotNull(intent)
        assertEquals(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT, intent!!.action)
        assertEquals(context.packageName, intent.getStringExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME))
    }

    private fun holdSmsRole() {
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
    }

    @Test
    fun holdingTheRoleMakesThisTheDefaultEvenWhenTheOlderCallSaysOtherwise() {
        // The device report: the user made this the default, yet pictures were refused because
        // getDefaultSmsPackage did not name this app. The role is what Android actually grants.
        holdSmsRole()
        ShadowTelephony.ShadowSms.setDefaultSmsPackage(null)

        assertTrue(DefaultSmsApp.isDefault(context))
        assertTrue(DefaultSmsApp.readings(context).disagree)
    }

    @Test
    fun whenTheRoleCanAnswerItsNoIsBelievedOverTheOlderCall() {
        // Guessing "yes" wrongly makes the receiver drop SMS_RECEIVED and lose incoming texts.
        shadowOf(context.getSystemService(RoleManager::class.java)).addAvailableRole(RoleManager.ROLE_SMS)
        ShadowTelephony.ShadowSms.setDefaultSmsPackage(context.packageName)

        assertFalse(DefaultSmsApp.isDefault(context))
    }

    @Test
    fun holdingTheRoleMeansNoRequestIsOffered() {
        // Why the banner could not be dismissed: with the role already held there is nothing to
        // request, so "Set as default" did nothing while the older check kept the banner up.
        holdSmsRole()
        assertEquals(null, DefaultSmsApp.requestIntent(context))
    }
}
