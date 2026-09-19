package com.kiwicup.scheduledmessenger.services

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.notifications.ComposeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeRequestTest {

    @Test
    fun parsesSmstoWithBodyQueryAndExtras() {
        val fromQuery = ComposeRequest.from(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+1%20555%20000%201111?body=see%20you")))!!
        assertEquals("+15550001111", fromQuery.recipients)
        assertEquals("see you", fromQuery.body)

        val fromExtra = ComposeRequest.from(Intent(Intent.ACTION_VIEW, Uri.parse("sms:5550001111,5550002222")).putExtra("sms_body", "hi all"))!!
        assertEquals("5550001111,5550002222", fromExtra.recipients)
        assertEquals("hi all", fromExtra.body)
    }

    @Test
    fun ignoresUnrelatedIntents() {
        assertNull(ComposeRequest.from(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
        assertNull(ComposeRequest.from(Intent(Intent.ACTION_MAIN)))
    }
}
