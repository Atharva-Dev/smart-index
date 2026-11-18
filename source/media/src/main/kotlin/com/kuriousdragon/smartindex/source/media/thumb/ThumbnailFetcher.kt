package com.kuriousdragon.smartindex.source.media.thumb

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest

/** Thrown when there is no thumbnail; the message never carries a URI or a name. */
class ThumbnailUnavailableException : RuntimeException("thumbnail unavailable")

/** Coil 3 fetcher for [ThumbnailRequest] (LLD — Media & file indexer §3.3.4). */
class ThumbnailFetcher(
    private val data: ThumbnailRequest,
    private val loader: ThumbnailLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = loader.load(data) ?: throw ThumbnailUnavailableException()
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val loader: ThumbnailLoader) : Fetcher.Factory<ThumbnailRequest> {
        override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, loader)
    }
}
