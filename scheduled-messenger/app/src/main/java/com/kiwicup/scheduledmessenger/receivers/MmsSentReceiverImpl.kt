package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.MmsSentReceiver

/**
 * The MMS library reports the outcome of a send here (matched by task affinity in the manifest).
 * The library's base class already marks the row in the phone's store; we relay the result to
 * whoever is waiting on it inside the app.
 */
class MmsSentReceiverImpl : MmsSentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent, resultCode: Int) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        context.sendBroadcast(
            Intent(ACTION_LOCAL_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
        )
    }

    companion object {
        const val ACTION_LOCAL_RESULT = "com.kiwicup.scheduledmessenger.MMS_RESULT"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_RESULT_CODE = "result_code"
    }
}
