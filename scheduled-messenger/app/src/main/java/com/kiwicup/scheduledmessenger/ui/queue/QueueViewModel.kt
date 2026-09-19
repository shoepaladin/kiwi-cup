package com.kiwicup.scheduledmessenger.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.core.belongsInSchedule
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the timeline: either a scheduled text or a reminder. */
sealed class QueueItem {
    abstract val whenMillis: Long
    abstract val key: String

    data class Message(val message: ScheduledMessage) : QueueItem() {
        override val whenMillis get() = message.targetTimestamp
        override val key get() = "m${message.id}"
    }

    data class Note(val reminder: Reminder) : QueueItem() {
        override val whenMillis get() = reminder.triggerTimestamp
        override val key get() = "r${reminder.id}"
    }
}

data class QueueUiState(
    val upcoming: List<QueueItem> = emptyList(),
    val history: List<QueueItem> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val scheduledMessages: ScheduledMessageRepository,
    private val reminders: ReminderRepository,
    private val timeSource: TimeSource
) : ViewModel() {

    private val snackbar = MutableStateFlow<String?>(null)

    val state: StateFlow<QueueUiState> = combine(
        scheduledMessages.observeAll(),
        reminders.observeAll(),
        snackbar
    ) { messages, notes, message ->
        // Send-now shares this table, so filter it out: a text sent immediately belongs in its
        // conversation, not in the schedule, and listing it here made an ordinary message look
        // like a queued one. A failed one still shows, since nothing else surfaces it yet.
        val scheduled = messages.filter {
            belongsInSchedule(it.targetTimestamp, it.createdAt, it.status == MessageStatus.FAILED)
        }
        val items = scheduled.map { QueueItem.Message(it) } + notes.map { QueueItem.Note(it) }
        val (upcoming, history) = items.partition { it.isOpen() }
        QueueUiState(
            upcoming = upcoming.sortedBy { it.whenMillis },
            history = history.sortedByDescending { it.whenMillis },
            snackbar = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState())

    fun now(): Long = timeSource.now()
    fun snackbarShown() { snackbar.value = null }

    fun cancelMessage(id: Long) = launch {
        snackbar.value = if (scheduledMessages.cancel(id)) "Cancelled" else "Too late, it is already sending"
    }

    fun deleteMessage(id: Long) = launch { scheduledMessages.delete(id); snackbar.value = "Deleted" }

    fun editMessage(id: Long, recipient: String, body: String, target: Long) = launch {
        scheduledMessages.edit(id, recipient, body, target)
            .onSuccess { snackbar.value = "Updated" }
            .onFailure { snackbar.value = it.message ?: "Could not update" }
    }

    fun rescheduleMessage(id: Long, target: Long) = launch {
        scheduledMessages.reschedule(id, target)
            .onSuccess { snackbar.value = "Back in the queue" }
            .onFailure { snackbar.value = it.message ?: "Could not reschedule" }
    }

    fun validateTargetOnly(millis: Long): String? =
        if (millis < timeSource.now() - 60_000L) "Pick a time in the future" else null

    fun completeReminder(id: Long) = launch { reminders.complete(id); snackbar.value = "Reminder dismissed" }
    fun deleteReminder(id: Long) = launch { reminders.delete(id); snackbar.value = "Deleted" }

    fun editReminder(id: Long, text: String, trigger: Long) = launch {
        reminders.edit(id, text, trigger)
            .onSuccess { snackbar.value = "Updated" }
            .onFailure { snackbar.value = it.message ?: "Could not update" }
    }

    fun clearHistory() = launch {
        scheduledMessages.clearHistory()
        reminders.clearCompleted()
        snackbar.value = "History cleared"
    }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    companion object {
        fun QueueItem.isOpen(): Boolean = when (this) {
            is QueueItem.Message -> message.status == MessageStatus.PENDING || message.status == MessageStatus.SENDING
            is QueueItem.Note -> !reminder.isCompleted
        }
    }
}
