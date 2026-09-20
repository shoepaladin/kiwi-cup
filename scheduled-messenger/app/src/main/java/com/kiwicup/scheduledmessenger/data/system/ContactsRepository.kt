package com.kiwicup.scheduledmessenger.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.kiwicup.scheduledmessenger.core.Contact
import com.kiwicup.scheduledmessenger.core.ContactIndex
import com.kiwicup.scheduledmessenger.diagnostics.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads contacts once and holds them in memory for the recipient search field to filter locally.
 *
 * This mirrors the "bulk-load then filter in RAM" strategy QKSMS and Fossify both use, rather than
 * hitting the provider on every keystroke: a phone's contact list rarely changes within a single
 * compose session, and one query up front is far cheaper than one per character typed.
 */
@Singleton
class ContactsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val loaded = AtomicBoolean(false)
    private val cache = AtomicReference(ContactIndex.EMPTY)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** The region a typed number with no country code should be assumed to belong to. */
    fun defaultRegion(): String = DefaultRegion.forDevice(context)

    /**
     * Returns the cached index, loading it first if this is the first call. Safe to call
     * repeatedly; only the first caller pays for the query and for the accent folding.
     */
    suspend fun contacts(): ContactIndex {
        if (loaded.get()) return cache.get()
        return withContext(Dispatchers.IO) {
            val result = ContactIndex(query())
            cache.set(result)
            loaded.set(true)
            result
        }
    }

    /** Forces the next [contacts] call to re-query, for use after the permission is granted. */
    fun invalidate() = loaded.set(false)

    private fun query(): List<Contact> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val results = mutableListOf<Contact>()
        runCatching {
            AppLog.d(TAG, "querying contacts provider")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
                while (cursor.moveToNext()) {
                    val lookupKey = cursor.getString(lookupIndex) ?: continue
                    val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
                    val number = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() } ?: continue
                    results += Contact(lookupKey, name, number, starred = cursor.getInt(starredIndex) != 0)
                }
            }
        }.onFailure { e -> AppLog.e(TAG, "contacts query failed, falling back to no contacts", e) }
        AppLog.d(TAG, "loaded ${results.size} contact rows")
        return results
    }

    private companion object {
        const val TAG = "ContactsRepository"
    }
}
