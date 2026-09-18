package com.kiwicup.scheduledmessenger.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Sends through the platform [SmsManager] and suspends until every part reports back via its
 * `sentIntent` broadcast, so the caller learns whether the carrier actually accepted the message
 * rather than just whether it was queued.
 */
@Singleton
class AndroidSmsSender @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsSender {

    override suspend fun send(address: String, body: String): SendResult {
        val smsManager = smsManager() ?: return SendResult.PermanentFailure("SMS not supported on this device")
        val parts: ArrayList<String> = try {
            smsManager.divideMessage(body)
        } catch (e: RuntimeException) {
            return SendResult.PermanentFailure("Could not split message: ${e.message}")
        }
        if (parts.isEmpty()) return SendResult.PermanentFailure("Empty message")

        val action = "$ACTION_PREFIX${UUID.randomUUID()}"
        val outcome = CompletableDeferred<SendResult>()
        val remaining = AtomicInteger(parts.size)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val failure = failureFor(resultCode)
                if (failure != null && !outcome.isCompleted) {
                    outcome.complete(failure)
                } else if (remaining.decrementAndGet() == 0 && !outcome.isCompleted) {
                    outcome.complete(SendResult.Sent)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (index in parts.indices) {
                val intent = Intent(action).setPackage(context.packageName).putExtra(EXTRA_PART, index)
                sentIntents += PendingIntent.getBroadcast(
                    context,
                    index,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            try {
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            } catch (e: SecurityException) {
                return SendResult.PermanentFailure("SEND_SMS permission missing")
            } catch (e: IllegalArgumentException) {
                return SendResult.PermanentFailure("Invalid recipient or message: ${e.message}")
            }
            return try {
                withTimeout(RECEIPT_TIMEOUT_MILLIS) { outcome.await() }
            } catch (e: TimeoutCancellationException) {
                SendResult.TransientFailure("No delivery receipt within ${RECEIPT_TIMEOUT_MILLIS / 1000}s")
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private fun failureFor(resultCode: Int): SendResult? = when (resultCode) {
        Activity.RESULT_OK -> null
        SmsManager.RESULT_ERROR_NO_SERVICE -> SendResult.TransientFailure("No cellular service")
        SmsManager.RESULT_ERROR_RADIO_OFF -> SendResult.TransientFailure("Radio is off (airplane mode?)")
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SendResult.TransientFailure("Carrier rejected the message")
        SmsManager.RESULT_ERROR_NULL_PDU -> SendResult.PermanentFailure("Message could not be encoded")
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> SendResult.PermanentFailure("SMS sending limit exceeded")
        else -> SendResult.TransientFailure("SMS error code $resultCode")
    }

    companion object {
        private const val ACTION_PREFIX = "com.kiwicup.scheduledmessenger.SMS_SENT."
        private const val EXTRA_PART = "part"
        const val RECEIPT_TIMEOUT_MILLIS = 60_000L
    }
}
