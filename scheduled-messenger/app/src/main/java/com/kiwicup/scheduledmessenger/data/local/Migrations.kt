package com.kiwicup.scheduledmessenger.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, in order.
 *
 * These exist because the database holds things that exist nowhere else: pending scheduled
 * messages and reminders. Conversations can be re-imported from the phone's store, but a
 * scheduled text wiped by a destructive migration is simply gone, and the user finds out when it
 * does not send.
 */
object Migrations {

    /**
     * Adds the per-message read flag. Existing rows default to read: the alternative would turn a
     * user's whole history bold the first time they open the updated app.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `sms_messages` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
