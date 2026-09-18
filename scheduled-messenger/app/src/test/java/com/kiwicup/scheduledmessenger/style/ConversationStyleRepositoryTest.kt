package com.kiwicup.scheduledmessenger.style

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.repository.ConversationStyleRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class ConversationStyleRepositoryTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: ConversationStyleRepository

    @Before
    fun setUp() {
        repo = ConversationStyleRepository(context, dbRule.db.conversationStyleDao())
    }

    @Test
    fun addressesAreNormalisedAndEmptyStylesAreNotStored() = runBlocking {
        repo.setBubbleColors("(555) 000-1111", incoming = 0xFF112233.toInt(), outgoing = null)
        val stored = repo.get("+5550001111".removePrefix("+"))
        assertNotNull(stored)
        assertEquals("5550001111", stored!!.address)
        assertEquals(0xFF112233.toInt(), stored.incomingBubbleColor)

        repo.setBubbleColors("5550001111", null, null)
        assertNull(repo.get("5550001111"))
        assertEquals(0, dbRule.db.conversationStyleDao().getAll().size)
    }

    @Test
    fun wallpaperIsCopiedIntoAppStorageAndCleared() = runBlocking {
        val source = File(context.cacheDir, "pic.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val uri = Uri.fromFile(source)
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, source.inputStream())

        assertTrue(repo.setWallpaper("+15550002222", uri))
        val style = repo.get("+15550002222")!!
        val copied = File(style.wallpaperPath!!)
        assertTrue(copied.exists())
        assertTrue(copied.absolutePath.startsWith(context.filesDir.absolutePath))
        assertEquals(4, copied.length())

        repo.setWallpaperDim("+15550002222", 55)
        assertEquals(55, repo.get("+15550002222")!!.wallpaperDimPercent)

        repo.clearWallpaper("+15550002222")
        assertFalse(copied.exists())
        assertNull(repo.get("+15550002222"))
    }

    @Test
    fun unreadableSourceReturnsFalse() = runBlocking {
        assertFalse(repo.setWallpaper("+15550003333", Uri.parse("content://nowhere/missing")))
        assertNull(repo.get("+15550003333"))
    }

    @Test
    fun observeEmitsOnChange() = runBlocking {
        repo.observe("+15550004444").test {
            assertNull(awaitItem())
            repo.setBubbleColors("+15550004444", null, 0xFF0000FF.toInt())
            assertEquals(0xFF0000FF.toInt(), awaitItem()!!.outgoingBubbleColor)
            repo.reset("+15550004444")
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
