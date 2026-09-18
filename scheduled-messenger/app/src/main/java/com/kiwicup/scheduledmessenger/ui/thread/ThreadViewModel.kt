package com.kiwicup.scheduledmessenger.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
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

data class ThreadUiState(
    val threadId: Long,
    val address: String = "",
    val messages: List<SmsMessage> = emptyList(),
    val activeReminders: List<Reminder> = emptyList(),
    val draft: String = "",
    val snackbar: String? = null
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsMessageDao: SmsMessageDao,
    private val scheduledMessages: ScheduledMessageRepository,
    private val reminders: ReminderRepository,
    private val timeSource: TimeSource
) : ViewModel() {

    val threadId: Long = checkNotNull(savedStateHandle["threadId"])
    private val draft = MutableStateFlow("")
    private val snackbar = MutableStateFlow<String?>(null)

    val state: StateFlow<ThreadUiState> = combine(
        smsMessageDao.observeThread(threadId),
        reminders.observeActiveForThread(threadId),
        draft,
        snackbar
    ) { messages, active, draftText, message ->
        ThreadUiState(
            threadId = threadId,
            address = messages.lastOrNull()?.address ?: "",
            messages = messages,
            activeReminders = active,
            draft = draftText,
            snackbar = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState(threadId))

    fun now(): Long = timeSource.now()
    fun onDraftChange(text: String) { draft.value = text }
    fun snackbarShown() { snackbar.value = null }

    fun validateTarget(millis: Long): String? =
        scheduledMessages.validate(state.value.address, state.value.draft, millis)?.message

    fun sendNow() = schedule(timeSource.now(), "Sending…")

    fun scheduleAt(millis: Long) = schedule(millis, "Scheduled for ${com.kiwicup.scheduledmessenger.ui.components.TimeFormat.dateTime(millis)}")

    private fun schedule(target: Long, successMessage: String) {
        val address = state.value.address
        val body = draft.value
        viewModelScope.launch {
            scheduledMessages.schedule(address, body, target, threadId)
                .onSuccess { draft.value = ""; snackbar.value = successMessage }
                .onFailure { snackbar.value = it.message ?: "Could not schedule" }
        }
    }

    fun remind(message: SmsMessage, text: String, triggerMillis: Long) {
        viewModelScope.launch {
            reminders.create(threadId, message.id, text, triggerMillis)
                .onSuccess { snackbar.value = "Reminder set for ${com.kiwicup.scheduledmessenger.ui.components.TimeFormat.dateTime(triggerMillis)}" }
                .onFailure { snackbar.value = it.message ?: "Could not set reminder" }
        }
    }
}
