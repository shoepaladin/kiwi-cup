package com.kiwicup.scheduledmessenger.ui.thread

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.repository.ConversationStyleRepository
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.settings.AppSettings
import com.kiwicup.scheduledmessenger.data.settings.SettingsRepository
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
    val look: ThreadLook = ThreadLook()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsMessageDao: SmsMessageDao,
    private val scheduledMessages: ScheduledMessageRepository,
    private val reminders: ReminderRepository,
    private val styles: ConversationStyleRepository,
    settingsRepository: SettingsRepository,
    private val timeSource: TimeSource
) : ViewModel() {

    val threadId: Long = checkNotNull(savedStateHandle["threadId"])
    private val draft = MutableStateFlow("")
    private val snackbar = MutableStateFlow<String?>(null)

    private val messages: Flow<List<SmsMessage>> = smsMessageDao.observeThread(threadId)
    private val address: Flow<String> = messages.map { it.lastOrNull()?.address ?: "" }.distinctUntilChanged()

    private val look: Flow<ThreadLook> = combine(
        address.flatMapLatest { addr -> if (addr.isEmpty()) flowOf(null) else styles.observe(addr) },
        settingsRepository.settings
    ) { style, settings -> style to settings }
        .flatMapLatest { (style, settings) -> flowOf(buildLook(style, settings)) }

    val state: StateFlow<ThreadUiState> = combine(
        messages,
        reminders.observeActiveForThread(threadId),
        draft,
        snackbar,
        look
    ) { messages, active, draftText, message, look ->
        ThreadUiState(
            threadId = threadId,
            address = messages.lastOrNull()?.address ?: "",
            messages = messages,
            activeReminders = active,
            draft = draftText,
            snackbar = message,
            look = look
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState(threadId))

    private suspend fun buildLook(style: ConversationStyle?, settings: AppSettings): ThreadLook = ThreadLook(
        incomingBubbleColor = style?.incomingBubbleColor ?: settings.incomingBubbleColor,
        outgoingBubbleColor = style?.outgoingBubbleColor ?: settings.outgoingBubbleColor,
        wallpaper = style?.wallpaperPath?.let { decodeWallpaper(it) },
        wallpaperDimPercent = style?.wallpaperDimPercent ?: 30,
        style = style
    )

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
        scheduledMessages.validate(state.value.address, state.value.draft, millis)?.message

    fun sendNow() = schedule(timeSource.now(), "Sending…")
    fun scheduleAt(millis: Long) = schedule(millis, "Scheduled for ${TimeFormat.dateTime(millis)}")

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
