package com.kuriousdragon.smartindex.source.media.thumb

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Seam over the system thumbnail call (Robolectric can't run `loadThumbnail`). */
fun interface SystemThumbnails {
    fun load(uri: Uri, size: Size, signal: CancellationSignal): Bitmap
}

/** The real [SystemThumbnails]: `ContentResolver.loadThumbnail`. */
class ContentResolverThumbnails @Inject constructor(
    @param:ApplicationContext context: Context,
) : SystemThumbnails {
    private val resolver = context.contentResolver

    override fun load(uri: Uri, size: Size, signal: CancellationSignal): Bitmap =
        resolver.loadThumbnail(uri, size, signal)
}
