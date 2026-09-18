package com.kiwicup.scheduledmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSms
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSmsHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Listens for incoming texts (any app with RECEIVE_SMS gets this broadcast, default SMS app or not). */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: IncomingSmsHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val incoming = parse(intent) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handler.handle(incoming)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Reassembles a multipart text into one [IncomingSms]; null when the intent carries nothing usable. */
        fun parse(intent: Intent): IncomingSms? {
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return null
            val first = parts.firstOrNull() ?: return null
            val address = first.displayOriginatingAddress ?: return null
            val body = parts.joinToString("") { it.displayMessageBody ?: "" }
            return IncomingSms(address, body, first.timestampMillis)
        }
    }
}
