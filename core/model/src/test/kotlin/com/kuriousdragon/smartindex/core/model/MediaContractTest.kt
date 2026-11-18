package com.kuriousdragon.smartindex.core.model

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LLD — Media & file indexer §3.1: the contracts in `Media.kt`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaContractTest {
    private val uri = "content://media/external_primary/images/media/812"

    private fun resultItem(type: ItemType, contentUri: String?) = ResultItem(
        id = 1,
        type = type,
        title = "t",
        time = 0,
        folder = null,
        sizeBytes = null,
        durationMs = null,
        ext = null,
        direction = null,
        msgKind = null,
        hasAttachment = false,
        contactName = null,
        number = null,
        contentUri = contentUri,
        snippet = null,
        matches = emptyMap(),
        linkedRecordingId = null,
    )

    private fun detail(type: ItemType, contentUri: String?) = ItemDetail(
        id = 1,
        key = SourceKey(Source.MEDIA_STORE, 1L),
        type = type,
        title = "t",
        primaryTs = 0,
        dates = emptyMap(),
        folder = null,
        mime = null,
        ext = null,
        sizeBytes = null,
        durationMs = null,
        contentUri = contentUri,
        numberRaw = null,
        contactName = null,
        direction = null,
        msgKind = null,
        threadId = null,
        hasAttachment = false,
        body = null,
        linkedRecordingId = null,
        linkedCallId = null,
    )

    private class RecordingResolver : ItemUriResolver {
        var calls = mutableListOf<Pair<ItemType, String?>>()

        override suspend fun resolve(type: ItemType, contentUri: String?): OpenTarget {
            calls += type to contentUri
            return OpenTarget.Ready(Uri.parse("content://x/1"), "image/jpeg", "a.jpg")
        }
    }

    @Test
    fun `file provider authority is the package name plus dot files`() {
        assertThat(MediaContract.fileProviderAuthority("com.kuriousdragon.smartindex"))
            .isEqualTo("com.kuriousdragon.smartindex.files")
    }

    @Test
    fun `thumbnail request is created for photo and video with a uri`() {
        for (type in listOf(ItemType.PHOTO, ItemType.VIDEO)) {
            val request = ThumbnailRequest.of(resultItem(type, uri))
            assertThat(request).isEqualTo(ThumbnailRequest(uri, type))
        }
    }

    @Test
    fun `thumbnail request is null for types without a thumbnail`() {
        for (type in listOf(
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.RECORDING,
            ItemType.CALL,
            ItemType.MESSAGE,
        )) {
            assertThat(ThumbnailRequest.of(resultItem(type, uri))).isNull()
        }
    }

    @Test
    fun `thumbnail request is null when the uri is null`() {
        assertThat(ThumbnailRequest.of(resultItem(ItemType.PHOTO, null))).isNull()
    }

    @Test
    fun `default size is 144 pixels`() {
        assertThat(ThumbnailRequest(uri, ItemType.PHOTO).sizePx).isEqualTo(144)
        assertThat(ThumbnailRequest.of(resultItem(ItemType.PHOTO, uri))!!.sizePx).isEqualTo(144)
    }

    @Test
    fun `cache key is sidx-thumb colon size colon uri`() {
        assertThat(ThumbnailRequest(uri, ItemType.PHOTO).cacheKey).isEqualTo("sidx-thumb:144:$uri")
    }

    @Test
    fun `size of zero throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { ThumbnailRequest(uri, ItemType.PHOTO, sizePx = 0) }
    }

    @Test
    fun `resolve extension for a result item delegates to resolve with type and uri`() = runTest {
        val resolver = RecordingResolver()
        resolver.resolve(resultItem(ItemType.VIDEO, uri))
        assertThat(resolver.calls).containsExactly(ItemType.VIDEO to uri)
    }

    @Test
    fun `resolve extension for an item detail delegates to resolve with type and uri`() = runTest {
        val resolver = RecordingResolver()
        val detail = detail(ItemType.DOCUMENT, "content://media/external_primary/file/4410")
        resolver.resolve(detail)
        assertThat(resolver.calls).containsExactly(ItemType.DOCUMENT to "content://media/external_primary/file/4410")
    }
}
