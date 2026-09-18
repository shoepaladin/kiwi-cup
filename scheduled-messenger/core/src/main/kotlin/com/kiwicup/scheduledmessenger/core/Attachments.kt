package com.kiwicup.scheduledmessenger.core

/** One media part of an MMS: where the bytes live and what they are. */
data class Attachment(val uri: String, val mimeType: String) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * Stores attachment lists in a single text column. Format: `uri|mime` joined by newlines.
 * Newlines and pipes never appear in URIs or MIME types, so no escaping is needed.
 */
object AttachmentCodec {
    private const val FIELD = '|'
    private const val ROW = '\n'

    fun encode(attachments: List<Attachment>): String? =
        attachments.takeIf { it.isNotEmpty() }?.joinToString(ROW.toString()) { "${it.uri}$FIELD${it.mimeType}" }

    fun decode(text: String?): List<Attachment> =
        text.orEmpty().split(ROW).filter { it.isNotBlank() }.mapNotNull { row ->
            val idx = row.lastIndexOf(FIELD)
            if (idx <= 0 || idx == row.length - 1) null else Attachment(row.substring(0, idx), row.substring(idx + 1))
        }
}

/** Comma-separated recipient lists for group messages. */
object Recipients {
    fun encode(addresses: List<String>): String = addresses.joinToString(",")

    fun decode(text: String?): List<String> =
        text.orEmpty().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Normalises every address; returns null if any is invalid or the list is empty. */
    fun normalizeAll(text: String?): List<String>? {
        val parsed = decode(text)
        if (parsed.isEmpty()) return null
        val normalised = parsed.map { RecipientValidator.normalizeOrNull(it) ?: return null }
        return normalised.distinct()
    }

    fun isGroup(text: String?): Boolean = decode(text).size > 1
}
