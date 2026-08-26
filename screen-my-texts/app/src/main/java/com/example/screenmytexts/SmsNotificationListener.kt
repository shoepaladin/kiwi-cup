package com.example.screenmytexts

import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!isMessagingApp(packageName)) return

        val combined = extractText(sbn)
        Log.d("NotificationListener", "pkg=$packageName text=$combined")
        if (combined.isBlank()) return

        // Allow list wins: if the message matches any allow rule, never screen it.
        // This protects OTPs, bank alerts, and known contacts from being hidden.
        val allowList = loadFilters(this, ALLOW_LIST_KEY)
        if (allowList.any { it.matches(combined) }) {
            Log.d("NotificationListener", "Allow-listed, keeping: $combined")
            return
        }

        val filters = loadFilters(this, BLOCK_LIST_KEY)
        val isFiltered = filters.any { it.matches(combined) }

        if (isFiltered) {
            Log.d("NotificationListener", "Dismissing notification: $combined")
            addToHistory(this, combined)
            cancelNotification(sbn.key)
        }
    }

    /**
     * Matches any app likely to surface SMS/MMS/RCS messages. Covers Textra, Google
     * Messages, Samsung Messages, AOSP/OEM com.android.mms, and most carrier apps.
     */
    private fun isMessagingApp(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.textra" ||
            pkg.contains("messaging") ||
            pkg.contains("message") ||
            pkg.contains("sms") ||
            pkg.contains("mms")
    }

    /**
     * Pulls message content from every field an SMS notification might use, not just
     * android.text. Long messages live in bigText; multiple unread messages in a thread
     * get collapsed so the individual lines move to textLines / MessagingStyle messages.
     */
    private fun extractText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val parts = mutableListOf<String>()

        extras.getCharSequence("android.title")?.let { parts.add(it.toString()) }
        extras.getCharSequence("android.text")?.let { parts.add(it.toString()) }
        extras.getCharSequence("android.bigText")?.let { parts.add(it.toString()) }

        extras.getCharSequenceArray("android.textLines")?.forEach { line ->
            line?.let { parts.add(it.toString()) }
        }

        // MessagingStyle (Google Messages, etc.) stores each message as a Bundle here.
        val messages = extras.getParcelableArray("android.messages")
        messages?.forEach { msg ->
            if (msg is Bundle) {
                msg.getCharSequence("text")?.let { parts.add(it.toString()) }
                msg.getCharSequence("sender")?.let { parts.add(it.toString()) }
            }
        }

        return parts.joinToString(separator = "\n")
    }

    private fun addToHistory(context: Context, message: String) {
        val prefs = context.getSharedPreferences("filters_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("history_list", null)
        val array = if (json != null) JSONArray(json) else JSONArray()
        
        val obj = JSONObject()
        obj.put("message", message)
        obj.put("timestamp", System.currentTimeMillis())
        
        // Keep only last 50 items
        val newList = mutableListOf<JSONObject>()
        newList.add(obj)
        for (i in 0 until array.length()) {
            if (newList.size < 50) {
                newList.add(array.getJSONObject(i))
            }
        }
        
        val newArray = JSONArray()
        newList.forEach { newArray.put(it) }
        prefs.edit().putString("history_list", newArray.toString()).apply()
    }
}
