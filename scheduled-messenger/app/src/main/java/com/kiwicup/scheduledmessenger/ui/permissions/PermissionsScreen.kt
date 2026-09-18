package com.kiwicup.scheduledmessenger.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Shown until the required permissions are granted. Stateless so it can be rendered in tests.
 * @param permanentlyDenied true when the system will no longer show the prompt; we then send the
 *        user to the app's settings page instead.
 */
@Composable
fun PermissionsScreen(
    missing: List<String>,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        if (permanentlyDenied) {
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
        TextButton(onClick = onRequest, modifier = Modifier.padding(top = 8.dp)) { Text("Check again") }
    }
}
