package com.kiwicup.scheduledmessenger.data.inbox

import com.kiwicup.scheduledmessenger.core.RecipientValidator
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a phone number to a conversation.
 *
 * Two subtleties: the phone's store formats numbers freely ("+1 555-000-1111" vs "15550001111"),
 * so matching is done on normalised digits; and conversations that exist only locally (the app
 * is not the default and nothing has been imported for that number yet) get NEGATIVE thread ids,
 * so they can never collide with a thread id the phone allocates later.
 */
@Singleton
class ThreadResolver @Inject constructor(private val dao: SmsMessageDao) {

    /** Existing thread for [address], matched on normalised digits; null when none. */
    suspend fun find(address: String): Long? {
        val wanted = RecipientValidator.normalize(address)
        if (wanted.isEmpty()) return null
        dao.findThreadIdByAddress(address)?.let { return it }
        val tail = wanted.takeLast(TAIL_DIGITS)
        return dao.threadsForAddressTail("%$tail")
            .firstOrNull { sameNumber(it.address, wanted) }
            ?.threadId
    }

    /** Existing thread or a fresh local-only (negative) one. */
    suspend fun findOrCreate(address: String): Long = find(address) ?: dao.nextLocalThreadId()

    /** After an import, fold local-only rows for [address] into the phone's [systemThreadId]. */
    suspend fun mergeLocalInto(address: String, systemThreadId: Long) {
        val wanted = RecipientValidator.normalize(address)
        val tail = wanted.takeLast(TAIL_DIGITS)
        dao.threadsForAddressTail("%$tail")
            .filter { it.threadId < 0 && sameNumber(it.address, wanted) }
            .forEach { dao.reassignThread(it.threadId, systemThreadId) }
    }

    /** A local row (no system ids) matching this message, if the app already stored it itself. */
    suspend fun findUnsynced(address: String, body: String, timestamp: Long): com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage? {
        val wanted = RecipientValidator.normalize(address)
        val tail = wanted.takeLast(TAIL_DIGITS)
        return dao.findUnsyncedCandidates("%$tail", body, timestamp).firstOrNull { sameNumber(it.address, wanted) }
    }

    fun sameNumber(a: String, b: String): Boolean {
        val x = RecipientValidator.normalize(a).trimStart('+')
        val y = b.trimStart('+')
        if (x == y) return true
        // Tolerate a missing or present country code: compare the last 10 digits when both are long enough.
        return x.length >= 10 && y.length >= 10 && x.takeLast(10) == y.takeLast(10)
    }

    companion object {
        /** Short enough that formatting characters never sit inside the tail; exact match happens in Kotlin. */
        const val TAIL_DIGITS = 4
    }
}
