package com.kiwicup.scheduledmessenger.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
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

    @Inject lateinit var scheduledMessages: ScheduledMessageRepository
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
                scheduledMessages.schedule(Recipients.encode(request.recipients), request.text, timeSource.now())
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
