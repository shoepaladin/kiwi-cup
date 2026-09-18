package com.kiwicup.scheduledmessenger.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.ui.components.DateTimePickerDialog
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat

/** Callbacks the queue screen needs; grouped so the composable signature stays readable. */
data class QueueActions(
    val onCancelMessage: (Long) -> Unit,
    val onDeleteMessage: (Long) -> Unit,
    val onEditMessage: (id: Long, recipient: String, body: String, target: Long) -> Unit,
    val onRescheduleMessage: (id: Long, target: Long) -> Unit,
    val onCompleteReminder: (Long) -> Unit,
    val onDeleteReminder: (Long) -> Unit,
    val onEditReminder: (id: Long, text: String, trigger: Long) -> Unit,
    val onClearHistory: () -> Unit,
    val onOpenThread: (Long) -> Unit
)

private sealed class Editing {
    data class Message(val message: ScheduledMessage) : Editing()
    data class Reschedule(val message: ScheduledMessage) : Editing()
    data class Note(val reminder: Reminder) : Editing()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    state: QueueUiState,
    nowMillis: () -> Long,
    validateTarget: (Long) -> String?,
    actions: QueueActions,
    onSnackbarShown: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); onSnackbarShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled & reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.history.isNotEmpty()) {
                        TextButton(onClick = actions.onClearHistory, modifier = Modifier.testTag("clear_history")) { Text("Clear history") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("queue_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            if (state.upcoming.isEmpty() && state.history.isEmpty()) {
                item {
                    Text(
                        "Nothing scheduled. Write a message and tap the calendar, or long-press a message for a reminder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("queue_empty")
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item { SectionHeader("Upcoming") }
                items(state.upcoming, key = { it.key }) { item ->
                    QueueCard(item, nowMillis(), actions, onEdit = { editing = it })
                }
            }
            if (state.history.isNotEmpty()) {
                item { SectionHeader("History") }
                items(state.history, key = { it.key }) { item ->
                    QueueCard(item, nowMillis(), actions, onEdit = { editing = it })
                }
            }
        }
    }

    when (val e = editing) {
        null -> Unit
        is Editing.Message -> EditMessageDialog(
            message = e.message,
            validateTarget = validateTarget,
            onConfirm = { recipient, body, target ->
                editing = null
                actions.onEditMessage(e.message.id, recipient, body, target)
            },
            onDismiss = { editing = null }
        )
        is Editing.Reschedule -> DateTimePickerDialog(
            initialMillis = maxOf(e.message.targetTimestamp, nowMillis() + 60L * 60L * 1000L),
            confirmLabel = "Reschedule",
            onConfirm = { target -> editing = null; actions.onRescheduleMessage(e.message.id, target) },
            onDismiss = { editing = null },
            validate = validateTarget
        )
        is Editing.Note -> EditReminderDialog(
            reminder = e.reminder,
            onConfirm = { text, trigger -> editing = null; actions.onEditReminder(e.reminder.id, text, trigger) },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun QueueCard(item: QueueItem, now: Long, actions: QueueActions, onEdit: (Editing) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("queue_item_${item.key}"),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (item) {
                is QueueItem.Message -> MessageCardBody(item.message, now, actions, onEdit)
                is QueueItem.Note -> ReminderCardBody(item.reminder, now, actions, onEdit)
            }
        }
    }
}

@Composable
private fun MessageCardBody(m: ScheduledMessage, now: Long, actions: QueueActions, onEdit: (Editing) -> Unit) {
    val label = when (m.status) {
        MessageStatus.PENDING -> if (m.targetTimestamp < now) "Sending soon" else "Scheduled"
        MessageStatus.SENDING -> "Sending…"
        MessageStatus.SENT -> "Sent"
        MessageStatus.FAILED -> "Failed"
        MessageStatus.CANCELLED -> "Cancelled"
    }
    Text("$label · ${TimeFormat.dateTime(m.targetTimestamp)}", style = MaterialTheme.typography.labelMedium,
        color = if (m.status == MessageStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    Text("To ${m.recipientAddress}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    Text(m.messageBody, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    m.failureReason?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
    }
    Row(modifier = Modifier.padding(top = 4.dp)) {
        when (m.status) {
            MessageStatus.PENDING -> {
                TextButton(onClick = { onEdit(Editing.Message(m)) }, modifier = Modifier.testTag("edit_${m.id}")) { Text("Edit") }
                TextButton(onClick = { actions.onCancelMessage(m.id) }, modifier = Modifier.testTag("cancel_${m.id}")) { Text("Cancel") }
            }
            MessageStatus.FAILED, MessageStatus.CANCELLED -> {
                TextButton(onClick = { onEdit(Editing.Reschedule(m)) }, modifier = Modifier.testTag("reschedule_${m.id}")) { Text("Reschedule") }
                IconButton(onClick = { actions.onDeleteMessage(m.id) }, modifier = Modifier.testTag("delete_${m.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            MessageStatus.SENT -> {
                m.threadId?.let { TextButton(onClick = { actions.onOpenThread(it) }) { Text("Open conversation") } }
                IconButton(onClick = { actions.onDeleteMessage(m.id) }, modifier = Modifier.testTag("delete_${m.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            MessageStatus.SENDING -> Unit
        }
    }
}

@Composable
private fun ReminderCardBody(r: Reminder, now: Long, actions: QueueActions, onEdit: (Editing) -> Unit) {
    val label = when {
        r.isCompleted -> "Reminder shown"
        r.triggerTimestamp < now -> "Reminder overdue"
        else -> "Reminder"
    }
    Text("$label · ${TimeFormat.dateTime(r.triggerTimestamp)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    Text(r.reminderText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    Row(modifier = Modifier.padding(top = 4.dp)) {
        TextButton(onClick = { actions.onOpenThread(r.threadId) }) { Text("Open conversation") }
        if (!r.isCompleted) {
            TextButton(onClick = { onEdit(Editing.Note(r)) }, modifier = Modifier.testTag("edit_reminder_${r.id}")) { Text("Edit") }
            TextButton(onClick = { actions.onCompleteReminder(r.id) }, modifier = Modifier.testTag("done_reminder_${r.id}")) { Text("Done") }
        } else {
            IconButton(onClick = { actions.onDeleteReminder(r.id) }, modifier = Modifier.testTag("delete_reminder_${r.id}")) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun EditMessageDialog(
    message: ScheduledMessage,
    validateTarget: (Long) -> String?,
    onConfirm: (recipient: String, body: String, target: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var recipient by remember { mutableStateOf(message.recipientAddress) }
    var body by remember { mutableStateOf(message.messageBody) }
    var pickingTime by remember { mutableStateOf(false) }

    if (!pickingTime) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit scheduled message") },
            text = {
                Column {
                    OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text("To") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_recipient"))
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("edit_body"))
                    Text("Currently ${TimeFormat.dateTime(message.targetTimestamp)}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingTime = true }, enabled = body.isNotBlank(), modifier = Modifier.testTag("edit_next")) { Text("Pick time") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    } else {
        DateTimePickerDialog(
            initialMillis = message.targetTimestamp,
            confirmLabel = "Save",
            onConfirm = { target -> onConfirm(recipient, body, target) },
            onDismiss = { pickingTime = false },
            validate = validateTarget
        )
    }
}

@Composable
private fun EditReminderDialog(
    reminder: Reminder,
    onConfirm: (text: String, trigger: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(reminder.reminderText) }
    var pickingTime by remember { mutableStateOf(false) }

    if (!pickingTime) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit reminder") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Reminder note") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_reminder_text"))
            },
            confirmButton = {
                TextButton(onClick = { pickingTime = true }, enabled = text.isNotBlank()) { Text("Pick time") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    } else {
        DateTimePickerDialog(
            initialMillis = reminder.triggerTimestamp,
            confirmLabel = "Save",
            onConfirm = { trigger -> onConfirm(text.trim(), trigger) },
            onDismiss = { pickingTime = false }
        )
    }
}
