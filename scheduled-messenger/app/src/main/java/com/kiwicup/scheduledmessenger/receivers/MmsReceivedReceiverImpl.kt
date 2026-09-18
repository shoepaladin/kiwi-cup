package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.klinker.android.send_message.MmsReceivedReceiver
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.notifications.IncomingMessageNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Called by the MMS library once an incoming MMS has been downloaded into the phone's store.
 *
 * The library's base class makes `onReceive` final, so Hilt cannot subclass it; dependencies
 * are looked up through an entry point instead of field injection.
 */
class MmsReceivedReceiverImpl : MmsReceivedReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun importer(): SmsInboxImporter
        fun smsMessageDao(): SmsMessageDao
        fun notifier(): IncomingMessageNotifier
    }

    override fun onMessageReceived(context: Context, messageUri: Uri?) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val mmsId = messageUri?.lastPathSegment?.toLongOrNull()
                deps.importer().importNew()
                val row = mmsId?.let { id -> deps.smsMessageDao().findByMmsSystemId(id) }
                if (row != null) deps.notifier().notifyNewMessage(row)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onError(context: Context, error: String?) {
        Log.w("MmsReceived", "MMS download failed: $error")
    }
}
