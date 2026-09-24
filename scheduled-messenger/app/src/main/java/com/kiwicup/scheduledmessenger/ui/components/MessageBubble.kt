package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.AttachmentCodec
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.core.ThemeColors
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage

/** One chat bubble. Long-press opens the context menu: reminders, and marking read or unread. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: SmsMessage,
    onRemind: (SmsMessage) -> Unit,
    modifier: Modifier = Modifier,
    /** Custom bubble colors (ARGB); null falls back to the theme. */
    incomingColor: Int? = null,
    outgoingColor: Int? = null,
    /** Flips this one message between read and unread; null hides the menu entry. */
    onToggleRead: ((SmsMessage) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val outgoing = !message.isIncoming
    val custom = if (outgoing) outgoingColor else incomingColor
    val container = when {
        custom != null -> Color(custom)
        outgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        custom != null -> Color(ThemeColors.readableOn(custom))
        outgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            if (message.isIncoming && message.recipients != null) {
                Text(
                    text = message.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp).testTag("sender_${message.id}")
                )
            }
            val media = AttachmentCodec.decode(message.attachments)
            media.forEachIndexed { index, attachment ->
                Box(modifier = Modifier.padding(vertical = 2.dp)) {
                    AttachmentThumb(attachment, size = 220.dp, tag = "attachment_${message.id}_$index")
                }
            }
            if (message.body.isNotBlank() || media.isEmpty()) Surface(
                color = container,
                contentColor = content,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (outgoing) 16.dp else 4.dp,
                    bottomEnd = if (outgoing) 4.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                    .testTag("bubble_${message.id}")
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Text(
                text = TimeFormat.time(message.timestamp) +
                    (if (message.status == SmsStatus.FAILED) " · Not sent" else "") +
                    // Visible confirmation that "Mark unread" took, since the "New" line only
                    // reflects what was unread when the conversation was opened.
                    (if (!message.isRead) " · Unread" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (message.status == SmsStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Remind me about this later") },
                    onClick = {
                        menuOpen = false
                        onRemind(message)
                    },
                    modifier = Modifier.testTag("menu_remind")
                )
                if (onToggleRead != null) {
                    DropdownMenuItem(
                        text = { Text(if (message.isRead) "Mark unread" else "Mark read") },
                        onClick = {
                            menuOpen = false
                            onToggleRead(message)
                        },
                        modifier = Modifier.testTag(if (message.isRead) "menu_mark_unread" else "menu_mark_read")
                    )
                }
            }
        }
    }
}
