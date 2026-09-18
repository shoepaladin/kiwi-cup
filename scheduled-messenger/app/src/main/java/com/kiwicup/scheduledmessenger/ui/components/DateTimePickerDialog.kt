package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Two-step picker: calendar first, then clock. Emits one epoch-millis timestamp.
 * [initialMillis] pre-selects an existing time when editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    confirmLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    validate: (Long) -> String? = { null }
) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis = TimeFormat.toUtcDayMillis(initialMillis))
    val initialTime = remember { TimeFormat.localTime(initialMillis) }
    val timeState = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute)
    var step by remember { mutableStateOf(Step.DATE) }
    var error by remember { mutableStateOf<String?>(null) }

    when (step) {
        Step.DATE -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { step = Step.TIME },
                    enabled = dateState.selectedDateMillis != null,
                    modifier = Modifier.testTag("picker_next")
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState, modifier = Modifier.testTag("date_picker"))
        }

        Step.TIME -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a time") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timeState, modifier = Modifier.testTag("time_picker"))
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp).testTag("picker_error")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = TimeFormat.combine(dateState.selectedDateMillis ?: return@TextButton, timeState.hour, timeState.minute)
                        val problem = validate(millis)
                        if (problem == null) onConfirm(millis) else error = problem
                    },
                    modifier = Modifier.testTag("picker_confirm")
                ) { Text(confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { step = Step.DATE }) { Text("Back") } }
        )
    }
}

private enum class Step { DATE, TIME }
