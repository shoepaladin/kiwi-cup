package com.kiwicup.scheduledmessenger.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Wraps the app: shows [PermissionsScreen] until the required permissions exist, then [content].
 * Kicks off an inbox import every time the gate opens (cheap: the import is incremental).
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val viewModel: PermissionsViewModel = hiltViewModel()
    var missing by remember { mutableStateOf(AppPermissions.missingRequired(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        missing = AppPermissions.missingRequired(context)
        val activity = context as? Activity
        permanentlyDenied = activity != null && missing.any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    // Ask once automatically on first launch; afterwards the user drives it from the screen.
    LaunchedEffect(Unit) {
        if (missing.isNotEmpty() && !asked) {
            asked = true
            launcher.launch(AppPermissions.all.toTypedArray())
        }
    }

    if (missing.isEmpty()) {
        LaunchedEffect(Unit) { viewModel.importInbox() }
        content()
    } else {
        PermissionsScreen(
            missing = missing,
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(AppPermissions.all.toTypedArray()) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }
}
