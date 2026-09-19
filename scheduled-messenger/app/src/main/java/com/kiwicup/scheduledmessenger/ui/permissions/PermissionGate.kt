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
import com.kiwicup.scheduledmessenger.core.SmsRoleStatus
import com.kiwicup.scheduledmessenger.core.gateState
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp

/**
 * Wraps the app: shows [PermissionsScreen] until the required permissions exist, then [content].
 *
 * The order is role first, permissions second, instructions last, and that order is the whole
 * design. On an install the Play Store did not make, the SMS group is hard-restricted and a
 * runtime request for it is refused without a dialog — the "denied access to SMS" warning. The
 * default-SMS-app role is not subject to that: the role controller grants the group to whichever
 * app holds the role, which is the only reason a sideloaded SMS app can work at all.
 *
 * A previous version read the installer up front and jumped straight to the instructions screen
 * whenever it was not the Play Store. A device report showed the cost: every permission came back
 * asks=0 while the role sat offerable and unasked. The app had diagnosed itself into never trying.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: PermissionsViewModel = hiltViewModel()
    val log = remember { PermissionAskLog(context) }

    // Fixed for the life of the install: who installed us cannot change under our feet. Used only
    // to choose what to say once the role has been offered and not taken — never to skip asking.
    val restrictedByInstaller = remember { AppPermissions.smsRestrictedByInstaller(context) }

    var states by remember { mutableStateOf(AppPermissions.states(context, activity, log)) }
    var roleStatus by remember { mutableStateOf(DefaultSmsApp.status(context)) }
    var roleOffered by rememberSaveable { mutableStateOf(false) }
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }

    // Launching an activity for result before the host is resumed can be dropped silently, which
    // is one candidate explanation for a role dialog that never appeared. Nothing is launched
    // until the gate is actually on screen.
    var resumed by remember { mutableStateOf(false) }

    fun refresh() {
        states = AppPermissions.states(context, activity, log)
        roleStatus = DefaultSmsApp.status(context)
    }

    fun diagnosticText() = DiagnosticSnapshot.collect(context, activity, log).render()

    fun currentState() = gateState(
        states,
        AppPermissions.requiredSet,
        roleStatus,
        roleOffered,
        restrictedByInstaller
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    fun requestPermissions() {
        permissionsRequested = true
        log.recordAsked(AppPermissions.all)
        permissionLauncher.launch(AppPermissions.all.toTypedArray())
    }

    // The role dialog is a plain activity, so its result arrives here rather than as a grant. The
    // grant itself lands asynchronously, so this only refreshes; whether a permission request is
    // still needed is decided by the gate on the next pass.
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    fun requestRole() {
        roleOffered = true
        DefaultSmsApp.requestIntent(context)?.let { roleLauncher.launch(it) }
    }

    LifecycleResumeEffect(Unit) {
        resumed = true
        refresh()
        onPauseOrDispose { resumed = false }
    }

    val state = currentState()

    LaunchedEffect(state, resumed) {
        if (!resumed) return@LaunchedEffect
        when (state) {
            PermissionGateState.REQUEST_ROLE -> if (!roleOffered) requestRole()
            PermissionGateState.ASK -> if (!permissionsRequested) requestPermissions()
            else -> Unit
        }
    }

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
            onRequestRole = {
                // Re-offering is allowed from the button even after a decline: the user asking
                // for it is different from the app looping on it.
                roleOffered = false
                requestRole()
            },
            roleStatus = roleStatus,
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
