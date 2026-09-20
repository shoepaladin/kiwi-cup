package com.kiwicup.scheduledmessenger.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.ContactSuggestion
import com.kiwicup.scheduledmessenger.core.SendOutcome
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar
import com.kiwicup.scheduledmessenger.ui.components.RecipientField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    state: ComposeUiState,
    nowMillis: () -> Long,
    onRecipientQueryChange: (String) -> Unit,
    onPickSuggestion: (ContactSuggestion) -> Unit,
    onRemoveChip: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSendNow: () -> Unit,
    onSchedule: (Long) -> Unit,
    validateTarget: (Long) -> String?,
    /** Called once the message is accepted, with the conversation it landed in when known. */
    onDone: (SendOutcome, Long?) -> Unit,
    onBack: () -> Unit,
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: (Attachment) -> Unit = {},
    isDefaultSmsApp: Boolean = true
) {
    LaunchedEffect(state.done) { state.done?.let { onDone(it, state.doneThreadId) } }

    Scaffold(
        // See ThreadScreen: under enableEdgeToEdge() the window no longer resizes for the
        // keyboard, so the IME inset has to be consumed here or the input bar sits underneath it.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                text = state.body,
                onTextChange = onBodyChange,
                onSendNow = onSendNow,
                onSchedule = onSchedule,
                nowMillis = nowMillis,
                validateTarget = validateTarget,
                // A still-typed, unconfirmed number counts too — otherwise the button stays
                // disabled until the user explicitly picks a suggestion instead of just typing.
                enabled = state.recipient.isNotBlank() || state.recipientQuery.isNotBlank(),
                attachments = state.attachments,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            RecipientField(
                chips = state.recipientChips,
                query = state.recipientQuery,
                onQueryChange = onRecipientQueryChange,
                suggestions = state.suggestions,
                onPickSuggestion = onPickSuggestion,
                onRemoveChip = onRemoveChip,
                isError = state.error != null
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("compose_error"))
            }
            if (state.isGroup) {
                Text("Group message: goes out as MMS to everyone listed.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).testTag("group_hint"))
            }
            if ((state.isGroup || state.attachments.isNotEmpty()) && !isDefaultSmsApp) {
                Text("Pictures and group texts need this app to be your default messaging app.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp).testTag("needs_default_hint"))
            }
            Text(
                "Type your message below, then send it now or tap the calendar to pick a time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
