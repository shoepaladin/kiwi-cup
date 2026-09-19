package com.kiwicup.scheduledmessenger.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    onRecheck: () -> Unit = onRequest
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
            RestrictedContent(onOpenSettings = onOpenSettings, onRecheck = onRecheck)
        } else {
            OrdinaryContent(
                missing = missing,
                state = state,
                onRequest = onRequest,
                onOpenSettings = onOpenSettings,
                onRecheck = onRecheck
            )
        }
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
 * The sideload case. Deliberately does not offer "Grant permissions": asking again cannot work,
 * and the settings toggle refuses too until the overflow item has been tapped, so the numbered
 * steps are the only thing on this screen that leads anywhere.
 */
@Composable
private fun RestrictedContent(onOpenSettings: () -> Unit, onRecheck: () -> Unit) {
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
    Spacer(Modifier.height(12.dp))
    Text(
        RestrictedPermissionHelp.ADB_ALTERNATIVE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("adb_hint")
    )
}
