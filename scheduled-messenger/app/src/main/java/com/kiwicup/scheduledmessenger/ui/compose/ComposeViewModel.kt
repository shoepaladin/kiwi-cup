package com.kiwicup.scheduledmessenger.ui.compose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.Contact
import com.kiwicup.scheduledmessenger.core.ContactSuggestion
import com.kiwicup.scheduledmessenger.core.ContactSuggestions
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.RecipientSelection
import com.kiwicup.scheduledmessenger.core.RecipientSelections
import com.kiwicup.scheduledmessenger.core.SendOutcome
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.data.system.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A confirmed recipient, rendered as a removable chip. */
data class RecipientChip(val address: String, val displayName: String?) {
    val label: String get() = displayName ?: address
}

data class ComposeUiState(
    /** Confirmed recipients, comma-joined — the canonical value used for validation and sending. */
    val recipient: String = "",
    /** Contact names for entries in [recipient] that came from a picked contact. */
    val recipientNames: Map<String, String> = emptyMap(),
    /** What is currently typed but not yet confirmed as a chip. */
    val recipientQuery: String = "",
    /** Matching contacts, plus a synthetic entry when the query itself looks like a number. */
    val suggestions: List<ContactSuggestion> = emptyList(),
    val body: String = "",
    val attachments: List<Attachment> = emptyList(),
    val error: String? = null,
    /** Set once the message is accepted; which value decides where the user is taken next. */
    val done: SendOutcome? = null
) {
    val isGroup: Boolean get() = Recipients.isGroup(recipient)
    val recipientChips: List<RecipientChip> get() = Recipients.decode(recipient).map { RecipientChip(it, recipientNames[it]) }
}

/** Just the slice of state RecipientSelections' pure functions operate on. */
private val ComposeUiState.selection: RecipientSelection get() = RecipientSelection(recipient, recipientNames)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduledMessages: ScheduledMessageRepository,
    private val attachmentStore: AttachmentStore,
    private val timeSource: TimeSource,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        ComposeUiState(
            recipient = savedStateHandle.get<String>("to").orEmpty(),
            body = savedStateHandle.get<String>("body").orEmpty()
        )
    )
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    // Loaded once and held for the life of the screen; see ContactsRepository for why a bulk
    // load beats querying the provider on every keystroke.
    private var contacts: List<Contact> = emptyList()
    private var defaultRegion: String = "US"

    init {
        viewModelScope.launch {
            contacts = contactsRepository.contacts()
            defaultRegion = contactsRepository.defaultRegion()
            refreshSuggestions()
        }
    }

    fun now(): Long = timeSource.now()

    fun onRecipientQueryChange(text: String) {
        _state.update { it.copy(recipientQuery = text, error = null) }
        refreshSuggestions()
    }

    fun onPickSuggestion(suggestion: ContactSuggestion) {
        _state.update { s ->
            val updated = RecipientSelections.add(s.selection, suggestion)
            s.copy(
                recipient = updated.recipient,
                recipientNames = updated.recipientNames,
                recipientQuery = "",
                suggestions = emptyList(),
                error = null
            )
        }
    }

    fun onRemoveChip(address: String) {
        _state.update { s ->
            val updated = RecipientSelections.remove(s.selection, address)
            s.copy(recipient = updated.recipient, recipientNames = updated.recipientNames)
        }
        // A contact removed here may now belong back in an already-open suggestion list, since it
        // was only excluded for being already selected.
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val s = _state.value
        val selected = Recipients.decode(s.recipient).toSet()
        val suggestions = if (s.recipientQuery.isBlank()) {
            emptyList()
        } else {
            ContactSuggestions.forQuery(contacts, s.recipientQuery, selected, defaultRegion)
        }
        _state.update { it.copy(suggestions = suggestions) }
    }

    fun onBodyChange(value: String) = _state.update { it.copy(body = value, error = null) }

    fun validateTarget(millis: Long): String? =
        scheduledMessages.validate(effectiveRecipient(), _state.value.body, millis, _state.value.attachments)?.message

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

    private fun effectiveRecipient(): String {
        val s = _state.value
        return RecipientSelections.effectiveRecipient(s.selection, s.recipientQuery)
    }

    /**
     * Both outcomes share the scheduling machinery, which is what carries the claim-once
     * guarantee and the retry policy, but they are reported back distinctly so the caller can
     * send the user to the conversation rather than to the schedule.
     */
    private fun submit(target: Long, outcome: SendOutcome) {
        val recipient = effectiveRecipient()
        val s = _state.value
        viewModelScope.launch {
            scheduledMessages.schedule(recipient, s.body, target, attachments = s.attachments)
                .onSuccess { _state.update { it.copy(done = outcome) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
