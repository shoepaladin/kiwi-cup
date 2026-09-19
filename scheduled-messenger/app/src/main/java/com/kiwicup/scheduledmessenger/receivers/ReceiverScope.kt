package com.kiwicup.scheduledmessenger.receivers

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs receiver work off the main thread with the broadcast kept alive, a hard deadline below
 * the system's 10 s limit, and a handler so a database or provider error is logged, not fatal.
 */
internal object ReceiverScope {
    private const val DEADLINE_MILLIS = 8_000L

    fun run(receiver: BroadcastReceiver, tag: String, block: suspend () -> Unit) {
        val pending = receiver.goAsync()
        val handler = CoroutineExceptionHandler { _, t -> Log.w(tag, "receiver work failed", t) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler).launch {
            try {
                withTimeout(DEADLINE_MILLIS) { block() }
            } catch (t: Throwable) {
                Log.w(tag, "receiver work failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
