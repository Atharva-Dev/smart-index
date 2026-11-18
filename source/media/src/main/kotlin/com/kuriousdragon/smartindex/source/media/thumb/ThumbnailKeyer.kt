package com.kuriousdragon.smartindex.source.media.thumb

import coil3.key.Keyer
import coil3.request.Options
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest

/** Memory-cache key of a thumbnail: the request's URI + size. */
class ThumbnailKeyer : Keyer<ThumbnailRequest> {
    override fun key(data: ThumbnailRequest, options: Options): String = data.cacheKey
}
