package com.kiwicup.scheduledmessenger.data.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.net.Uri
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.MmsBudget
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
        // Images are downscaled at send time; anything else is sent as-is and must already be
        // small. This ceiling is the library's own (MmsConfig 800 KB-ish) rather than the 300 KB
        // message budget: video has no compressor yet, so a tighter check here would refuse
        // almost every clip outright. Video compression is a tracked gap (docs/FEATURES.md).
        if (!mime.startsWith("image/") && target.length() > NON_IMAGE_MAX_BYTES) { target.delete(); return@withContext null }
        Attachment(Uri.fromFile(target).toString(), mime)
    }

    fun readBytes(attachment: Attachment): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
    }.getOrNull()

    /** One part ready for an MMS, plus its size before compression for the send log. */
    data class MmsPart(val bytes: ByteArray, val mimeType: String, val originalBytes: Int)

    /**
     * Bytes ready for an MMS. Images larger than [maxBytes] are re-encoded as JPEG at decreasing
     * sizes and qualities until they fit; callers pass each picture's share of the whole-message
     * budget (see core MmsBudget). Non-image media is passed through untouched.
     */
    fun readBytesForMms(attachment: Attachment, maxBytes: Int = MmsBudget.MESSAGE_BYTES): MmsPart? {
        val raw = readBytes(attachment) ?: return null
        if (!attachment.isImage || raw.size <= maxBytes) return MmsPart(raw, attachment.mimeType, raw.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MMS_MAX_EDGE_PX || bounds.outHeight / sample > MMS_MAX_EDGE_PX) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.let { uprightFor(raw, it) }
            ?: return MmsPart(raw, attachment.mimeType, raw.size)
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxBytes || (quality <= 40 && bitmap.width <= 480)) return MmsPart(bytes, "image/jpeg", raw.size)
            if (quality > 40) {
                quality -= 15
            } else {
                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
                quality = 85
            }
        }
    }

    /**
     * Applies the photo's EXIF rotation to the pixels. Re-encoding drops EXIF, and cameras store
     * portrait shots as landscape pixels plus an orientation tag, so without this every portrait
     * photo that gets compressed (nearly all of them, at a 300 KB budget) arrives sideways.
     * Same handling as Fossify's ImageCompressor.determineImageRotation: rotations only (tags 6, 3,
     * 8); the rare mirrored orientations are left as they are.
     */
    private fun uprightFor(raw: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        /** Largest non-image file accepted at pick time; see persist(). */
        const val NON_IMAGE_MAX_BYTES = 700 * 1024
        const val MMS_MAX_EDGE_PX = 1600
    }

    fun delete(attachments: List<Attachment>) {
        attachments.forEach { a ->
            val uri = Uri.parse(a.uri)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }
}
