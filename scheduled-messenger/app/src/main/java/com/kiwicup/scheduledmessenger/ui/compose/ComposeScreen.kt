package com.kiwicup.scheduledmessenger.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    state: ComposeUiState,
    nowMillis: () -> Long,
    onRecipientChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSendNow: () -> Unit,
    onSchedule: (Long) -> Unit,
    validateTarget: (Long) -> String?,
    onDone: (String) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(state.done) { state.done?.let(onDone) }

    Scaffold(
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
                enabled = state.recipient.isNotBlank()
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            OutlinedTextField(
                value = state.recipient,
                onValueChange = onRecipientChange,
                label = { Text("To (phone number)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.error != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recipient_input")
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("compose_error"))
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
