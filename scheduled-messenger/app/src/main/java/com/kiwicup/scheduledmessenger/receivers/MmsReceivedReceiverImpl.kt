package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.net.Uri
import com.klinker.android.send_message.MmsReceivedReceiver
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import com.kiwicup.scheduledmessenger.diagnostics.MmsStoreProbe
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
        val mmsId = messageUri?.lastPathSegment?.toLongOrNull()
        AppLog.d(TAG, "incoming MMS downloaded: id=$mmsId")
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        runCatching {
            runBlocking {
                val imported = deps.importer().importNew()
                val row = mmsId?.let { deps.smsMessageDao().findByMmsSystemId(it) }
                AppLog.d(TAG, "after download: imported ${imported.imported} row(s); message in conversation list=${row != null}")
                row?.let { deps.notifier().notifyNewMessage(it) }
            }
        }.onFailure { AppLog.e(TAG, "post-receive work failed", it) }
        AppLog.d(TAG, MmsStoreProbe.snapshot(context))
    }

    override fun onError(context: Context, error: String?) {
        // The download itself failed: the phone was told a picture exists but could not fetch it.
        AppLog.e(TAG, "incoming MMS download failed: $error")
        AppLog.d(TAG, MmsStoreProbe.snapshot(context))
    }

    private companion object {
        const val TAG = "MmsReceived"
    }
}
