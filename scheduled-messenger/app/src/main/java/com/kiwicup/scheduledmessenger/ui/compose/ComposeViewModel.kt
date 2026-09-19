package com.kiwicup.scheduledmessenger.ui.compose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.SendOutcome
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeUiState(
    /** One number, or several separated by commas for a group. */
    val recipient: String = "",
    val body: String = "",
    val attachments: List<Attachment> = emptyList(),
    val error: String? = null,
    /** Set once the message is accepted; which value decides where the user is taken next. */
    val done: SendOutcome? = null
) {
    val isGroup: Boolean get() = Recipients.isGroup(recipient)
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduledMessages: ScheduledMessageRepository,
    private val attachmentStore: AttachmentStore,
    private val timeSource: TimeSource
) : ViewModel() {

    private val _state = MutableStateFlow(
        ComposeUiState(
            recipient = savedStateHandle.get<String>("to").orEmpty(),
            body = savedStateHandle.get<String>("body").orEmpty()
        )
    )
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    fun now(): Long = timeSource.now()
    fun onRecipientChange(value: String) = _state.update { it.copy(recipient = value, error = null) }
    fun onBodyChange(value: String) = _state.update { it.copy(body = value, error = null) }

    fun validateTarget(millis: Long): String? =
        scheduledMessages.validate(_state.value.recipient, _state.value.body, millis, _state.value.attachments)?.message

    fun attach(uri: Uri) {
        viewModelScope.launch {
            val stored = attachmentStore.persist(uri)
            if (stored == null) _state.update { it.copy(error = "Could not read that file") }
            else _state.update { it.copy(attachments = it.attachments + stored) }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        _state.update { it.copy(attachments = it.attachments - attachment) }
        attachmentStore.delete(listOf(attachment))
    }

    fun sendNow() = submit(timeSource.now(), SendOutcome.SENT_NOW)
    fun scheduleAt(millis: Long) = submit(millis, SendOutcome.SCHEDULED)

    /**
     * Both outcomes share the scheduling machinery, which is what carries the claim-once
     * guarantee and the retry policy, but they are reported back distinctly so the caller can
     * send the user to the conversation rather than to the schedule.
     */
    private fun submit(target: Long, outcome: SendOutcome) {
        val s = _state.value
        viewModelScope.launch {
            scheduledMessages.schedule(s.recipient, s.body, target, attachments = s.attachments)
                .onSuccess { _state.update { it.copy(done = outcome) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
