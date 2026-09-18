package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** "Remind me about this later": a note plus a time. Two-step: note first, then the date/time picker. */
@Composable
fun ReminderDialog(
    quotedMessage: String?,
    initialText: String,
    initialMillis: Long,
    onConfirm: (text: String, triggerMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    if (!pickingTime) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remind me about this later") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    quotedMessage?.let {
                        Text(
                            text = "“$it”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Reminder note") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_text")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pickingTime = true },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.testTag("reminder_next")
                ) { Text("Pick time") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    } else {
        DateTimePickerDialog(
            initialMillis = initialMillis,
            confirmLabel = "Set reminder",
            onConfirm = { millis -> onConfirm(text.trim(), millis) },
            onDismiss = { pickingTime = false }
        )
    }
}
