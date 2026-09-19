package com.kiwicup.scheduledmessenger.notifications

import android.content.Intent
import android.net.Uri
import com.kiwicup.scheduledmessenger.core.Recipients

/** A request from another app to start a message: recipients and an optional body. */
data class ComposeRequest(val recipients: String, val body: String) {
    companion object {
        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

        /** Parses `sms:+1555?body=hi` style intents (SENDTO / VIEW / SEND); null for anything else. */
        fun from(intent: Intent): ComposeRequest? {
            val data: Uri = intent.data ?: return null
            if (data.scheme?.lowercase() !in SCHEMES) return null
            val raw = data.schemeSpecificPart.orEmpty().substringBefore('?')
            val recipients = Recipients.normalizeAll(Uri.decode(raw)) ?: emptyList()
            val body = intent.getStringExtra("sms_body")
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: runCatching { data.getQueryParameter("body") }.getOrNull()
                ?: ""
            return ComposeRequest(Recipients.encode(recipients), body)
        }
    }
}
