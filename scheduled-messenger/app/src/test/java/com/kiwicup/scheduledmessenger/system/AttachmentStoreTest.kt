package com.kiwicup.scheduledmessenger.system

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AttachmentStore(context)

    @Test
    fun persistCopiesIntoAppStorageAndDeleteRemoves() = runBlocking {
        val source = File(context.cacheDir, "clip.bin").apply { writeBytes(ByteArray(10) { it.toByte() }) }
        val attachment = store.persist(Uri.fromFile(source))
        assertNotNull(attachment)
        val copied = File(Uri.parse(attachment!!.uri).path!!)
        assertTrue(copied.absolutePath.startsWith(context.filesDir.absolutePath))
        assertEquals(10, copied.length())
        store.delete(listOf(attachment))
        assertFalse(copied.exists())
    }

    @Test
    fun smallImagesAndNonImagesPassThroughUnchanged() = runBlocking {
        val video = File(context.cacheDir, "v.bin").apply { writeBytes(ByteArray(2_000_000)) }
        val (bytes, mime) = store.readBytesForMms(Attachment(Uri.fromFile(video).toString(), "video/mp4"))!!
        assertEquals(2_000_000, bytes.size)
        assertEquals("video/mp4", mime)

        val small = File(context.cacheDir, "s.jpg").apply { writeBytes(ByteArray(1000)) }
        val (b2, m2) = store.readBytesForMms(Attachment(Uri.fromFile(small).toString(), "image/jpeg"))!!
        assertEquals(1000, b2.size)
        assertEquals("image/jpeg", m2)
    }
}
