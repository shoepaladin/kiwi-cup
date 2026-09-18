package com.kiwicup.scheduledmessenger.inbox

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class SmsInboxImporterTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var importer: SmsInboxImporter

    @Before
    fun setUp() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.READ_SMS)
        importer = SmsInboxImporter(context, dbRule.db.smsMessageDao())
    }

    private fun stubProvider(vararg rows: Array<Any?>) {
        FakeSmsProvider.rows = rows.toList()
        Robolectric.buildContentProvider(FakeSmsProvider::class.java).create(Telephony.Sms.CONTENT_URI.authority!!)
    }

    @Test
    fun importsInboxAndSentRowsWithSystemThreadIds() = runBlocking {
        stubProvider(
            arrayOf(10L, 3L, "+15550001111", "hey", 1_000L, Telephony.Sms.MESSAGE_TYPE_INBOX),
            arrayOf(11L, 3L, "+15550001111", "hi back", 2_000L, Telephony.Sms.MESSAGE_TYPE_SENT),
            arrayOf(12L, 4L, "+15550002222", "lunch?", 3_000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
        )
        val result = importer.importNew()

        assertEquals(3, result.imported)
        assertFalse(result.skipped)
        val thread3 = dbRule.db.smsMessageDao().getThread(3L)
        assertEquals(listOf("hey", "hi back"), thread3.map { it.body })
        assertEquals(listOf(true, false), thread3.map { it.isIncoming })
        assertEquals(listOf(SmsStatus.RECEIVED, SmsStatus.SENT), thread3.map { it.status })
        assertEquals(12L, dbRule.db.smsMessageDao().maxSystemId())
    }

    @Test
    fun secondImportIsIncrementalAndSkipsKnownRows() = runBlocking {
        stubProvider(arrayOf(10L, 3L, "+15550001111", "hey", 1_000L, Telephony.Sms.MESSAGE_TYPE_INBOX))
        importer.importNew()
        // Provider now holds the old row plus a new one; the importer must only read past the last id.
        stubProvider(
            arrayOf(10L, 3L, "+15550001111", "hey", 1_000L, Telephony.Sms.MESSAGE_TYPE_INBOX),
            arrayOf(13L, 3L, "+15550001111", "new", 4_000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
        )
        val result = importer.importNew()

        assertEquals(1, result.imported)
        assertEquals(2, dbRule.db.smsMessageDao().countInThread(3L))
    }

    @Test
    fun withoutPermissionNothingIsRead() = runBlocking {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.READ_SMS)
        stubProvider(arrayOf(10L, 3L, "+15550001111", "hey", 1_000L, Telephony.Sms.MESSAGE_TYPE_INBOX))
        val result = importer.importNew()
        assertTrue(result.skipped)
        assertEquals(0, dbRule.db.smsMessageDao().countInThread(3L))
    }
}

/** Minimal stand-in for the phone's SMS store. Honours the `_id > ?` selection the importer uses. */
class FakeSmsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = arrayOf(
            SmsInboxImporter.COL_ID, SmsInboxImporter.COL_THREAD, SmsInboxImporter.COL_ADDRESS,
            SmsInboxImporter.COL_BODY, SmsInboxImporter.COL_DATE, SmsInboxImporter.COL_TYPE
        )
        val minExclusive = if (selection == "${SmsInboxImporter.COL_ID} > ?") selectionArgs!![0].toLong() else Long.MIN_VALUE
        val cursor = MatrixCursor(columns)
        rows.filter { (it[0] as Long) > minExclusive }.sortedBy { it[0] as Long }.forEach { cursor.addRow(it) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        var rows: List<Array<Any?>> = emptyList()
    }
}
