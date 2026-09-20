package com.kiwicup.scheduledmessenger.data.inbox

import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out which conversation a message is about to land in, before it is sent.
 *
 * QKSMS calls `getOrCreateThreadId` up front for the same reason: sending a text should leave you
 * in the conversation you just sent to, and you cannot navigate there without knowing its id. When
 * this app is the default, the phone's own thread id is the answer, and it is the same id
 * [SentMessageRecorder] will later record against, so the screen does not move under the user.
 * When it is not the default, the local (negative) id serves, and the recorder prefers the id
 * handed to it, so the two agree there too.
 *
 * Group messages go out as MMS and are imported rather than recorded, so their thread id is not
 * knowable in advance; those return null and the caller falls back to the conversation list.
 */
@Singleton
class ThreadTargets @Inject constructor(
    private val systemStore: SystemMessageStore,
    private val threads: ThreadResolver
) {
    suspend fun forRecipients(recipient: String): Long? {
        val addresses = Recipients.decode(recipient)
        val address = addresses.singleOrNull() ?: return null
        return systemStore.threadIdFor(address) ?: threads.findOrCreate(address)
    }
}
