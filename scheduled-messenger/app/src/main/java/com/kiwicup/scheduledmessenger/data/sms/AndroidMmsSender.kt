package com.kiwicup.scheduledmessenger.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.kiwicup.scheduledmessenger.core.MmsBudget
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp
import com.kiwicup.scheduledmessenger.receivers.MmsSentReceiverImpl
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
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

    /**
     * Every outcome is logged. Picture sending has never actually run on the user's phone — the
     * default check refused it first — so the first real attempts are the ones worth a record.
     * Only counts and types are logged, never the message text or the recipients.
     */
    override suspend fun send(message: OutgoingMms): SendResult {
        AppLog.d(TAG, "sending MMS: ${message.recipients.size} recipient(s), ${message.attachments.size} attachment(s)")
        val result = sendChecked(message)
        when (result) {
            SendResult.Sent -> AppLog.d(TAG, "MMS sent")
            is SendResult.PermanentFailure -> AppLog.e(TAG, "MMS failed permanently: ${result.reason}")
            is SendResult.TransientFailure -> AppLog.w(TAG, "MMS failed, will retry: ${result.reason}")
        }
        return result
    }

    private suspend fun sendChecked(message: OutgoingMms): SendResult {
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
            // Non-image parts go through untouched, so read them first: what they and the text
            // take comes out of the budget the pictures then share (see core MmsBudget).
            val (images, others) = message.attachments.partition { it.isImage }
            val otherParts = others.map { attachmentStore.readBytesForMms(it) ?: return SendResult.PermanentFailure("Attachment is missing: ${it.uri}") }
            val perImage = MmsBudget.perImageBytes(images.size, otherParts.sumOf { it.bytes.size } + message.body.toByteArray().size)
            val imageParts = images.map { attachmentStore.readBytesForMms(it, perImage) ?: return SendResult.PermanentFailure("Attachment is missing: ${it.uri}") }
            // Back into the order the user attached them in.
            val byAttachment = (images.zip(imageParts) + others.zip(otherParts)).toMap()
            val parts = message.attachments.map { byAttachment.getValue(it) }
            AppLog.d(
                TAG,
                "MMS parts: " + parts.joinToString { "${it.mimeType} ${it.originalBytes / 1024}KB->${it.bytes.size / 1024}KB" } +
                    "; total ${parts.sumOf { it.bytes.size } / 1024}KB; budget ${MmsBudget.MESSAGE_BYTES / 1024}KB" +
                    " (${perImage / 1024}KB per picture); carrier max ${carrierMaxMessageBytes()?.let { "${it / 1024}KB" } ?: "unknown"}"
            )
            parts.forEach { libMessage.addMedia(it.bytes, it.mimeType) }
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

    /**
     * What the carrier says its MMS limit is, for the log only. Deliberately not used to raise
     * the budget: a carrier's stated maximum and what it reliably delivers are not the same thing,
     * which is why QKSMS and Fossify both ship defaults well below typical stated maximums. Having
     * the value in the log lets a real failure be compared against it.
     */
    private fun carrierMaxMessageBytes(): Int? = runCatching {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        manager?.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)?.takeIf { it > 0 }
    }.getOrNull()

    companion object {
        const val SEND_TIMEOUT_MILLIS = 3L * 60L * 1000L
        private const val TAG = "MmsSender"
    }
}
