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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.AppSettings
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import com.kiwicup.scheduledmessenger.diagnostics.CrashHandler
import com.kiwicup.scheduledmessenger.ui.components.ColorSwatches
import com.kiwicup.scheduledmessenger.ui.permissions.DiagnosticFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSeedColor: (Int) -> Unit,
    onBubbleColors: (incoming: Int?, outgoing: Int?) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    exactAlarmsAllowed: Boolean = true,
    onOpenExactAlarmSettings: (() -> Unit)? = null
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
            Text("Scheduling", style = MaterialTheme.typography.titleMedium)
            if (exactAlarmsAllowed) {
                Text("Scheduled texts go out at the exact minute, even while the phone sleeps.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).testTag("exact_ok"))
            } else {
                Text("Android is not allowing exact timing, so a scheduled text may go out a few minutes late while the phone sleeps.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp).testTag("exact_off"))
                if (onOpenExactAlarmSettings != null) {
                    TextButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.testTag("exact_settings")) { Text("Allow exact timing") }
                }
            }
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            DiagnosticsSection()
        }
    }
}

/**
 * Lets the user pull a log off the phone without a computer: an app-log export and, when one
 * exists, the report from the last crash. Both write through [DiagnosticFile], the same
 * Downloads/clipboard route the permission screen's diagnostics already use, so there is one
 * place the user needs to know to look regardless of which screen sent them there.
 */
@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val lastCrash = remember { CrashHandler.lastReport(context) }

    Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
    Text(
        "If something crashes, this is what to send along.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Row {
        TextButton(
            onClick = { status = DiagnosticFile.save(context, AppLog.fileContents()) },
            modifier = Modifier.testTag("save_app_log")
        ) { Text("Save app log") }
        if (lastCrash != null) {
            TextButton(
                onClick = { status = DiagnosticFile.save(context, lastCrash) },
                modifier = Modifier.testTag("save_last_crash")
            ) { Text("Save last crash report") }
        }
    }
    status?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("diagnostics_status"))
    }
}
