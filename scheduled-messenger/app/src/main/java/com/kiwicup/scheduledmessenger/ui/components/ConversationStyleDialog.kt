package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle

/** Per-conversation look: bubble colors, wallpaper pick/clear, wallpaper dimming. */
@Composable
fun ConversationStyleDialog(
    address: String,
    style: ConversationStyle?,
    onBubbleColors: (incoming: Int?, outgoing: Int?) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onDim: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Style for $address") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ColorSwatches(
                    label = "Their messages",
                    selected = style?.incomingBubbleColor,
                    onSelect = { onBubbleColors(it, style?.outgoingBubbleColor) },
                    tag = "conv_incoming",
                    allowNone = true
                )
                Spacer(Modifier.height(12.dp))
                ColorSwatches(
                    label = "Your messages",
                    selected = style?.outgoingBubbleColor,
                    onSelect = { onBubbleColors(style?.incomingBubbleColor, it) },
                    tag = "conv_outgoing",
                    allowNone = true
                )
                Spacer(Modifier.height(16.dp))
                Text("Wallpaper", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = onPickWallpaper, modifier = Modifier.testTag("pick_wallpaper")) { Text("Choose photo") }
                    if (style?.wallpaperPath != null) {
                        TextButton(onClick = onClearWallpaper, modifier = Modifier.testTag("clear_wallpaper")) { Text("Remove") }
                    }
                }
                if (style?.wallpaperPath != null) {
                    Text("Dim ${style.wallpaperDimPercent}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = style.wallpaperDimPercent.toFloat(),
                        onValueChange = { onDim(it.toInt()) },
                        valueRange = 0f..90f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dim_slider")
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onReset, modifier = Modifier.testTag("reset_style")) { Text("Reset") } }
    )
}
