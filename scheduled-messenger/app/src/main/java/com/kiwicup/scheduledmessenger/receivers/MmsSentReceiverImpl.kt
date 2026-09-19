package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.klinker.android.send_message.MmsSentReceiver
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * The MMS library reports the outcome of a send here (explicit intent from within this app).
 * The base class already marks the row in the phone's store and calls this on a background
 * thread; we relay the result to the sender waiting inside the app and refresh the inbox.
 */
class MmsSentReceiverImpl : MmsSentReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun importer(): SmsInboxImporter
    }

    override fun onMessageStatusUpdated(context: Context, intent: Intent, resultCode: Int) {
        intent.getStringExtra(EXTRA_TOKEN)?.let { token ->
            context.sendBroadcast(
                Intent(ACTION_LOCAL_RESULT)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_TOKEN, token)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
            )
        }
        runCatching {
            val importer = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).importer()
            runBlocking { importer.importNew() }
        }.onFailure { Log.w("MmsSent", "import after send failed", it) }
    }

    companion object {
        const val ACTION_LOCAL_RESULT = "com.kiwicup.scheduledmessenger.MMS_RESULT"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_RESULT_CODE = "result_code"
    }
}
