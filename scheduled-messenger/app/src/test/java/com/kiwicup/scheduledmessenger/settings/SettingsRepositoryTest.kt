package com.kiwicup.scheduledmessenger.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.ThemeColors
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(folder.newFolder(), "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repo = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsWhenNothingStored() = runBlocking {
        val s = repo.settings.first()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertFalse(s.useDynamicColor)
        assertEquals(ThemeColors.defaultSeed, s.seedColor)
        assertNull(s.incomingBubbleColor)
    }

    @Test
    fun writesRoundTripAndResetClears() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        repo.setDynamicColor(true)
        repo.setSeedColor(0xFF123456.toInt())
        repo.setBubbleColors(0xFF00FF00.toInt(), null)

        val s = repo.settings.first()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertTrue(s.useDynamicColor)
        assertEquals(0xFF123456.toInt(), s.seedColor)
        assertEquals(0xFF00FF00.toInt(), s.incomingBubbleColor)
        assertNull(s.outgoingBubbleColor)

        repo.setBubbleColors(null, 0xFF0000FF.toInt())
        assertNull(repo.settings.first().incomingBubbleColor)

        repo.reset()
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
    }
}
