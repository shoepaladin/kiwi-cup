package com.kiwicup.scheduledmessenger.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
