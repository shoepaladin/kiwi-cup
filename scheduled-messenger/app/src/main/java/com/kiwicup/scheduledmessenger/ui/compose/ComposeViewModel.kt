package com.kiwicup.scheduledmessenger.ui.compose

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat
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
    val done: String? = null
) {
    val isGroup: Boolean get() = Recipients.isGroup(recipient)
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val scheduledMessages: ScheduledMessageRepository,
    private val attachmentStore: AttachmentStore,
    private val timeSource: TimeSource
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeUiState())
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

    fun sendNow() = schedule(timeSource.now(), "Sending…")
    fun scheduleAt(millis: Long) = schedule(millis, "Scheduled for ${TimeFormat.dateTime(millis)}")

    private fun schedule(target: Long, doneMessage: String) {
        val s = _state.value
        viewModelScope.launch {
            scheduledMessages.schedule(s.recipient, s.body, target, attachments = s.attachments)
                .onSuccess { _state.update { it.copy(done = doneMessage) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
