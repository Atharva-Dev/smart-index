package com.kuriousdragon.smartindex.source.media.testing

import android.database.MatrixCursor
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import com.kuriousdragon.smartindex.source.media.map.MediaRow

/** A photo row by default; every argument can be overridden (null = column value null). */
@Suppress("LongParameterList")
internal fun row(
    id: Long? = 1L,
    name: String? = "IMG_0001.jpg",
    path: String? = "DCIM/Camera/",
    mime: String? = "image/jpeg",
    mediaType: Int? = FileColumns.MEDIA_TYPE_IMAGE,
    size: Long? = 1_000L,
    duration: Long? = null,
    taken: Long? = 1_700_000_000_000L,
    addedSec: Long? = 1_700_000_000L,
    modifiedSec: Long? = 1_700_000_000L,
    gen: Long? = 10L,
    volume: String? = "external_primary",
    pending: Int? = 0,
    trashed: Int? = 0,
    owner: String? = null,
) = MediaRow(
    id, name, path, volume, mime, mediaType, size, duration, taken, addedSec, modifiedSec, gen, pending, trashed, owner,
)

/** A [MatrixCursor] with exactly the [projection]'s columns (missing values as null). */
internal fun cursorOf(rows: List<MediaRow>, projection: Array<String>): MatrixCursor {
    val cursor = MatrixCursor(projection, rows.size)
    rows.forEach { r -> cursor.addRow(projection.map { valueOf(r, it) }) }
    return cursor
}

private fun valueOf(r: MediaRow, column: String): Any? = when (column) {
    MediaColumns._ID -> r.id
    MediaColumns.DISPLAY_NAME -> r.displayName
    MediaColumns.RELATIVE_PATH -> r.relativePath
    MediaColumns.VOLUME_NAME -> r.volumeName
    MediaColumns.MIME_TYPE -> r.mimeType
    FileColumns.MEDIA_TYPE -> r.mediaType
    MediaColumns.SIZE -> r.size
    MediaColumns.DURATION -> r.durationMs
    MediaColumns.DATE_TAKEN -> r.dateTaken
    MediaColumns.DATE_ADDED -> r.dateAddedSec
    MediaColumns.DATE_MODIFIED -> r.dateModifiedSec
    MediaColumns.GENERATION_MODIFIED -> r.generationModified
    MediaColumns.IS_PENDING -> r.isPending
    MediaColumns.IS_TRASHED -> r.isTrashed
    MediaColumns.OWNER_PACKAGE_NAME -> r.ownerPackage
    else -> null
}
