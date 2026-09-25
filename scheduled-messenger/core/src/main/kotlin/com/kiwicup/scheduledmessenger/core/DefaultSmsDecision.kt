package com.kiwicup.scheduledmessenger.core

/**
 * Two ways to ask Android "is this the default SMS app?", and which one to believe.
 *
 * [roleHeld] is `RoleManager.isRoleHeld(ROLE_SMS)`, available from Android 10 (API 29) and the
 * documented authority there; null where RoleManager does not exist or cannot answer.
 * [systemDefaultPackage] is the older `Telephony.Sms.getDefaultSmsPackage`.
 *
 * The app used to rely on the older call alone. On a Pixel 8a running Android 17 it reported this
 * app as not the default while the user had made it the default, so picture messages were refused
 * with "need this app to be the default SMS app" and the "Make this your default" banner never
 * went away.
 *
 * Why no OR of the two: being wrong in the "yes" direction is the dangerous one. The SMS receiver
 * drops the ordinary SMS_RECEIVED broadcast when it believes it is the default, relying on the
 * default-only SMS_DELIVER instead — so a false "yes" would lose incoming texts outright. The
 * role check alone decides wherever it can answer.
 */
object DefaultSmsDecision {

    data class Readings(val roleHeld: Boolean?, val systemDefaultPackage: String?, val ourPackage: String) {
        val isDefault: Boolean get() = roleHeld ?: (systemDefaultPackage == ourPackage)
        /** True when both calls answered and contradict each other — the exact situation this fixes. */
        val disagree: Boolean get() = roleHeld != null && roleHeld != (systemDefaultPackage == ourPackage)

        fun describe(): String =
            "role held=${roleHeld ?: "n/a"}, system default=${systemDefaultPackage ?: "none"}, decided default=$isDefault" +
                if (disagree) " (the two readings disagree)" else ""
    }
}
