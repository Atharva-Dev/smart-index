package com.kuriousdragon.smartindex.source.media.store

import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns

/** Projections, sort orders and selections for the MediaStore Files table (LLD — Media & file indexer §3.3.1, §4.1). */
internal object MediaQueries {
    /** Union of all attached external volumes. */
    val FILES_URI: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Everything [com.kuriousdragon.smartindex.source.media.map.MediaRow] holds. */
    val ROW_PROJECTION: Array<String> = arrayOf(
        MediaColumns._ID,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.RELATIVE_PATH,
        MediaColumns.VOLUME_NAME,
        MediaColumns.MIME_TYPE,
        FileColumns.MEDIA_TYPE,
        MediaColumns.SIZE,
        MediaColumns.DURATION,
        MediaColumns.DATE_TAKEN,
        MediaColumns.DATE_ADDED,
        MediaColumns.DATE_MODIFIED,
        MediaColumns.GENERATION_MODIFIED,
        MediaColumns.IS_PENDING,
        MediaColumns.IS_TRASHED,
        MediaColumns.OWNER_PACKAGE_NAME,
    )

    /** INCREMENTAL reconcile pass: exactly what the classifier reads. */
    val ID_PROJECTION: Array<String> = arrayOf(
        MediaColumns._ID,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.RELATIVE_PATH,
        MediaColumns.MIME_TYPE,
        FileColumns.MEDIA_TYPE,
        MediaColumns.IS_PENDING,
        MediaColumns.IS_TRASHED,
        MediaColumns.OWNER_PACKAGE_NAME,
    )

    /** Resolver: open one item. DATA is read only by the resolver, never indexed or logged. */
    val OPEN_PROJECTION: Array<String> = arrayOf(
        MediaColumns._ID,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.MIME_TYPE,
        MediaColumns.RELATIVE_PATH,
        MediaColumns.VOLUME_NAME,
        MediaColumns.DATA,
        MediaColumns.IS_PENDING,
        MediaColumns.IS_TRASHED,
    )

    const val SORT_FULL = "_id ASC"
    const val SORT_INCREMENTAL = "generation_modified ASC, _id ASC"
    const val SORT_IDS = "_id ASC"

    /** "generation_modified > ?" or, with [partialMedia], "(generation_modified > ? OR media_type IN (1,3))". */
    fun incrementalSelection(partialMedia: Boolean): String =
        if (partialMedia) {
            "(generation_modified > ? OR media_type IN (1,3))"
        } else {
            "generation_modified > ?"
        }

    /** D4: the composite store version, sorted `"<volume>=<version>"` entries joined by `;`. */
    fun storeVersion(reader: MediaStoreReader): String =
        reader.externalVolumes().sorted().joinToString(";") { "$it=${reader.version(it)}" }
}
