package com.kiwicup.scheduledmessenger.data.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.kiwicup.scheduledmessenger.core.DefaultSmsDecision
import com.kiwicup.scheduledmessenger.core.SmsRoleStatus
import com.kiwicup.scheduledmessenger.diagnostics.AppLog

/** Checks and requests the default-SMS-app role. */
object DefaultSmsApp {

    /** See [DefaultSmsDecision] for why the role check, not getDefaultSmsPackage, decides. */
    fun isDefault(context: Context): Boolean = readings(context).also(::logIfChanged).isDefault

    /** Both raw answers, for the decision and for Settings' diagnostics line. */
    fun readings(context: Context): DefaultSmsDecision.Readings {
        val roleHeld: Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.getSystemService(RoleManager::class.java)
                    ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }
                    ?.isRoleHeld(RoleManager.ROLE_SMS)
            }.getOrNull()
        } else {
            null
        }
        val systemDefault = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        return DefaultSmsDecision.Readings(roleHeld, systemDefault, context.packageName)
    }

    // Called on every incoming text and screen resume, so log transitions, not every call.
    @Volatile private var lastLogged: DefaultSmsDecision.Readings? = null

    private fun logIfChanged(readings: DefaultSmsDecision.Readings) {
        if (readings == lastLogged) return
        lastLogged = readings
        if (readings.disagree) AppLog.w(TAG, readings.describe()) else AppLog.d(TAG, readings.describe())
    }

    private const val TAG = "DefaultSmsApp"

    /**
     * What the platform will actually do if we ask for the role.
     *
     * Degrades to [SmsRoleStatus.OFFERABLE] when it cannot tell, deliberately: the failure that
     * cost the most here was the app deciding in advance that asking was pointless and never
     * asking. An offer that the system declines to draw costs one dialog that does not appear; a
     * suppressed offer costs the whole feature.
     */
    fun status(context: Context): SmsRoleStatus = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return@runCatching if (isDefault(context)) SmsRoleStatus.HELD else SmsRoleStatus.OFFERABLE
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return@runCatching SmsRoleStatus.UNAVAILABLE
            if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) SmsRoleStatus.HELD else SmsRoleStatus.OFFERABLE
        } else {
            if (isDefault(context)) SmsRoleStatus.HELD else SmsRoleStatus.OFFERABLE
        }
    }.getOrDefault(SmsRoleStatus.OFFERABLE)

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
