package com.kiwicup.scheduledmessenger.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A small rolling log for exactly one purpose: when the user reports "it crashed" with no other
 * detail, there is something to read afterward.
 *
 * `adb logcat` is not an option for this project — the user is phone-only, no computer, no adb —
 * so this exists as the on-device substitute: every line also goes to Log.println (so a computer
 * *can* read it live if one is ever plugged in), and is kept both in memory and appended to a
 * capped file, so it survives whatever wiped the process. [CrashHandler] reads [tail] into every
 * crash report; the Settings screen lets the user export [fileContents] on demand, the same
 * Downloads/clipboard route [com.kiwicup.scheduledmessenger.ui.permissions.DiagnosticFile]
 * already uses for the permission diagnostic text.
 */
object AppLog {

    private const val MAX_MEMORY_LINES = 400
    private const val MAX_FILE_BYTES = 512 * 1024L
    private val TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val lock = Any()
    private val memory = ArrayDeque<String>()
    private var logFile: File? = null

    /** Call once, as the first thing the process does — see ScheduledMessengerApp.onCreate. */
    fun init(context: Context) {
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        logFile = File(dir, "app.log")
        d("AppLog", "── process start ──")
    }

    fun d(tag: String, message: String) = write(Log.DEBUG, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    /** The last [maxLines] breadcrumbs, newest last — what [CrashHandler] embeds in a report. */
    fun tail(maxLines: Int = 150): String = synchronized(lock) { memory.takeLast(maxLines).joinToString("\n") }

    /** The full on-disk log, for the Settings screen's "Save log" action. */
    fun fileContents(): String = logFile?.takeIf { it.exists() }?.readText().orEmpty()

    private fun write(priority: Int, tag: String, message: String, error: Throwable?) {
        Log.println(priority, tag, message)
        error?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
        val stamp = LocalDateTime.now().format(TIMESTAMP)
        val level = when (priority) { Log.ERROR -> "E"; Log.WARN -> "W"; else -> "D" }
        val line = buildString {
            append(stamp).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (error != null) append(" — ").append(error.javaClass.simpleName).append(": ").append(error.message)
        }
        synchronized(lock) {
            memory.addLast(line)
            while (memory.size > MAX_MEMORY_LINES) memory.removeFirst()
            appendToFile(line, error)
        }
    }

    /** Runs under [lock]. Best-effort: a logging failure must never itself throw. */
    private fun appendToFile(line: String, error: Throwable?) {
        val file = logFile ?: return
        runCatching {
            if (file.length() > MAX_FILE_BYTES) {
                // Cheap rotation: keep the newest half rather than growing forever or losing
                // everything. This runs rarely — most sessions never approach the cap.
                val kept = file.readText().takeLast((MAX_FILE_BYTES / 2).toInt())
                file.writeText(kept)
            }
            file.appendText(line + "\n")
            if (error != null) file.appendText(Log.getStackTraceString(error) + "\n")
        }
    }
}
