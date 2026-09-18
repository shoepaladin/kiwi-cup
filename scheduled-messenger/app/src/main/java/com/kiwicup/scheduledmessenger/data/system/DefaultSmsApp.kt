package com.kiwicup.scheduledmessenger.data.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Checks and requests the default-SMS-app role. */
object DefaultSmsApp {

    fun isDefault(context: Context): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }.getOrDefault(false)

    /** Intent that asks the user to make this app the default; null when the device has no SMS role. */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return legacyIntent(context)
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        return legacyIntent(context)
    }

    private fun legacyIntent(context: Context): Intent =
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
}
