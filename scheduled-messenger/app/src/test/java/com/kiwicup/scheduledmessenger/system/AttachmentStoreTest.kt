package com.kiwicup.scheduledmessenger.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.MmsBudget
import com.kiwicup.scheduledmessenger.data.system.AttachmentStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

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

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLargePhotoIsCompressedUnderItsShareOfTheMessageBudget() = runBlocking {
        // Real JPEG encoding (native graphics), not Robolectric's fake bitmaps, which would make
        // any size assertion pass for the wrong reason. Random pixels are the worst case for
        // JPEG, so this photo is far larger than a real one of the same dimensions.
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        bitmap.setPixels(IntArray(2400 * 1800) { random.nextInt() or (0xFF shl 24) }, 0, 2400, 0, 0, 2400, 1800)
        val photo = File(context.cacheDir, "big.jpg")
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        assertTrue("fixture should be well over budget", photo.length() > 1_000_000)

        val budget = MmsBudget.perImageBytes(imageCount = 1, otherPartBytes = 0)
        val part = store.readBytesForMms(Attachment(Uri.fromFile(photo).toString(), "image/jpeg"), budget)!!

        assertTrue("compressed to ${part.bytes.size} bytes, budget $budget", part.bytes.size <= budget)
        assertEquals("image/jpeg", part.mimeType)
        assertEquals(photo.length().toInt(), part.originalBytes)
        assertNotNull("result must still be a decodable picture", BitmapFactory.decodeByteArray(part.bytes, 0, part.bytes.size))
    }
}
