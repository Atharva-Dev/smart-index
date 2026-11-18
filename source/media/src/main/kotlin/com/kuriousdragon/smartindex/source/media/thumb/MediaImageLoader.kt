package com.kuriousdragon.smartindex.source.media.thumb

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader

/** Builds the app's Coil [ImageLoader] (called from :app). */
object MediaImageLoader {
    private const val MEMORY_CACHE_PERCENT = 0.15

    /** Thumbnail components, memory cache 15 % of the app's heap, no disk cache. */
    fun create(context: PlatformContext, loader: ThumbnailLoader): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailKeyer())
                add(ThumbnailFetcher.Factory(loader))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
}
