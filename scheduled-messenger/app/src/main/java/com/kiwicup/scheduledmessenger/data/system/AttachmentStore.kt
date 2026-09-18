package com.kiwicup.scheduledmessenger.data.system

import android.content.Context
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
        if (ok) Attachment(Uri.fromFile(target).toString(), mime) else { target.delete(); null }
    }

    fun readBytes(attachment: Attachment): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
    }.getOrNull()

    fun delete(attachments: List<Attachment>) {
        attachments.forEach { a ->
            val uri = Uri.parse(a.uri)
            if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        }
    }
}
