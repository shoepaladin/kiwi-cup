package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.klinker.android.send_message.MmsReceivedReceiver
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.notifications.IncomingMessageNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Called by the MMS library once an incoming MMS has been downloaded into the phone's store. */
@AndroidEntryPoint
class MmsReceivedReceiverImpl : MmsReceivedReceiver() {

    @Inject lateinit var importer: SmsInboxImporter
    @Inject lateinit var smsMessageDao: SmsMessageDao
    @Inject lateinit var notifier: IncomingMessageNotifier

    override fun onMessageReceived(context: Context, messageUri: Uri?) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val mmsId = messageUri?.lastPathSegment?.toLongOrNull()
                importer.importNew()
                val row = mmsId?.let { id -> smsMessageDao.findByMmsSystemId(id) }
                if (row != null) notifier.notifyNewMessage(row)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onError(context: Context, error: String?) {
        Log.w("MmsReceived", "MMS download failed: $error")
    }
}
