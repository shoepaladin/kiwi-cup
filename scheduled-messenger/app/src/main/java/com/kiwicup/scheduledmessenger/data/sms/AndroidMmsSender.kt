package com.kiwicup.scheduledmessenger.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp
import com.kiwicup.scheduledmessenger.receivers.MmsSentReceiverImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Sends through the MMS library and suspends until [MmsSentReceiverImpl] reports the outcome.
 * Only the default SMS app may send MMS; anything else is a permanent failure with a clear reason.
 */
@Singleton
class AndroidMmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentStore: AttachmentStore
) : MmsSender {

    override suspend fun send(message: OutgoingMms): SendResult {
        if (!DefaultSmsApp.isDefault(context)) {
            return SendResult.PermanentFailure("Pictures and group texts need this app to be the default SMS app")
        }
        if (message.recipients.isEmpty()) return SendResult.PermanentFailure("No recipients")

        val token = UUID.randomUUID().toString()
        val outcome = CompletableDeferred<SendResult>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getStringExtra(MmsSentReceiverImpl.EXTRA_TOKEN) != token) return
                val code = intent.getIntExtra(MmsSentReceiverImpl.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                outcome.complete(if (code == Activity.RESULT_OK) SendResult.Sent else SendResult.TransientFailure("MMS send failed (code $code)"))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(MmsSentReceiverImpl.ACTION_LOCAL_RESULT), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val settings = Settings().apply {
                useSystemSending = true
                group = true
                deliveryReports = false
            }
            val libMessage = Message(message.body, message.recipients.toTypedArray())
            message.attachments.forEach { attachment ->
                val bytes = attachmentStore.readBytes(attachment)
                    ?: return SendResult.PermanentFailure("Attachment is missing: ${attachment.uri}")
                libMessage.addMedia(bytes, attachment.mimeType)
            }
            val sentIntent = Intent(context, MmsSentReceiverImpl::class.java).putExtra(MmsSentReceiverImpl.EXTRA_TOKEN, token)
            try {
                Transaction(context, settings)
                    .setExplicitBroadcastForSentMms(sentIntent)
                    .sendNewMessage(libMessage)
            } catch (e: Exception) {
                return SendResult.TransientFailure("Could not hand the MMS to the radio: ${e.message}")
            }
            return try {
                withTimeout(SEND_TIMEOUT_MILLIS) { outcome.await() }
            } catch (e: TimeoutCancellationException) {
                SendResult.TransientFailure("No MMS receipt within ${SEND_TIMEOUT_MILLIS / 1000}s")
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    companion object {
        const val SEND_TIMEOUT_MILLIS = 3L * 60L * 1000L
    }
}
