package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.MmsSentReceiver
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The MMS library reports the outcome of a send here (matched by task affinity in the manifest).
 * The library's base class already marks the row in the phone's store; we relay the result to
 * whoever is waiting on it inside the app.
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
        // Pull the library's copy of the sent MMS into our inbox so the conversation updates now.
        val importer = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).importer()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runCatching { importer.importNew() } } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_LOCAL_RESULT = "com.kiwicup.scheduledmessenger.MMS_RESULT"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_RESULT_CODE = "result_code"
    }
}
