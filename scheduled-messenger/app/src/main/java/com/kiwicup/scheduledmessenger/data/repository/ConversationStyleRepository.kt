package com.kiwicup.scheduledmessenger.data.repository

import android.content.Context
import android.net.Uri
import com.kiwicup.scheduledmessenger.core.RecipientValidator
import com.kiwicup.scheduledmessenger.data.local.dao.ConversationStyleDao
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Per-contact look. Wallpapers picked from the photo picker are copied into app storage because
 * the picker's content URIs stop working once the picking activity finishes.
 */
@Singleton
class ConversationStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ConversationStyleDao
) {
    fun observe(address: String): Flow<ConversationStyle?> = dao.observe(key(address))
    suspend fun get(address: String): ConversationStyle? = dao.get(key(address))

    suspend fun setBubbleColors(address: String, incoming: Int?, outgoing: Int?) {
        val current = dao.get(key(address)) ?: ConversationStyle(key(address))
        save(current.copy(incomingBubbleColor = incoming, outgoingBubbleColor = outgoing))
    }

    suspend fun setWallpaperDim(address: String, percent: Int) {
        val current = dao.get(key(address)) ?: return
        save(current.copy(wallpaperDimPercent = percent.coerceIn(0, 100)))
    }

    /** Copies the picked image into app storage and records it. Returns false if it could not be read. */
    suspend fun setWallpaper(address: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val target = wallpaperFile(address)
        val ok = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!ok) return@withContext false
        val current = dao.get(key(address)) ?: ConversationStyle(key(address))
        save(current.copy(wallpaperPath = target.absolutePath))
        true
    }

    suspend fun clearWallpaper(address: String) {
        val current = dao.get(key(address)) ?: return
        current.wallpaperPath?.let { File(it).delete() }
        save(current.copy(wallpaperPath = null))
    }

    suspend fun reset(address: String) {
        dao.get(key(address))?.wallpaperPath?.let { File(it).delete() }
        dao.delete(key(address))
    }

    /** Empty styles are deleted rather than stored, so "reset" and "never customised" look the same. */
    private suspend fun save(style: ConversationStyle) {
        if (style.isEmpty) dao.delete(style.address) else dao.upsert(style)
    }

    private fun key(address: String): String = RecipientValidator.normalize(address)

    private fun wallpaperFile(address: String): File =
        File(context.filesDir, "wallpapers/${key(address).replace('+', 'p')}.img")
}
