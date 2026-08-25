package com.example.screenmytexts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val filters = loadFilters(context)
            
            for (message in messages) {
                val body = message.displayMessageBody
                val isFiltered = filters.any { it.matches(body) }
                
                if (isFiltered) {
                    Log.d("SmsReceiver", "Filtered message from ${message.displayOriginatingAddress}: $body")
                    // Note: abortBroadcast() only works for high priority receivers 
                    // and typically only if the app is the default SMS app or has special permissions.
                    // For now, we just log it as a proof of concept.
                } else {
                    Log.d("SmsReceiver", "Allowed message from ${message.displayOriginatingAddress}: $body")
                }
            }
        }
    }
}
