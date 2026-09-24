package com.kiwicup.scheduledmessenger.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading a real version-1 database must keep what exists nowhere else: scheduled messages.
 *
 * The version-1 schema is not hand-written. The test lets Room create the current schema, then
 * rewinds only sms_messages to its version-1 shape using Room's *own* DDL for that table with the
 * new column removed. Every other table is byte-for-byte what Room makes, so the only thing under
 * test is the migration — and Room validates the migrated schema on reopen, failing loudly if the
 * migration left anything different from what the current entities expect.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .build()

    @Test
    fun upgradingFromVersion1KeepsScheduledMessagesAndTreatsHistoryAsRead() = runBlocking {
        val current = open()
        val scheduledId = current.scheduledMessageDao().insert(
            ScheduledMessage(recipientAddress = "+15551234567", messageBody = "happy birthday", targetTimestamp = 2_000_000L, createdAt = 1_000L)
        )
        val sql = current.openHelper.writableDatabase

        val tableDdl = sql.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'sms_messages'")
            .use { it.moveToFirst(); it.getString(0) }
        val isReadColumn = ", `isRead` INTEGER NOT NULL DEFAULT 1"
        assertTrue("Room's DDL changed shape; update this test. Was: $tableDdl", tableDdl.contains(isReadColumn))
        val indexDdl = sql.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'sms_messages' AND sql IS NOT NULL")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

        sql.execSQL("DROP TABLE sms_messages")
        sql.execSQL(tableDdl.replace(isReadColumn, ""))
        indexDdl.forEach { sql.execSQL(it) }
        sql.execSQL(
            "INSERT INTO sms_messages (threadId, address, body, timestamp, status, isIncoming) " +
                "VALUES (3, '+15550001111', 'an old text', 1000, 'RECEIVED', 1)"
        )
        sql.version = 1
        current.close()

        // Reopen exactly as the app does. Room runs 1 -> 2, then validates the result.
        val migrated = open()
        assertEquals("happy birthday", migrated.scheduledMessageDao().getById(scheduledId)?.messageBody)
        val old = migrated.smsMessageDao().getThread(3).single()
        assertTrue("history from before the upgrade must not turn up unread", old.isRead)
        migrated.close()
    }
}
