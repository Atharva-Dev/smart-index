package com.kuriousdragon.smartindex.source.media.open

import android.webkit.MimeTypeMap
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.source.media.map.MediaNames

/** MIME type for an intent (LLD — Media & file indexer §3.3.6); never blank. */
object MimeResolver {
    private const val OCTET_STREAM = "application/octet-stream"

    /**
     * Row MIME (normalised) unless blank or "application/octet-stream"; then MimeTypeMap by extension;
     * then the row MIME if any; then the type's wildcard (image, video or audio) or octet-stream.
     */
    fun mimeFor(rowMime: String?, displayName: String, type: ItemType): String {
        val row = rowMime?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (row != null && row != OCTET_STREAM) return row
        MediaNames.extension(displayName)
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        if (row != null) return row
        return when (type) {
            ItemType.PHOTO -> "image/*"
            ItemType.VIDEO -> "video/*"
            ItemType.RECORDING -> "audio/*"
            else -> OCTET_STREAM
        }
    }
}
