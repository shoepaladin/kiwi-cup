package com.kiwicup.scheduledmessenger.data.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.net.Uri
import com.kiwicup.scheduledmessenger.core.Attachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies media the user picked into app storage, because photo-picker URIs expire once the
 * picker closes and a scheduled message may be sent hours later.
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun persist(source: Uri): Attachment? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(source) ?: "application/octet-stream"
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val target = File(dir, UUID.randomUUID().toString())
        val ok = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!ok) { target.delete(); return@withContext null }
        // Images are downscaled at send time; anything else must already fit an MMS.
        if (!mime.startsWith("image/") && target.length() > MMS_MAX_BYTES) { target.delete(); return@withContext null }
        Attachment(Uri.fromFile(target).toString(), mime)
    }

    fun readBytes(attachment: Attachment): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
    }.getOrNull()

    /**
     * Bytes ready for an MMS. Carriers reject messages above roughly 1 MB, so images larger than
     * [maxBytes] are re-encoded as JPEG at decreasing sizes and qualities until they fit.
     * Non-image media is passed through untouched (the carrier may still reject it).
     */
    fun readBytesForMms(attachment: Attachment, maxBytes: Int = MMS_MAX_BYTES): Pair<ByteArray, String>? {
        val raw = readBytes(attachment) ?: return null
        if (!attachment.isImage || raw.size <= maxBytes) return raw to attachment.mimeType
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MMS_MAX_EDGE_PX || bounds.outHeight / sample > MMS_MAX_EDGE_PX) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return raw to attachment.mimeType
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxBytes || (quality <= 40 && bitmap.width <= 480)) return bytes to "image/jpeg"
            if (quality > 40) {
                quality -= 15
            } else {
                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
                quality = 85
            }
        }
    }

    companion object {
        const val MMS_MAX_BYTES = 900 * 1024
        const val MMS_MAX_EDGE_PX = 1600
    }

    fun delete(attachments: List<Attachment>) {
        attachments.forEach { a ->
            val uri = Uri.parse(a.uri)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }
}
