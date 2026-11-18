package com.kuriousdragon.smartindex.source.media.open

import android.net.Uri
import android.provider.MediaStore

/** MediaStore URI parsing shared by thumbnails and the open-file resolver (LLD — Media & file indexer §3.3.4). */
object MediaUris {
    /** Parses [s]; returns it only when scheme == "content" and authority == [MediaStore.AUTHORITY], else null. Never throws. */
    @Suppress("TooGenericExceptionCaught")
    fun parseMediaUri(s: String?): Uri? {
        if (s.isNullOrEmpty()) return null
        val uri = try {
            Uri.parse(s)
        } catch (_: Exception) {
            return null
        }
        return uri.takeIf { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
    }
}
