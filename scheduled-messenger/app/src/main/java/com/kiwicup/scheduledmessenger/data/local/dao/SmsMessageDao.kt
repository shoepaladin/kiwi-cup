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
    val messageCount: Int
)

@Dao
interface SmsMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<SmsMessage>): List<Long>

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
        SELECT threadId, address, body, timestamp, COUNT(*) AS messageCount
        FROM sms_messages
        WHERE id IN (
            SELECT id FROM sms_messages m
            WHERE timestamp = (SELECT MAX(timestamp) FROM sms_messages WHERE threadId = m.threadId)
        )
        GROUP BY threadId
        ORDER BY timestamp DESC
        """
    )
    fun observeThreadSummaries(): Flow<List<ThreadSummary>>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE threadId = :threadId")
    suspend fun countInThread(threadId: Long): Int

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long): Int

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAll()
}
