package com.kiwicup.scheduledmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import kotlinx.coroutines.flow.Flow

/** Newest message of each thread, used to render the conversation list. */
data class ThreadSummary(
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val messageCount: Int,
    val recipients: String? = null,
    val attachments: String? = null
) {
    /** Group conversations list every participant; one-to-one shows the other party. */
    val title: String
        get() = recipients?.takeIf { it.contains(',') }?.replace(",", ", ") ?: address

    val preview: String
        get() = body.ifBlank { if (attachments.isNullOrBlank()) "" else "\uD83D\uDCF7 Picture" }
}

@Dao
interface SmsMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<SmsMessage>): List<Long>

    /** Import path: rows whose systemId already exists are skipped (returns -1 for those). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(messages: List<SmsMessage>): List<Long>

    @Query("SELECT MAX(systemId) FROM sms_messages")
    suspend fun maxSystemId(): Long?

    @Query("SELECT MAX(mmsSystemId) FROM sms_messages")
    suspend fun maxMmsSystemId(): Long?

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestInThread(threadId: Long): SmsMessage?

    @Query("SELECT * FROM sms_messages WHERE mmsSystemId = :mmsSystemId")
    suspend fun findByMmsSystemId(mmsSystemId: Long): SmsMessage?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM sms_messages WHERE address = :address AND body = :body " +
            "AND ABS(timestamp - :timestamp) < 5000)"
    )
    suspend fun existsUnsynced(address: String, body: String, timestamp: Long): Boolean

    @Update
    suspend fun update(message: SmsMessage)

    @Delete
    suspend fun delete(message: SmsMessage)

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: Long): SmsMessage?

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun observeThread(threadId: Long): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    suspend fun getThread(threadId: Long): List<SmsMessage>

    @Query(
        """
        SELECT m.threadId AS threadId, m.address AS address, m.body AS body, m.timestamp AS timestamp,
               (SELECT COUNT(*) FROM sms_messages c WHERE c.threadId = m.threadId) AS messageCount,
               m.recipients AS recipients, m.attachments AS attachments
        FROM sms_messages m
        WHERE m.id = (
            SELECT x.id FROM sms_messages x
            WHERE x.threadId = m.threadId
            ORDER BY x.timestamp DESC, x.id DESC
            LIMIT 1
        )
        ORDER BY m.timestamp DESC, m.id DESC
        """
    )
    fun observeThreadSummaries(): Flow<List<ThreadSummary>>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE threadId = :threadId")
    suspend fun countInThread(threadId: Long): Int

    @Query("SELECT threadId FROM sms_messages WHERE address = :address ORDER BY timestamp DESC LIMIT 1")
    suspend fun findThreadIdByAddress(address: String): Long?

    /** Next unused thread id for a brand-new conversation. */
    @Query("SELECT COALESCE(MAX(threadId), 0) + 1 FROM sms_messages")
    suspend fun nextThreadId(): Long

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long): Int

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAll()
}
