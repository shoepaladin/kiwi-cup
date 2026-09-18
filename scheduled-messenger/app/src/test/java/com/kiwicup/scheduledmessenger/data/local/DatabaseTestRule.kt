package com.kiwicup.scheduledmessenger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** Opens a fresh in-memory database for every test and closes it afterwards. */
class DatabaseTestRule : ExternalResource() {
    lateinit var db: AppDatabase
        private set

    override fun before() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    override fun after() {
        db.close()
    }
}
