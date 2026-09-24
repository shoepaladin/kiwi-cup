package com.kiwicup.scheduledmessenger.data.system

/** A row this app wrote into the phone's SMS store. */
data class StoredSms(val systemId: Long, val threadId: Long)

/**
 * Writes into the phone's own message store. Only the default SMS app is allowed to write there,
 * so every method is a no-op (returns null) when we are not the default.
 */
interface SystemMessageStore {
    fun isDefaultSmsApp(): Boolean
    fun insertReceivedSms(address: String, body: String, timestamp: Long): StoredSms?
    fun insertSentSms(address: String, body: String, timestamp: Long): StoredSms?

    /**
     * The phone's thread id for [address], allocating one if the conversation is new; null when
     * we are not the default app and so cannot ask. Resolving this *before* sending is what lets
     * the compose screen open the conversation the message is about to land in.
     */
    fun threadIdFor(address: String): Long?
    /** Best-effort: marks a conversation read in the system store. */
    fun markThreadRead(threadId: Long)

    /**
     * Best-effort: sets one message's read flag in the system store, so the launcher badge and
     * other messaging apps agree with this one. A message with neither id exists only locally
     * (this app was not the default when it arrived) and has nothing to update.
     */
    fun setMessageRead(systemId: Long?, mmsSystemId: Long?, read: Boolean)
}
