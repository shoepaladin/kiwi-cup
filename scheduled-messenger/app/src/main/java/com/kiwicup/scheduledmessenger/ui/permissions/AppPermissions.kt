package com.kiwicup.scheduledmessenger.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kiwicup.scheduledmessenger.core.InstallSource
import com.kiwicup.scheduledmessenger.core.PermissionState

/** Which runtime permissions the app asks for, and which of them block the main UI. */
object AppPermissions {
    /** Without these the app cannot read or send texts, so the gate screen stays up. */
    val required: List<String> = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )

    /** Asked for alongside, but the app works without them. */
    val optional: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
    }

    val all: List<String> get() = required + optional

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missingRequired(context: Context): List<String> = required.filterNot { isGranted(context, it) }

    val requiredSet: Set<String> get() = required.toSet()

    /**
     * Who installed us, which decides whether the SMS group was allowlisted. Null means the shell
     * (adb), which does allowlist — so a failure to read this must not be reported as null, or a
     * sideloaded install would be mistaken for an adb one.
     */
    fun installerPackage(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }
                .getOrDefault(UNKNOWN_INSTALLER)
        } else {
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getInstallerPackageName(context.packageName) }
                .getOrDefault(UNKNOWN_INSTALLER)
        }

    fun smsRestrictedByInstaller(context: Context): Boolean =
        InstallSource.smsLikelyRestricted(Build.VERSION.SDK_INT, installerPackage(context))

    /** Stands in for an installer we could not read: treated as a sideload, never as the shell. */
    private const val UNKNOWN_INSTALLER = "unknown"

    /**
     * Snapshots the whole batch, optional permissions included: the optional ones are what prove
     * the permission dialog is being drawn at all, which is how a system refusal is told apart
     * from a user's. [activity] is needed for the rationale flag and may be absent in previews.
     */
    fun states(context: Context, activity: Activity?, log: PermissionAskLog): List<PermissionState> =
        all.map { permission ->
            PermissionState(
                permission = permission,
                granted = isGranted(context, permission),
                canShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission),
                timesAsked = log.timesAsked(permission)
            )
        }

    fun label(permission: String): String = when (permission) {
        Manifest.permission.READ_SMS -> "Read your text messages (to show conversations)"
        Manifest.permission.RECEIVE_SMS -> "Receive text messages (to update conversations live)"
        Manifest.permission.SEND_SMS -> "Send text messages (to deliver scheduled texts)"
        Manifest.permission.POST_NOTIFICATIONS -> "Show notifications (for reminders and new messages)"
        Manifest.permission.READ_CONTACTS -> "Read contacts (to show names instead of numbers)"
        Manifest.permission.READ_PHONE_STATE -> "Read phone state (needed by picture messaging on some phones)"
        else -> permission
    }
}
