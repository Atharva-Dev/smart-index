package com.kuriousdragon.smartindex.source.media.thumb

import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val URI = "content://media/external/images/media/7"

private class FakeSystem(private val action: (Uri, Size, CancellationSignal) -> Bitmap) : SystemThumbnails {
    val calls = mutableListOf<Pair<Uri, Size>>()

    override fun load(uri: Uri, size: Size, signal: CancellationSignal): Bitmap {
        calls += uri to size
        return action(uri, size, signal)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreThumbnailLoaderTest {
    private val bitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private fun loader(system: SystemThumbnails) = MediaStoreThumbnailLoader(system, Dispatchers.IO)

    private fun request(type: ItemType, uri: String = URI) = ThumbnailRequest(uri, type, 144)

    @Test
    fun `photo and video call the system with 144 by 144`() = runBlocking {
        for (type in listOf(ItemType.PHOTO, ItemType.VIDEO)) {
            val system = FakeSystem { _, _, _ -> bitmap }
            assertThat(loader(system).load(request(type))).isSameInstanceAs(bitmap)
            assertThat(system.calls).hasSize(1)
            assertThat(system.calls[0].first.toString()).isEqualTo(URI)
            assertThat(system.calls[0].second).isEqualTo(Size(144, 144))
        }
    }

    @Test
    fun `non thumbnail types return null without calling the system`() = runBlocking {
        val system = FakeSystem { _, _, _ -> bitmap }
        for (type in listOf(ItemType.DOCUMENT, ItemType.RECORDING, ItemType.CALL)) {
            assertThat(loader(system).load(request(type))).isNull()
        }
        assertThat(system.calls).isEmpty()
    }

    @Test
    fun `non media and malformed uris return null without calling the system`() = runBlocking {
        val system = FakeSystem { _, _, _ -> bitmap }
        for (uri in listOf("content://com.android.contacts/contacts/1", "file:///a.jpg", "", "not a uri")) {
            assertThat(loader(system).load(request(ItemType.PHOTO, uri))).isNull()
        }
        assertThat(system.calls).isEmpty()
    }

    @Test
    fun `system failures return null`() = runBlocking {
        val failures = listOf<Exception>(
            IOException("x"),
            SecurityException("x"),
            IllegalArgumentException("x"),
            FileNotFoundException("x"),
        )
        for (failure in failures) {
            val system = FakeSystem { _, _, _ -> throw failure }
            assertThat(loader(system).load(request(ItemType.PHOTO))).isNull()
            assertThat(system.calls).hasSize(1)
        }
    }

    @Test
    fun `cancelling the caller cancels the signal and propagates CancellationException`() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        var captured: CancellationSignal? = null
        val system = FakeSystem { _, _, signal ->
            captured = signal
            signal.setOnCancelListener { cancelled.countDown() }
            entered.countDown()
            check(cancelled.await(5, TimeUnit.SECONDS)) { "signal was not cancelled" }
            signal.throwIfCanceled()
            bitmap
        }
        val job = async(Dispatchers.Default) { loader(system).load(request(ItemType.PHOTO)) }
        check(entered.await(5, TimeUnit.SECONDS)) { "system not reached" }
        job.cancel()
        var thrown: Throwable? = null
        try {
            job.await()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(captured!!.isCanceled).isTrue()
    }
}
