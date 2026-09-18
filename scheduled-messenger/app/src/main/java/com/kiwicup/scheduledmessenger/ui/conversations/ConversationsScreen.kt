package com.kiwicup.scheduledmessenger.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.data.local.dao.ThreadSummary
import com.kiwicup.scheduledmessenger.ui.components.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    threads: List<ThreadSummary>,
    onOpenThread: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    IconButton(onClick = onOpenQueue, modifier = Modifier.testTag("open_queue")) {
                        Icon(Icons.Default.DateRange, contentDescription = "Scheduled & reminders")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage, modifier = Modifier.testTag("new_message")) {
                Icon(Icons.Default.Add, contentDescription = "New message")
            }
        }
    ) { padding ->
        if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to write a message, then send it now or pick a time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("thread_list")) {
                items(threads, key = { it.threadId }) { thread ->
                    ListItem(
                        headlineContent = { Text(thread.address) },
                        supportingContent = { Text(thread.body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text(TimeFormat.date(thread.timestamp), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .clickable { onOpenThread(thread.threadId) }
                            .testTag("thread_${thread.threadId}")
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
