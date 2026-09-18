package com.kiwicup.scheduledmessenger.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.AppSettings
import com.kiwicup.scheduledmessenger.ui.components.ColorSwatches

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSeedColor: (Int) -> Unit,
    onBubbleColors: (incoming: Int?, outgoing: Int?) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = onReset, modifier = Modifier.testTag("reset_settings")) { Text("Reset") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("theme_${mode.name}")
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use wallpaper colors", style = MaterialTheme.typography.bodyLarge)
                        Text("Android picks the accent from your wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.useDynamicColor, onCheckedChange = onDynamicColor, modifier = Modifier.testTag("dynamic_switch"))
                }
            }
            Spacer(Modifier.height(16.dp))
            ColorSwatches(
                label = "Accent color",
                selected = settings.seedColor,
                onSelect = { it?.let(onSeedColor) },
                tag = "seed"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            Text("Message bubbles (default for every conversation)", style = MaterialTheme.typography.titleMedium)
            Text("Long-press a conversation title to style just that one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ColorSwatches(
                label = "Their messages",
                selected = settings.incomingBubbleColor,
                onSelect = { onBubbleColors(it, settings.outgoingBubbleColor) },
                tag = "incoming",
                allowNone = true
            )
            Spacer(Modifier.height(12.dp))
            ColorSwatches(
                label = "Your messages",
                selected = settings.outgoingBubbleColor,
                onSelect = { onBubbleColors(settings.incomingBubbleColor, it) },
                tag = "outgoing",
                allowNone = true
            )
        }
    }
}
