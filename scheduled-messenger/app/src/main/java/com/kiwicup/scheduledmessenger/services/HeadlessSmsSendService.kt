package com.kiwicup.scheduledmessenger.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.TimeSource
import android.util.Log
import com.kiwicup.scheduledmessenger.data.inbox.SentMessageRecorder
import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Required of every default SMS app: the dialer's "reply with message" on an incoming call
 * hands us a recipient and text to send immediately.
 */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject lateinit var smsSender: SmsSender
    @Inject lateinit var sentRecorder: SentMessageRecorder
    @Inject lateinit var timeSource: TimeSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.let(::parse)
        if (request == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                // The caller (in-call screen) expects the text to go out now, not via a queued job.
                request.recipients.forEach { address ->
                    if (smsSender.send(address, request.text) is SendResult.Sent) {
                        sentRecorder.record(address, request.text, null, timeSource.now())
                    }
                }
            } catch (t: Throwable) {
                Log.w("QuickReply", "quick reply failed", t)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    data class QuickReply(val recipients: List<String>, val text: String)

    companion object {
        /** Parses `sms:`/`smsto:`/`mms:`/`mmsto:` data plus EXTRA_TEXT; null when either is missing. */
        fun parse(intent: Intent): QuickReply? {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val data = intent.dataString ?: return null
            val raw = Uri.decode(data).removePrefix("smsto:").removePrefix("sms:").removePrefix("mmsto:").removePrefix("mms:").trim()
            val recipients = Recipients.normalizeAll(raw) ?: return null
            if (text.isEmpty()) return null
            return QuickReply(recipients, text)
        }
    }
}
