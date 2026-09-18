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
import org.robolectric.annotation.Config

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
}
