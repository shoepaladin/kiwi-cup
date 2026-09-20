package com.kiwicup.scheduledmessenger.ui.compose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.ContactIndex
import com.kiwicup.scheduledmessenger.core.ContactSuggestion
import com.kiwicup.scheduledmessenger.core.ContactSuggestions
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.RecipientSelection
import com.kiwicup.scheduledmessenger.core.RecentConversation
import com.kiwicup.scheduledmessenger.core.RecipientSelections
import com.kiwicup.scheduledmessenger.core.SendOutcome
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.inbox.ThreadTargets
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.data.system.PlatformPhoneNumberRecognizer
import com.kiwicup.scheduledmessenger.data.system.ContactsRepository
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val done: SendOutcome? = null,
    /** The conversation a sent message landed in, when one could be resolved; see [ThreadTargets]. */
    val doneThreadId: Long? = null
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
    private val contactsRepository: ContactsRepository,
    private val phoneNumbers: PlatformPhoneNumberRecognizer,
    private val threadTargets: ThreadTargets,
    private val smsMessageDao: SmsMessageDao
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
    @Volatile private var contacts: ContactIndex = ContactIndex.EMPTY
    @Volatile private var recents: List<RecentConversation> = emptyList()
    @Volatile private var defaultRegion: String = "US"

    init {
        AppLog.d(TAG, "init: opening compose screen")
        viewModelScope.launch {
            // Contact search is a convenience on top of composing a message, not a precondition
            // for it — a failure loading contacts should degrade to "no suggestions," never take
            // the whole screen down with it, which a bare (uncaught) exception here would do.
            try {
                contacts = contactsRepository.contacts()
                defaultRegion = contactsRepository.defaultRegion()
                recents = loadRecents()
                AppLog.d(TAG, "init: loaded ${contacts.entries.size} contacts, ${recents.size} recents, region=$defaultRegion")
                refreshSuggestions()
            } catch (e: Exception) {
                AppLog.e(TAG, "init: contact/recents load failed, continuing with no suggestions", e)
            }
        }
    }

    /** The conversations this phone has had most recently, to fill an empty recipient field. */
    private suspend fun loadRecents(): List<RecentConversation> =
        runCatching {
            smsMessageDao.observeThreadSummaries().first()
                .filter { !it.title.contains(',') }
                .map { RecentConversation(address = it.address, displayName = null) }
        }.getOrDefault(emptyList())

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
        refreshSuggestions()
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

    /**
     * Rebuilds the dropdown off the main thread.
     *
     * Filtering scans every contact, and picking a suggestion is a touch event away, so a few
     * thousand entries is enough to drop frames while someone types. QKSMS does this work on its
     * computation scheduler for the same reason. The result is discarded if the query moved on
     * while it ran, so a slow pass can never overwrite a newer one.
     */
    private fun refreshSuggestions() {
        val query = _state.value.recipientQuery
        val selected = Recipients.decode(_state.value.recipient).toSet()
        viewModelScope.launch {
            val suggestions = try {
                withContext(Dispatchers.Default) {
                    if (query.isBlank()) {
                        // Only while the field is still empty-handed. Once a recipient is chosen the
                        // screen's job is the message, and a standing list would sit on top of it.
                        if (selected.isEmpty()) ContactSuggestions.forEmptyField(recents, contacts, selected) else emptyList()
                    } else {
                        ContactSuggestions.forQuery(contacts, phoneNumbers, query, selected, defaultRegion)
                    }
                }
            } catch (e: Exception) {
                // The dropdown is a convenience; a bug in it must never take the compose screen
                // down with it. This is the same trade as the try/catch around the initial load.
                AppLog.e(TAG, "refreshSuggestions failed for a query of length ${query.length}", e)
                emptyList()
            }
            _state.update { if (it.recipientQuery == query) it.copy(suggestions = suggestions) else it }
        }
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
            // Resolved before sending, not after: the send itself is handed to WorkManager and
            // the user should not be left waiting on it to find out where they are going.
            val threadId = runCatching { threadTargets.forRecipients(recipient) }.getOrNull()
            scheduledMessages.schedule(recipient, s.body, target, threadId = threadId, attachments = s.attachments)
                .onSuccess { _state.update { it.copy(done = outcome, doneThreadId = threadId) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    private companion object {
        const val TAG = "ComposeViewModel"
    }
}
