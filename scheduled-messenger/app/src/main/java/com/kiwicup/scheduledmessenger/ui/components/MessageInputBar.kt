package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.SmsTextAnalyzer

/**
 * Text box with two actions: send now, or pick a date and time to send later.
 * The segment counter mirrors what the radio will do with the text.
 */
@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendNow: () -> Unit,
    onSchedule: (Long) -> Unit,
    nowMillis: () -> Long,
    validateTarget: (Long) -> String?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val canSend = enabled && SmsTextAnalyzer.isSendable(text)
    val info = SmsTextAnalyzer.analyze(text)

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input"),
                    placeholder = { Text("Text message") },
                    maxLines = 5,
                    enabled = enabled
                )
                IconButton(
                    onClick = { showPicker = true },
                    enabled = canSend,
                    modifier = Modifier.testTag("schedule_button")
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Schedule send")
                }
                IconButton(
                    onClick = onSendNow,
                    enabled = canSend,
                    modifier = Modifier.testTag("send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send now")
                }
            }
            if (text.isNotEmpty()) {
                Text(
                    text = "${info.length}/${info.length + info.remainingInSegment} · ${info.segments} part${if (info.segments > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp).testTag("segment_counter")
                )
            }
        }
    }

    if (showPicker) {
        DateTimePickerDialog(
            initialMillis = nowMillis() + 60L * 60L * 1000L,
            confirmLabel = "Schedule",
            onConfirm = { millis ->
                showPicker = false
                onSchedule(millis)
            },
            onDismiss = { showPicker = false },
            validate = validateTarget
        )
    }
}
