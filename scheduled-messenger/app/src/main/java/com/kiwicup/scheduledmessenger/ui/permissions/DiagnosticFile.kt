package com.kiwicup.scheduledmessenger.ui.permissions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Gets the diagnostic text off the phone. Two routes, because neither is reliable alone: the file
 * lands in Downloads where any file manager can reach it, and the clipboard copy covers the case
 * where the user is working on the phone and just wants to paste it into a chat.
 */
object DiagnosticFile {

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Writes to Downloads and returns where it landed, or a message saying why it could not. */
    fun save(context: Context, text: String): String {
        val name = "scheduled-messenger-diagnostics-${LocalDateTime.now().format(STAMP)}.txt"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToDownloads(context, name, text)
            else saveToAppStorage(context, name, text)
        }.getOrElse { "Could not save: ${it.javaClass.simpleName}: ${it.message}" }
    }

    /**
     * MediaStore rather than a raw path: scoped storage blocks writing to Downloads directly on
     * API 29+, and this route needs no storage permission at all — which matters when the whole
     * problem being diagnosed is a permission that will not grant.
     */
    private fun saveToDownloads(context: Context, name: String, text: String): String {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: return "Could not save: the media store refused the file"
        resolver.openOutputStream(uri).use { stream ->
            checkNotNull(stream) { "no output stream" }.write(text.toByteArray())
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return "Saved to Downloads/$name"
    }

    private fun saveToAppStorage(context: Context, name: String, text: String): String {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(directory, name)
        file.writeText(text)
        return "Saved to ${file.absolutePath}"
    }

    fun copyToClipboard(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Scheduled Messenger diagnostics", text))
    }
}
