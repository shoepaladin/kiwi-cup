package com.kiwicup.scheduledmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSms
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSmsHandler
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.notifications.IncomingMessageNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Incoming texts. `SMS_RECEIVED` reaches every app with the permission; `SMS_DELIVER` reaches
 * only the default SMS app, which is then responsible for storing and announcing the message.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: IncomingSmsHandler
    @Inject lateinit var smsMessageDao: SmsMessageDao
    @Inject lateinit var notifier: IncomingMessageNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val incoming = parse(intent) ?: return
        val weAreDefault = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val rowId = handler.handle(incoming)
                // Only the default app announces messages; otherwise the stock app already did.
                if (weAreDefault && rowId > 0) smsMessageDao.getById(rowId)?.let { notifier.notifyNewMessage(it) }
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
