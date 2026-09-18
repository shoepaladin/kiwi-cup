package com.kiwicup.scheduledmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import kotlinx.coroutines.flow.Flow

/**
 * All status changes go through guarded UPDATE statements that check the *current* status in
 * the WHERE clause and return the number of rows touched. SQLite runs each statement atomically,
 * so two actors (worker vs. user cancel) can never both "win": exactly one sees 1 row updated.
 */
@Dao
interface ScheduledMessageDao {

    @Insert
    suspend fun insert(message: ScheduledMessage): Long

    @Update
    suspend fun update(message: ScheduledMessage)

    @Delete
    suspend fun delete(message: ScheduledMessage)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getById(id: Long): ScheduledMessage?

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    fun observeById(id: Long): Flow<ScheduledMessage?>

    @Query("SELECT * FROM scheduled_messages ORDER BY targetTimestamp ASC, id ASC")
    fun observeAll(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY targetTimestamp ASC, id ASC")
    fun observePending(): Flow<List<ScheduledMessage>>

    /** Snapshot used by the boot receiver to re-enqueue work. */
    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY targetTimestamp ASC, id ASC")
    suspend fun getPending(): List<ScheduledMessage>

    @Query(
        "SELECT * FROM scheduled_messages WHERE status = 'PENDING' AND targetTimestamp <= :nowMillis " +
            "ORDER BY targetTimestamp ASC, id ASC"
    )
    suspend fun getDue(nowMillis: Long): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled_messages WHERE status IN ('SENT', 'FAILED', 'CANCELLED') ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<ScheduledMessage>>

    @Query("UPDATE scheduled_messages SET workRequestId = :workRequestId, updatedAt = :nowMillis WHERE id = :id")
    suspend fun setWorkRequestId(id: Long, workRequestId: String?, nowMillis: Long = System.currentTimeMillis()): Int

    /** PENDING -> SENDING. Returns 1 when this caller now owns the dispatch, 0 otherwise. */
    @Query(
        "UPDATE scheduled_messages SET status = 'SENDING', updatedAt = :nowMillis " +
            "WHERE id = :id AND status = 'PENDING'"
    )
    suspend fun claimForSending(id: Long, nowMillis: Long = System.currentTimeMillis()): Int

    /** SENDING -> SENT. */
    @Query(
        "UPDATE scheduled_messages SET status = 'SENT', failureReason = NULL, updatedAt = :nowMillis " +
            "WHERE id = :id AND status = 'SENDING'"
    )
    suspend fun markSent(id: Long, nowMillis: Long = System.currentTimeMillis()): Int

    /** PENDING or SENDING -> FAILED with a reason. */
    @Query(
        "UPDATE scheduled_messages SET status = 'FAILED', failureReason = :reason, updatedAt = :nowMillis " +
            "WHERE id = :id AND status IN ('PENDING', 'SENDING')"
    )
    suspend fun markFailed(id: Long, reason: String, nowMillis: Long = System.currentTimeMillis()): Int

    /** SENDING -> PENDING, used when the worker hits a transient error and asks WorkManager to retry. */
    @Query(
        "UPDATE scheduled_messages SET status = 'PENDING', updatedAt = :nowMillis " +
            "WHERE id = :id AND status = 'SENDING'"
    )
    suspend fun releaseClaim(id: Long, nowMillis: Long = System.currentTimeMillis()): Int

    /** PENDING -> CANCELLED. Fails (0 rows) once a worker has claimed the row. */
    @Query(
        "UPDATE scheduled_messages SET status = 'CANCELLED', updatedAt = :nowMillis " +
            "WHERE id = :id AND status = 'PENDING'"
    )
    suspend fun cancel(id: Long, nowMillis: Long = System.currentTimeMillis()): Int

    /** FAILED or CANCELLED -> PENDING with a new target time (user re-schedules from the queue). */
    @Query(
        "UPDATE scheduled_messages SET status = 'PENDING', targetTimestamp = :newTargetTimestamp, " +
            "failureReason = NULL, workRequestId = NULL, updatedAt = :nowMillis " +
            "WHERE id = :id AND status IN ('FAILED', 'CANCELLED')"
    )
    suspend fun reschedule(id: Long, newTargetTimestamp: Long, nowMillis: Long = System.currentTimeMillis()): Int

    /** Edit body / recipient / time while still PENDING (attachments are kept). */
    @Query(
        "UPDATE scheduled_messages SET recipientAddress = :recipientAddress, messageBody = :messageBody, " +
            "targetTimestamp = :targetTimestamp, updatedAt = :nowMillis WHERE id = :id AND status = 'PENDING'"
    )
    suspend fun editPending(
        id: Long,
        recipientAddress: String,
        messageBody: String,
        targetTimestamp: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE status = :status")
    suspend fun countByStatus(status: MessageStatus): Int

    @Query("DELETE FROM scheduled_messages WHERE status IN ('SENT', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory(): Int
}
