package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.kiwicup.scheduledmessenger.core.ThemeColors

/**
 * A row of preset circles plus a hex field. [selected] null shows nothing selected.
 * [allowNone] adds a "theme default" swatch that reports null.
 */
@Composable
fun ColorSwatches(
    label: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    tag: String,
    allowNone: Boolean = false,
    modifier: Modifier = Modifier
) {
    var hex by remember(selected) { mutableStateOf(selected?.let(ThemeColors::toHex) ?: "") }
    var hexError by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(modifier = Modifier.testTag("${tag}_row")) {
            if (allowNone) {
                item {
                    Swatch(color = null, isSelected = selected == null, tag = "${tag}_none") { onSelect(null) }
                }
            }
            items(ThemeColors.presets, key = { it.argb }) { preset ->
                Swatch(color = Color(preset.argb), isSelected = selected == preset.argb, tag = "${tag}_${preset.name}") {
                    onSelect(preset.argb)
                }
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { text ->
                hex = text
                val parsed = ThemeColors.parseHex(text)
                hexError = text.isNotBlank() && parsed == null
                if (parsed != null) onSelect(parsed)
            },
            label = { Text("Hex, e.g. #1E5F74") },
            isError = hexError,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag("${tag}_hex")
        )
    }
}

@Composable
private fun Swatch(color: Color?, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(40.dp)
            .border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) outline else outline.copy(alpha = 0.3f), shape = CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) Text("A", style = MaterialTheme.typography.labelLarge)
    }
}
