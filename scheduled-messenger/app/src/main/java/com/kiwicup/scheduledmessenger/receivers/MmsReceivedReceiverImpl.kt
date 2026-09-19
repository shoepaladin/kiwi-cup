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
import kotlinx.coroutines.runBlocking

/**
 * Called by the MMS library once an incoming MMS has been downloaded into the phone's store.
 *
 * The library's `onReceive` is final, already holds the broadcast open and invokes this on its own
 * background thread, so the work runs inline (no second goAsync) and Hilt dependencies come from
 * an entry point rather than field injection.
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
        runCatching {
            runBlocking {
                deps.importer().importNew()
                val mmsId = messageUri?.lastPathSegment?.toLongOrNull() ?: return@runBlocking
                deps.smsMessageDao().findByMmsSystemId(mmsId)?.let { deps.notifier().notifyNewMessage(it) }
            }
        }.onFailure { Log.w("MmsReceived", "post-receive work failed", it) }
    }

    override fun onError(context: Context, error: String?) {
        Log.w("MmsReceived", "MMS download failed: $error")
    }
}
