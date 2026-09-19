package com.kiwicup.scheduledmessenger.ui.thread

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.shouldScrollToLatest
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.ui.components.ConversationStyleDialog
import com.kiwicup.scheduledmessenger.ui.components.MessageBubble
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar
import com.kiwicup.scheduledmessenger.ui.components.ReminderDialog
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat

/** Style callbacks, grouped so the screen signature stays readable. */
data class ThreadStyleActions(
    val onBubbleColors: (incoming: Int?, outgoing: Int?) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onClearWallpaper: () -> Unit,
    val onDim: (Int) -> Unit,
    val onReset: () -> Unit
) {
    companion object {
        val None = ThreadStyleActions({ _, _ -> }, {}, {}, {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: ThreadUiState,
    nowMillis: () -> Long,
    onDraftChange: (String) -> Unit,
    onSendNow: () -> Unit,
    onSchedule: (Long) -> Unit,
    validateTarget: (Long) -> String?,
    onRemind: (SmsMessage, String, Long) -> Unit,
    onSnackbarShown: () -> Unit,
    onBack: () -> Unit,
    styleActions: ThreadStyleActions = ThreadStyleActions.None,
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: (Attachment) -> Unit = {}
) {
    val snackbarHost = remember { SnackbarHostState() }
    var remindTarget by remember { mutableStateOf<SmsMessage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var styleOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            onSnackbarShown()
        }
    }
    // Set when the user sends, consumed when the sent message arrives: a send should land in view
    // even if they had scrolled back through the history.
    var sendRequestedScroll by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        if (shouldScrollToLatest(listState.firstVisibleItemIndex, sendRequestedScroll)) {
            sendRequestedScroll = false
            // Index 0 is the newest message; the list is laid out in reverse.
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        // Without this the keyboard draws over the input bar: enableEdgeToEdge() means the window
        // no longer resizes itself, so the IME inset has to be consumed here instead.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(state.title, modifier = Modifier.testTag("thread_title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("thread_menu")) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Style this conversation") },
                            onClick = { menuOpen = false; styleOpen = true },
                            modifier = Modifier.testTag("menu_style")
                        )
                    }
                }
            )
        },
        // No snackbarHost slot: Scaffold anchors it to the bottom, directly over the newest
        // messages and the input bar. It is placed at the top of the content instead, where it
        // covers the app bar rather than the conversation.
        bottomBar = {
            MessageInputBar(
                text = state.draft,
                onTextChange = onDraftChange,
                onSendNow = {
                    sendRequestedScroll = true
                    onSendNow()
                },
                onSchedule = onSchedule,
                nowMillis = nowMillis,
                validateTarget = validateTarget,
                enabled = state.address.isNotEmpty(),
                attachments = state.attachments,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            state.look.wallpaper?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wallpaper")
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = state.look.wallpaperDimPercent / 100f))
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.activeReminders.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.activeReminders.joinToString("\n") { "⏰ ${it.reminderText} · ${TimeFormat.dateTime(it.triggerTimestamp)}" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("reminder_banner")
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    // Bottom-anchored by construction, the Compose equivalent of a RecyclerView's
                    // stackFromEnd. It keeps the newest message in view when the keyboard opens
                    // and shrinks the list, which a top-anchored list would scroll off the bottom.
                    // asReversed() is a view over the list, not a copy.
                    reverseLayout = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_list")
                ) {
                    items(state.messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onRemind = { remindTarget = it },
                            incomingColor = state.look.incomingBubbleColor,
                            outgoingColor = state.look.outgoingBubbleColor
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("thread_snackbar")
            )
        }
    }

    remindTarget?.let { message ->
        ReminderDialog(
            quotedMessage = message.body,
            initialText = "Reply to ${state.address.ifEmpty { "this message" }}",
            initialMillis = nowMillis() + 60L * 60L * 1000L,
            onConfirm = { text, millis ->
                remindTarget = null
                onRemind(message, text, millis)
            },
            onDismiss = { remindTarget = null }
        )
    }

    if (styleOpen) {
        ConversationStyleDialog(
            address = state.address,
            style = state.look.style,
            onBubbleColors = styleActions.onBubbleColors,
            onPickWallpaper = styleActions.onPickWallpaper,
            onClearWallpaper = styleActions.onClearWallpaper,
            onDim = styleActions.onDim,
            onReset = styleActions.onReset,
            onDismiss = { styleOpen = false }
        )
    }
}
