package com.kuriousdragon.smartindex.source.media.store

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import com.kuriousdragon.smartindex.core.model.SiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The real [MediaStoreReader] (LLD — Media & file indexer §3.3.1). */
class ContentResolverMediaStoreReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaStoreReader {
    @Suppress("TooGenericExceptionCaught")
    override fun externalVolumes(): Set<String> {
        val names = try {
            MediaStore.getExternalVolumeNames(context)
        } catch (t: Exception) {
            SiLog.w(TAG, "volumes failed", t)
            emptySet()
        }
        return names.ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun version(volume: String): String = try {
        MediaStore.getVersion(context, volume)
    } catch (t: Exception) {
        SiLog.w(TAG, "version failed", t)
        "?"
    }

    @Suppress("LongParameterList")
    override fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String?,
        signal: CancellationSignal?,
    ): Cursor? {
        val bundle = Bundle().apply {
            if (selection != null) putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (args != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            if (sortOrder != null) putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
        }
        return context.contentResolver.query(uri, projection, bundle, signal)
    }

    private companion object {
        const val TAG = "Media"
    }
}
