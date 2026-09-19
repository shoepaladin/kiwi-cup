package com.kiwicup.scheduledmessenger.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Phone number -> contact name, when the (optional) contacts permission is granted. Cached per process. */
@Singleton
class ContactNames @Inject constructor(@ApplicationContext private val context: Context) {

    private val cache = ConcurrentHashMap<String, String>()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** The contact's name, or the number itself when unknown or not permitted. */
    fun displayName(address: String): String {
        if (address.isBlank() || !hasPermission()) return address
        cache[address]?.let { return it }
        val name = runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: address
        cache[address] = name
        return name
    }

    fun clearCache() = cache.clear()
}
