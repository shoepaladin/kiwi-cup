package com.kiwicup.scheduledmessenger.ui.permissions

import android.content.Context

/**
 * Counts how many times this app has launched a request for each permission.
 *
 * Android does not expose this, and without it `shouldShowRequestPermissionRationale() == false`
 * is ambiguous: it means both "never asked" and "denied so often we stopped asking". Telling
 * those apart is what lets the app recognise a permission the system refused on its own, which
 * is the sideloaded-install case and needs completely different instructions.
 */
class PermissionAskLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun timesAsked(permission: String): Int = prefs.getInt(permission, 0)

    fun recordAsked(permissions: Collection<String>) {
        val editor = prefs.edit()
        permissions.distinct().forEach { editor.putInt(it, timesAsked(it) + 1) }
        editor.apply()
    }

    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "permission_asks"
    }
}
