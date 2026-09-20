package com.kiwicup.scheduledmessenger.testing

import com.kiwicup.scheduledmessenger.data.system.StoredSms
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore

/** Simulates the phone's store: when "default", every insert gets a fresh system id and a thread id per address. */
class FakeSystemMessageStore(var isDefault: Boolean = false) : SystemMessageStore {
    data class Row(val address: String, val body: String, val timestamp: Long, val sent: Boolean)

    val rows = mutableListOf<Row>()
    val readThreads = mutableListOf<Long>()
    private var nextId = 1000L
    private val threads = mutableMapOf<String, Long>()

    override fun isDefaultSmsApp(): Boolean = isDefault

    override fun insertReceivedSms(address: String, body: String, timestamp: Long): StoredSms? = insert(address, body, timestamp, false)
    override fun insertSentSms(address: String, body: String, timestamp: Long): StoredSms? = insert(address, body, timestamp, true)
    override fun markThreadRead(threadId: Long) { readThreads += threadId }

    override fun threadIdFor(address: String): Long? =
        if (isDefault) threads.getOrPut(address) { 500L + threads.size } else null

    private fun insert(address: String, body: String, timestamp: Long, sent: Boolean): StoredSms? {
        if (!isDefault) return null
        rows += Row(address, body, timestamp, sent)
        val threadId = threads.getOrPut(address) { 500L + threads.size }
        return StoredSms(nextId++, threadId)
    }
}
