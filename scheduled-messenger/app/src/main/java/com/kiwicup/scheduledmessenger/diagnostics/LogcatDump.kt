package com.kiwicup.scheduledmessenger.diagnostics

import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * Captures this process's own logcat lines.
 *
 * Reading another app's log needs the READ_LOGS permission (system apps only); reading *your
 * own* process's log has needed no permission since Android 4.1, which is what makes this usable
 * here. Filtering by `--pid` keeps the dump to lines this app itself produced, cutting out the
 * rest of the device's log noise.
 */
object LogcatDump {

    /** The tail of this process's logcat, or an explanation string if the capture itself failed. */
    fun captureOwnProcess(maxLines: Int = 400): String = try {
        val pid = Process.myPid()
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS)
        val lines = output.lines()
        if (lines.size > maxLines) lines.takeLast(maxLines).joinToString("\n") else output
    } catch (e: Exception) {
        "Could not capture logcat: ${e.javaClass.simpleName}: ${e.message}"
    }
}
