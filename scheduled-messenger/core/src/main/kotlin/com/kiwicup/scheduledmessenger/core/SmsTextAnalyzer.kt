package com.kiwicup.scheduledmessenger.core

/** Encoding the radio will use for a message body. */
enum class SmsEncoding { GSM_7BIT, UCS2 }

data class SmsTextInfo(
    val encoding: SmsEncoding,
    val length: Int,
    val segments: Int,
    /** Characters remaining before another segment is needed. */
    val remainingInSegment: Int
)

/**
 * Mirrors the arithmetic Android's SmsManager.divideMessage performs, so the compose box
 * can show "142/160 (1)" style counters without touching platform APIs.
 */
object SmsTextAnalyzer {

    private const val GSM_SINGLE = 160
    private const val GSM_MULTI = 153
    private const val UCS2_SINGLE = 70
    private const val UCS2_MULTI = 67

    private val gsmBasic: Set<Char> = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        ).toSet()

    /** Characters in the GSM extension table cost two septets each. */
    private val gsmExtended: Set<Char> = "\u000c^{}\\[~]|€".toSet()

    fun encodingOf(body: String): SmsEncoding =
        if (body.all { it in gsmBasic || it in gsmExtended }) SmsEncoding.GSM_7BIT else SmsEncoding.UCS2

    fun analyze(body: String): SmsTextInfo {
        val encoding = encodingOf(body)
        val units = when (encoding) {
            SmsEncoding.GSM_7BIT -> body.length + body.count { it in gsmExtended }
            SmsEncoding.UCS2 -> body.length
        }
        val (single, multi) = when (encoding) {
            SmsEncoding.GSM_7BIT -> GSM_SINGLE to GSM_MULTI
            SmsEncoding.UCS2 -> UCS2_SINGLE to UCS2_MULTI
        }
        val segments = when {
            units == 0 -> 1
            units <= single -> 1
            else -> (units + multi - 1) / multi
        }
        val capacity = if (segments == 1) single else segments * multi
        return SmsTextInfo(encoding, units, segments, capacity - units)
    }

    fun isSendable(body: String): Boolean = body.isNotBlank()
}
