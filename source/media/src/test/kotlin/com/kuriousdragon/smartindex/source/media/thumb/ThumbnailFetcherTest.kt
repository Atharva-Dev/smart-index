package com.kuriousdragon.smartindex.source.media.thumb

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.ImageFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object NoThumbnails : ThumbnailLoader {
    override suspend fun load(request: ThumbnailRequest): Bitmap? = null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThumbnailFetcherTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val request = ThumbnailRequest("content://media/external/images/media/9", ItemType.PHOTO, 144)

    private fun fetcher(result: Bitmap?) = ThumbnailFetcher(
        request,
        object : ThumbnailLoader {
            override suspend fun load(request: ThumbnailRequest): Bitmap? = result
        },
    )

    @Test
    fun `a bitmap becomes a sampled disk ImageFetchResult`() = runTest {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        val result = fetcher(bitmap).fetch()
        assertThat(result).isInstanceOf(ImageFetchResult::class.java)
        result as ImageFetchResult
        assertThat(result.isSampled).isTrue()
        assertThat(result.dataSource).isEqualTo(DataSource.DISK)
    }

    @Test
    fun `a null bitmap throws ThumbnailUnavailableException without the uri in the message`() = runTest {
        val e = try {
            fetcher(null).fetch()
            null
        } catch (e: ThumbnailUnavailableException) {
            e
        }
        assertThat(e).isNotNull()
        assertThat(e!!.message).isEqualTo("thumbnail unavailable")
        assertThat(e!!.message).doesNotContain("content://")
        assertThat(e!!.message).doesNotContain("media")
    }

    @Test
    fun `the keyer returns the cache key`() {
        val key = ThumbnailKeyer().key(request, Options(context))
        assertThat(key).isEqualTo(request.cacheKey)
    }

    @Test
    fun `the factory creates a fetcher`() {
        val loader = MediaImageLoader.create(context, NoThumbnails)
        val fetcher = ThumbnailFetcher.Factory(NoThumbnails).create(request, Options(context), loader)
        assertThat(fetcher).isInstanceOf(ThumbnailFetcher::class.java)
    }

    @Test
    fun `MediaImageLoader registers both components and disables the disk cache`() {
        val loader: ImageLoader = MediaImageLoader.create(context, NoThumbnails)
        val components = loader.components
        assertThat(components.keyers.map { it.first }.any { it is ThumbnailKeyer }).isTrue()
        assertThat(components.fetcherFactories.map { it.first }.any { it is ThumbnailFetcher.Factory }).isTrue()
        assertThat(loader.defaults.diskCachePolicy).isEqualTo(CachePolicy.DISABLED)
    }
}
