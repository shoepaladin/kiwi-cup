package com.kiwicup.scheduledmessenger.data.sms

/** Outcome of one attempt to hand a message to the radio. */
sealed class SendResult {
    object Sent : SendResult()

    /** Worth retrying later (no service, radio off, timeout waiting for the receipt). */
    data class TransientFailure(val reason: String) : SendResult()

    /** Retrying will not help (bad address, missing permission, message rejected). */
    data class PermanentFailure(val reason: String) : SendResult()
}

/**
 * Thin seam over [android.telephony.SmsManager]. The worker only ever talks to this interface,
 * so unit tests substitute a fake and never touch the platform.
 */
interface SmsSender {
    suspend fun send(address: String, body: String): SendResult
}
