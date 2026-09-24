package com.kiwicup.scheduledmessenger.ui.thread

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.ReadState
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.repository.ConversationStyleRepository
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.settings.SettingsRepository
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import com.kiwicup.scheduledmessenger.data.system.ContactNames
import com.kiwicup.scheduledmessenger.notifications.IncomingMessageNotifier
import com.kiwicup.scheduledmessenger.notifications.VisibleThread
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Effective bubble colors and wallpaper for the open conversation. */
data class ThreadLook(
    val incomingBubbleColor: Int? = null,
    val outgoingBubbleColor: Int? = null,
    val wallpaper: Bitmap? = null,
    val wallpaperDimPercent: Int = 30,
    val style: ConversationStyle? = null
)

data class ThreadUiState(
    val threadId: Long,
    val address: String = "",
    val messages: List<SmsMessage> = emptyList(),
    val activeReminders: List<Reminder> = emptyList(),
    val draft: String = "",
    val snackbar: String? = null,
    val look: ThreadLook = ThreadLook(),
    val attachments: List<Attachment> = emptyList(),
    /** Every other participant for a group conversation; empty for one-to-one. */
    val participants: List<String> = emptyList(),
    /** Contact name for the other party when known. */
    val contactName: String? = null,
    /**
     * The first message that was unread when the conversation was opened, where the "New" line
     * goes. Fixed for the visit: it records what was new on arrival, and does not move as
     * messages are read or marked unread afterward.
     */
    val firstUnreadId: Long? = null
) {
    val isGroup: Boolean get() = participants.size > 1
    val title: String get() = if (isGroup) participants.joinToString(", ") else contactName ?: address.ifEmpty { "Conversation" }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsMessageDao: SmsMessageDao,
    private val scheduledMessages: ScheduledMessageRepository,
    private val reminders: ReminderRepository,
    private val styles: ConversationStyleRepository,
    settingsRepository: SettingsRepository,
    private val attachmentStore: AttachmentStore,
    private val systemStore: SystemMessageStore,
    private val incomingNotifier: IncomingMessageNotifier,
    private val contacts: ContactNames,
    private val timeSource: TimeSource
) : ViewModel() {

    val threadId: Long = checkNotNull(savedStateHandle["threadId"])
    private val draft = MutableStateFlow("")
    private val snackbar = MutableStateFlow<String?>(null)
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())

    private val firstUnreadAtOpen = MutableStateFlow<Long?>(null)

    // Read-state bookkeeping for this visit. Touched only from the main thread (viewModelScope's
    // default dispatcher), so plain fields are safe.
    /** Highest message id when the visit started; anything above it arrived while open. */
    private var openedAtMaxId: Long = Long.MAX_VALUE
    /** Messages the user marked unread during this visit; never auto-read until the next visit. */
    private val markedUnreadThisVisit = mutableSetOf<Long>()
    private var visible = false

    init {
        // Opening a conversation counts as reading it. Record where the unread run starts first,
        // or the "New" line has nothing left to point at.
        viewModelScope.launch {
            runCatching {
                firstUnreadAtOpen.value = smsMessageDao.firstUnreadId(threadId)
                openedAtMaxId = smsMessageDao.maxIdInThread(threadId) ?: 0L
                smsMessageDao.markThreadRead(threadId)
            }
            withContext(Dispatchers.IO) {
                runCatching { systemStore.markThreadRead(threadId) }
                runCatching { incomingNotifier.cancelForThread(threadId) }
            }
            if (visible) markArrivalsRead()
        }
        // Texts that land while the conversation is actually on screen are read as they arrive.
        viewModelScope.launch {
            smsMessageDao.observeThread(threadId).collect { if (visible) markArrivalsRead() }
        }
    }

    /**
     * Called as the screen resumes and pauses. This is what "the user is looking at it" means —
     * not "the ViewModel exists", which stays true while the app sits in the background. Tying
     * VisibleThread to the ViewModel's lifetime used to suppress notifications for the last
     * conversation opened even after the user had left the app.
     */
    fun onScreenResumed() {
        visible = true
        VisibleThread.current = threadId
        viewModelScope.launch { markArrivalsRead() }
    }

    fun onScreenPaused() {
        visible = false
        if (VisibleThread.current == threadId) VisibleThread.current = null
    }

    override fun onCleared() {
        if (VisibleThread.current == threadId) VisibleThread.current = null
        super.onCleared()
    }

    /** Marks read what arrived during this visit, sparing anything the user marked unread. */
    private suspend fun markArrivalsRead() {
        val ids = ReadState.arrivalsToMarkRead(
            runCatching { smsMessageDao.unreadIds(threadId) }.getOrDefault(emptyList()),
            openedAtMaxId,
            markedUnreadThisVisit
        )
        if (ids.isEmpty()) return
        runCatching { smsMessageDao.markRead(ids) }
        val rows = ids.mapNotNull { runCatching { smsMessageDao.getById(it) }.getOrNull() }
        withContext(Dispatchers.IO) {
            rows.forEach { runCatching { systemStore.setMessageRead(it.systemId, it.mmsSystemId, read = true) } }
        }
    }

    /**
     * Long-press "Mark unread" / "Mark read" on one message. Only that message changes — the
     * conversation counts as unread while any of its messages is — and it stays that way through
     * this visit, clearing the next time the conversation is opened.
     */
    fun toggleRead(message: SmsMessage) {
        val nowRead = !message.isRead
        if (nowRead) markedUnreadThisVisit -= message.id else markedUnreadThisVisit += message.id
        viewModelScope.launch {
            runCatching { smsMessageDao.setRead(message.id, nowRead) }
                .onFailure { snackbar.value = "Could not update that message"; return@launch }
            withContext(Dispatchers.IO) {
                runCatching { systemStore.setMessageRead(message.systemId, message.mmsSystemId, nowRead) }
            }
            snackbar.value = if (nowRead) "Marked read" else "Marked unread"
        }
    }

    private val messages: Flow<List<SmsMessage>> = smsMessageDao.observeThread(threadId)
    private val address: Flow<String> = messages.map { it.lastOrNull()?.address ?: "" }.distinctUntilChanged()

    // The wallpaper is decoded only when the style row changes; settings changes just re-pick colors.
    private val styleWithWallpaper: Flow<Pair<ConversationStyle?, Bitmap?>> = address
        .flatMapLatest { addr -> if (addr.isEmpty()) flowOf(null) else styles.observe(addr) }
        .distinctUntilChanged()
        .map { style -> style to style?.wallpaperPath?.let { decodeWallpaper(it) } }

    private val look: Flow<ThreadLook> = combine(styleWithWallpaper, settingsRepository.settings) { (style, bitmap), settings ->
        ThreadLook(
            incomingBubbleColor = style?.incomingBubbleColor ?: settings.incomingBubbleColor,
            outgoingBubbleColor = style?.outgoingBubbleColor ?: settings.outgoingBubbleColor,
            wallpaper = bitmap,
            wallpaperDimPercent = style?.wallpaperDimPercent ?: 30,
            style = style
        )
    }

    private val composer: Flow<Pair<String, List<Attachment>>> = combine(draft, attachments) { d, a -> d to a }

    // combine() takes at most five typed flows; the "New" line position rides along with look.
    private val lookAndUnread: Flow<Pair<ThreadLook, Long?>> = combine(look, firstUnreadAtOpen) { l, f -> l to f }

    val state: StateFlow<ThreadUiState> = combine(
        messages,
        reminders.observeActiveForThread(threadId),
        composer,
        snackbar,
        lookAndUnread
    ) { messages, active, (draftText, pending), message, (look, firstUnread) ->
        val latest = messages.lastOrNull()
        ThreadUiState(
            threadId = threadId,
            address = latest?.address ?: "",
            messages = messages,
            activeReminders = active,
            draft = draftText,
            snackbar = message,
            look = look,
            attachments = pending,
            participants = Recipients.decode(latest?.recipients),
            contactName = latest?.address?.let { contacts.displayName(it) }?.takeIf { it != latest.address },
            firstUnreadId = firstUnread
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState(threadId))

    private suspend fun decodeWallpaper(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun now(): Long = timeSource.now()
    fun onDraftChange(text: String) { draft.value = text }
    fun snackbarShown() { snackbar.value = null }

    fun validateTarget(millis: Long): String? =
        scheduledMessages.validate(recipientList(), state.value.draft, millis, attachments.value)?.message

    /** Group conversations reply to everyone; one-to-one replies to the other party. */
    private fun recipientList(): String {
        val s = state.value
        return if (s.isGroup) Recipients.encode(s.participants) else s.address
    }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            val stored = attachmentStore.persist(uri)
            if (stored == null) snackbar.value = "Could not read that file" else attachments.value = attachments.value + stored
        }
    }

    fun removeAttachment(attachment: Attachment) {
        attachments.value = attachments.value - attachment
        attachmentStore.delete(listOf(attachment))
    }

    fun sendNow() = schedule(timeSource.now(), "Sending…")
    fun scheduleAt(millis: Long) = schedule(millis, "Scheduled for ${TimeFormat.dateTime(millis)}")

    private fun schedule(target: Long, successMessage: String) {
        val recipients = recipientList()
        val body = draft.value
        val media = attachments.value
        viewModelScope.launch {
            scheduledMessages.schedule(recipients, body, target, threadId, media)
                .onSuccess { draft.value = ""; attachments.value = emptyList(); snackbar.value = successMessage }
                .onFailure { snackbar.value = it.message ?: "Could not schedule" }
        }
    }

    fun remind(message: SmsMessage, text: String, triggerMillis: Long) {
        viewModelScope.launch {
            reminders.create(threadId, message.id, text, triggerMillis)
                .onSuccess { snackbar.value = "Reminder set for ${TimeFormat.dateTime(triggerMillis)}" }
                .onFailure { snackbar.value = it.message ?: "Could not set reminder" }
        }
    }

    // ---- per-conversation style ----
    fun setBubbleColors(incoming: Int?, outgoing: Int?) = withAddress { styles.setBubbleColors(it, incoming, outgoing) }
    fun setWallpaper(uri: Uri) = withAddress { if (!styles.setWallpaper(it, uri)) snackbar.value = "Could not read that photo" }
    fun clearWallpaper() = withAddress { styles.clearWallpaper(it) }
    fun setWallpaperDim(percent: Int) = withAddress { styles.setWallpaperDim(it, percent) }
    fun resetStyle() = withAddress { styles.reset(it) }

    private fun withAddress(block: suspend (String) -> Unit) {
        val addr = state.value.address
        if (addr.isEmpty()) return
        viewModelScope.launch { block(addr) }
    }
}
