package com.kiwicup.scheduledmessenger.services

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadlessSmsSendServiceTest {

    @Test
    fun parsesRecipientAndTextFromQuickReplyIntent() {
        val intent = Intent("android.intent.action.RESPOND_VIA_MESSAGE", Uri.parse("smsto:%2B1%20555%20000%201111"))
            .putExtra(Intent.EXTRA_TEXT, "  Can't talk now  ")
        val reply = HeadlessSmsSendService.parse(intent)!!
        assertEquals(listOf("+15550001111"), reply.recipients)
        assertEquals("Can't talk now", reply.text)
    }

    @Test
    fun rejectsMissingTextOrBadRecipient() {
        assertNull(HeadlessSmsSendService.parse(Intent("x", Uri.parse("sms:+15550001111"))))
        assertNull(HeadlessSmsSendService.parse(Intent("x", Uri.parse("sms:bob")).putExtra(Intent.EXTRA_TEXT, "hi")))
        assertNull(HeadlessSmsSendService.parse(Intent("x").putExtra(Intent.EXTRA_TEXT, "hi")))
    }
}
