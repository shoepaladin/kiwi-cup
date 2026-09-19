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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kiwicup.scheduledmessenger.core.PermissionGateState
import com.kiwicup.scheduledmessenger.core.gateState
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp

/**
 * Wraps the app: shows [PermissionsScreen] until the required permissions exist, then [content].
 * Kicks off an inbox import every time the gate opens (cheap: the import is incremental).
 *
 * The default-SMS-app role is requested *before* the SMS permissions, which is the order Android
 * documents ("an app must request to become the default SMS handler before it requests the
 * READ_SMS permission") and the order that actually works: holding the role is what makes the
 * system grant the SMS group, and asking for the permissions first can get them refused outright.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: PermissionsViewModel = hiltViewModel()
    val log = remember { PermissionAskLog(context) }

    // Fixed for the life of the install: who installed us cannot change under our feet.
    val restrictedByInstaller = remember { AppPermissions.smsRestrictedByInstaller(context) }

    var states by remember { mutableStateOf(AppPermissions.states(context, activity, log)) }
    var roleRequested by rememberSaveable { mutableStateOf(false) }
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        states = AppPermissions.states(context, activity, log)
    }

    fun diagnosticText() = DiagnosticSnapshot.collect(context, activity, log).render()

    fun currentState() = gateState(states, AppPermissions.requiredSet, restrictedByInstaller)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    fun requestPermissions() {
        permissionsRequested = true
        log.recordAsked(AppPermissions.all)
        permissionLauncher.launch(AppPermissions.all.toTypedArray())
    }

    // The role dialog is a plain activity, so its result arrives here rather than as a grant.
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
        // Accepting the role usually grants the SMS group outright; declining leaves it to the
        // permission prompt. Either way the remaining permissions still have to be asked for.
        if (currentState() != PermissionGateState.READY) requestPermissions()
    }

    // Coming back from Settings (or the default-app dialog) must refresh without a tap.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        val current = currentState()
        if (current == PermissionGateState.READY) return@LaunchedEffect
        // When the installer could not allowlist the SMS group, Android refuses both the role
        // request and the permission request without drawing anything. Firing either one just
        // produces a system warning dialog and no progress, so show the instructions instead and
        // let the user drive from there.
        if (current == PermissionGateState.RESTRICTED) return@LaunchedEffect
        val roleIntent = if (roleRequested) null else DefaultSmsApp.requestIntent(context)
        when {
            roleIntent != null -> {
                roleRequested = true
                roleLauncher.launch(roleIntent)
            }
            !permissionsRequested -> requestPermissions()
        }
    }

    val state = currentState()
    if (state == PermissionGateState.READY) {
        // Import on every return to the foreground; the import is incremental and cheap.
        LifecycleResumeEffect(Unit) {
            viewModel.importInbox()
            onPauseOrDispose { }
        }
        content()
    } else {
        // Keyed on the state so the report reflects where the gate actually settled, rather than
        // the moment before a request was fired and answered.
        LaunchedEffect(state) {
            diagnosticsStatus = DiagnosticFile.save(context, diagnosticText())
        }
        PermissionsScreen(
            missing = AppPermissions.missingRequired(context),
            state = state,
            onRequest = ::requestPermissions,
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onRecheck = ::refresh,
            onCopyDiagnostics = {
                DiagnosticFile.copyToClipboard(context, diagnosticText())
                diagnosticsStatus = "Copied to clipboard."
            },
            onSaveDiagnostics = { diagnosticsStatus = DiagnosticFile.save(context, diagnosticText()) },
            diagnosticsStatus = diagnosticsStatus
        )
    }
}
