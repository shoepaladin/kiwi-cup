package com.kiwicup.scheduledmessenger.data.repository

import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.work.SchedulerApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Entry point for "remind me about this later". */
@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: SchedulerApi,
    private val notifier: ReminderNotifier,
    private val timeSource: TimeSource
) {
    fun observeAll(): Flow<List<Reminder>> = dao.observeAll()
    fun observeActive(): Flow<List<Reminder>> = dao.observeActive()
    fun observeActiveForThread(threadId: Long): Flow<List<Reminder>> = dao.observeActiveForThread(threadId)
    suspend fun getById(id: Long): Reminder? = dao.getById(id)

    suspend fun create(threadId: Long, messageId: Long?, text: String, triggerTimestamp: Long): Result<Long> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Reminder text cannot be empty"))
        val id = dao.insert(
            Reminder(
                threadId = threadId,
                messageId = messageId,
                reminderText = text.trim(),
                triggerTimestamp = triggerTimestamp,
                createdAt = timeSource.now()
            )
        )
        arm(id, triggerTimestamp)
        return Result.success(id)
    }

    suspend fun edit(id: Long, text: String, triggerTimestamp: Long): Result<Unit> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Reminder text cannot be empty"))
        if (dao.edit(id, text.trim(), triggerTimestamp) != 1) return Result.failure(IllegalStateException("Reminder not found"))
        notifier.cancel(id)
        arm(id, triggerTimestamp)
        return Result.success(Unit)
    }

    /** Marks done without notifying (user dismissed it from the queue). */
    suspend fun complete(id: Long) {
        scheduler.cancelReminder(id)
        dao.markCompleted(id)
        notifier.cancel(id)
    }

    suspend fun delete(id: Long) {
        scheduler.cancelReminder(id)
        notifier.cancel(id)
        dao.deleteById(id)
    }

    suspend fun reenqueueAllActive(): Int {
        val active = dao.getActive()
        active.forEach { arm(it.id, it.triggerTimestamp, replace = false) }
        return active.size
    }

    suspend fun clearCompleted(): Int = dao.clearCompleted()

    private suspend fun arm(id: Long, triggerTimestamp: Long, replace: Boolean = true) {
        val workId = scheduler.scheduleReminder(id, triggerTimestamp, replace)
        dao.setWorkRequestId(id, workId.toString())
    }
}
