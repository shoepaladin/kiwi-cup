package com.kiwicup.scheduledmessenger.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class MmsStoreProbeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun stub(vararg rows: Array<Any?>) {
        ProbeMmsProvider.rows = rows.toList()
        Robolectric.buildContentProvider(ProbeMmsProvider::class.java).create("mms")
    }

    @Test
    fun anUndownloadedIncomingPictureIsNamedAsSuch() {
        // The failure this probe exists to expose: told a picture exists, never fetched it.
        val now = System.currentTimeMillis() / 1000
        stub(arrayOf<Any?>(42L, Telephony.Mms.MESSAGE_BOX_INBOX, 130, 7L, now - 30))

        val line = MmsStoreProbe.snapshot(context)

        assertTrue(line, line.contains("id=42 box=inbox type=notification(not yet downloaded) thread=7"))
    }

    @Test
    fun aSentPictureShowsItsBox() {
        val now = System.currentTimeMillis() / 1000
        stub(arrayOf<Any?>(43L, Telephony.Mms.MESSAGE_BOX_SENT, 128, 7L, now))

        assertTrue(MmsStoreProbe.snapshot(context).contains("box=sent type=send-request"))
    }

    @Test
    fun anEmptyStoreSaysSo() {
        stub()
        assertEquals("mms store: empty", MmsStoreProbe.snapshot(context))
    }

    @Test
    fun noReadableStoreNeverThrows() {
        // No provider registered at all: the probe must degrade to a line, not crash the logger.
        assertTrue(MmsStoreProbe.snapshot(context).startsWith("mms store:"))
    }
}

/** Newest-first rows of (_id, msg_box, m_type, thread_id, date seconds). */
class ProbeMmsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(Telephony.Mms._ID, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE))
            .apply { rows.forEach { addRow(it) } }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        var rows: List<Array<Any?>> = emptyList()
    }
}
