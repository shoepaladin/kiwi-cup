package com.kiwicup.scheduledmessenger.core

/**
 * How many bytes each picture in one MMS may take.
 *
 * Carriers limit the whole message, not each part, and this app sends every attachment of a
 * message in one MMS. A per-picture cap therefore lets three photos add up to three times the
 * limit, so the budget is for the message and is shared between its pictures.
 *
 * The budget follows the open-source apps, which ship small defaults because US carriers drop
 * larger messages after the phone has already reported them "sent": QKSMS defaults to 300 KB
 * (domain/.../util/Preferences.kt `mmsSize`), Fossify to 200 KB (helpers/Config.kt
 * `mmsFileSizeLimit`). The MMS library passes its own limit to Android: 800 KB until
 * `MmsConfig.init` loads the per-carrier `mms_config.xml` (generic file: 307200 bytes), which
 * only happens once an MMS has been received in the process. So until then a too-large picture
 * is accepted by the phone and can be lost at the carrier instead of failing visibly. 300 KB
 * stays within both values. See docs/FEATURES.md, "MMS image size budget".
 */
object MmsBudget {

    /** Whole-message budget; QKSMS's default. */
    const val MESSAGE_BYTES = 300 * 1024

    /** Room left for MMS headers, the SMIL layout part and part names. */
    const val OVERHEAD_BYTES = 10 * 1024

    /** Below this a photo stops being recognisable, so no picture is squeezed further. */
    const val MIN_PER_IMAGE_BYTES = 40 * 1024

    fun perImageBytes(imageCount: Int, otherPartBytes: Int, messageBytes: Int = MESSAGE_BYTES): Int {
        if (imageCount <= 0) return messageBytes
        val available = messageBytes - OVERHEAD_BYTES - otherPartBytes.coerceAtLeast(0)
        return (available / imageCount).coerceAtLeast(MIN_PER_IMAGE_BYTES)
    }
}
