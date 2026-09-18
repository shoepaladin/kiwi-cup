package com.kiwicup.scheduledmessenger.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.ui.components.MessageBubble
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar
import com.kiwicup.scheduledmessenger.ui.components.ReminderDialog
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat

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
    onBack: () -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    var remindTarget by remember { mutableStateOf<SmsMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            onSnackbarShown()
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.address.ifEmpty { "Conversation" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            MessageInputBar(
                text = state.draft,
                onTextChange = onDraftChange,
                onSendNow = onSendNow,
                onSchedule = onSchedule,
                nowMillis = nowMillis,
                validateTarget = validateTarget,
                enabled = state.address.isNotEmpty()
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
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
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_list")
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message, onRemind = { remindTarget = it })
                }
            }
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
}
