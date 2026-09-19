package com.kiwicup.scheduledmessenger.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.PermissionGateState
import com.kiwicup.scheduledmessenger.core.RestrictedPermissionHelp

/**
 * Shown until the required permissions are granted. Stateless so it can be rendered in tests.
 * @param state what to say and which button to offer; see [PermissionGateState].
 */
@Composable
fun PermissionsScreen(
    missing: List<String>,
    state: PermissionGateState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit = onRequest,
    onCopyDiagnostics: () -> Unit = {},
    onSaveDiagnostics: () -> Unit = {},
    diagnosticsStatus: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state == PermissionGateState.RESTRICTED) {
            RestrictedContent(
                onOpenSettings = onOpenSettings,
                onRecheck = onRecheck,
                onRequest = onRequest
            )
        } else {
            OrdinaryContent(
                missing = missing,
                state = state,
                onRequest = onRequest,
                onOpenSettings = onOpenSettings,
                onRecheck = onRecheck
            )
        }
        DiagnosticsBlock(
            status = diagnosticsStatus,
            onCopy = onCopyDiagnostics,
            onSave = onSaveDiagnostics
        )
    }
}

/**
 * Sits below every gate state, not just the restricted one, because the reading that matters most
 * is whichever one contradicts the state we think we are in. Shown unconditionally so a user with
 * no access to adb always has something conclusive to send back.
 */
@Composable
private fun DiagnosticsBlock(status: String?, onCopy: () -> Unit, onSave: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Text(
        "Diagnostics",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag("diagnostics_heading")
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "A report of what Android reports about this install is saved automatically. Copy it if " +
            "you need to send it on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onCopy, modifier = Modifier.testTag("copy_diagnostics")) { Text("Copy report") }
        TextButton(onClick = onSave, modifier = Modifier.testTag("save_diagnostics")) { Text("Save again") }
    }
    if (status != null) {
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("diagnostics_status")
        )
    }
}

@Composable
private fun OrdinaryContent(
    missing: List<String>,
    state: PermissionGateState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Scheduled Messenger needs a few permissions", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Nothing leaves your phone except the texts you schedule.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        missing.forEach { permission ->
            Text(
                text = "• " + AppPermissions.label(permission),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("missing_$permission")
            )
        }
        Spacer(Modifier.height(24.dp))
        if (state == PermissionGateState.OPEN_SETTINGS) {
            Text(
                "You turned these down earlier, so Android will not ask again. Please enable them in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("denied_hint")
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) { Text("Open settings") }
        } else {
            Button(onClick = onRequest, modifier = Modifier.testTag("grant_button")) { Text("Grant permissions") }
        }
        TextButton(onClick = onRecheck, modifier = Modifier.padding(top = 8.dp)) { Text("Check again") }
    }
}

/**
 * The sideload case. The numbered steps lead the screen rather than a request button, because
 * until the user allows restricted settings Android refuses both the permission request and the
 * default-SMS-app role without drawing anything at all. The retry below stays available for the
 * moment after they have done it, and as an escape hatch if we misjudged the installer.
 */
@Composable
private fun RestrictedContent(
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onRequest: () -> Unit
) {
    Text("Android is blocking SMS access", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        "Because this app was installed outside the Play Store, Android treats SMS as a " +
            "restricted permission and refused it without asking you. Nothing is wrong with the " +
            "app, but it takes a few taps to unblock.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("restricted_hint")
    )
    Spacer(Modifier.height(20.dp))
    RestrictedPermissionHelp.steps.forEachIndexed { index, step ->
        Text(
            text = "${index + 1}. $step",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("restricted_step_$index")
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onOpenSettings, modifier = Modifier.testTag("open_app_info")) { Text("Open App info") }
    TextButton(onClick = onRecheck, modifier = Modifier.testTag("restricted_recheck")) { Text("Check again") }
    TextButton(onClick = onRequest, modifier = Modifier.testTag("restricted_try_prompt")) {
        Text("Try the permission prompt anyway")
    }
    Spacer(Modifier.height(12.dp))
    Text(
        RestrictedPermissionHelp.ADB_ALTERNATIVE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("adb_hint")
    )
}
