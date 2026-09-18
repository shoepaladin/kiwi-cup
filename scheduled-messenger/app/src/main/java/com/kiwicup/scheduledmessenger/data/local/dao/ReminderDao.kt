package com.kiwicup.scheduledmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders ORDER BY triggerTimestamp ASC, id ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimestamp ASC, id ASC")
    fun observeActive(): Flow<List<Reminder>>

    /** Snapshot used by the boot receiver to re-enqueue work. */
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimestamp ASC, id ASC")
    suspend fun getActive(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE threadId = :threadId AND isCompleted = 0 ORDER BY triggerTimestamp ASC")
    fun observeActiveForThread(threadId: Long): Flow<List<Reminder>>

    @Query("UPDATE reminders SET workRequestId = :workRequestId WHERE id = :id")
    suspend fun setWorkRequestId(id: Long, workRequestId: String?): Int

    /** Guarded so a reminder fires its notification at most once. Returns 1 on the first completion only. */
    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :id AND isCompleted = 0")
    suspend fun markCompleted(id: Long): Int

    @Query(
        "UPDATE reminders SET reminderText = :reminderText, triggerTimestamp = :triggerTimestamp, " +
            "isCompleted = 0, workRequestId = NULL WHERE id = :id"
    )
    suspend fun edit(id: Long, reminderText: String, triggerTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM reminders WHERE isCompleted = 0")
    suspend fun countActive(): Int

    @Query("DELETE FROM reminders WHERE isCompleted = 1")
    suspend fun clearCompleted(): Int
}
