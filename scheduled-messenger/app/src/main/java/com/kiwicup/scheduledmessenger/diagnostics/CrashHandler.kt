package com.kiwicup.scheduledmessenger.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Installs a global uncaught-exception handler that writes a full report to disk before the
 * process dies.
 *
 * This does not swallow the crash — it still hands off to whatever handler was installed before
 * it (Android's default one, which shows the "app has stopped" dialog and kills the process), so
 * the app crashes exactly as it would have otherwise. All this adds is a chance to read the
 * report afterward: the user is phone-only with no adb, so without this the only evidence of a
 * crash is a system dialog that closes and takes the stack trace with it.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val REPORT_FILE = "last_crash.txt"

    /** Call once, as early as possible — see ScheduledMessengerApp.onCreate. */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(context, thread, throwable)
            } catch (writeFailure: Throwable) {
                // Never let the reporter itself be the reason the original crash goes unreported.
                Log.e(TAG, "failed to write crash report", writeFailure)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** The last crash report written, or null if none exists (fresh install, or none yet). */
    fun lastReport(context: Context): String? = reportFile(context).takeIf { it.exists() }?.readText()

    fun clearLastReport(context: Context) {
        reportFile(context).delete()
    }

    private fun reportFile(context: Context) = File(File(context.filesDir, "diagnostics").apply { mkdirs() }, REPORT_FILE)

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        val report = buildString {
            appendLine("Scheduled Messenger crash report")
            appendLine("Time: ${Instant.now()}")
            appendLine("Thread: ${thread.name}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App version: $versionName")
            appendLine()
            appendLine("--- Exception ---")
            append(stackTrace)
            appendLine()
            appendLine("--- Recent app log ---")
            appendLine(AppLog.tail(150))
            appendLine()
            appendLine("--- logcat (this process) ---")
            appendLine(LogcatDump.captureOwnProcess())
        }
        reportFile(context).writeText(report)
    }
}
