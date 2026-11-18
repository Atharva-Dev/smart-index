package com.kuriousdragon.smartindex.source.media.store

import android.database.Cursor
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import com.kuriousdragon.smartindex.source.media.map.MediaRow

/**
 * Cursor to [MediaRow] with tolerant column getters (LLD — Media & file indexer §3.3.1).
 * Column indices are resolved once; a missing column is always null. A null value or a getter that
 * throws yields null for that field only.
 */
internal class MediaCursorReader(cursor: Cursor) {
    private val iId = cursor.getColumnIndex(MediaColumns._ID)
    private val iName = cursor.getColumnIndex(MediaColumns.DISPLAY_NAME)
    private val iPath = cursor.getColumnIndex(MediaColumns.RELATIVE_PATH)
    private val iVolume = cursor.getColumnIndex(MediaColumns.VOLUME_NAME)
    private val iMime = cursor.getColumnIndex(MediaColumns.MIME_TYPE)
    private val iMediaType = cursor.getColumnIndex(FileColumns.MEDIA_TYPE)
    private val iSize = cursor.getColumnIndex(MediaColumns.SIZE)
    private val iDuration = cursor.getColumnIndex(MediaColumns.DURATION)
    private val iTaken = cursor.getColumnIndex(MediaColumns.DATE_TAKEN)
    private val iAdded = cursor.getColumnIndex(MediaColumns.DATE_ADDED)
    private val iModified = cursor.getColumnIndex(MediaColumns.DATE_MODIFIED)
    private val iGeneration = cursor.getColumnIndex(MediaColumns.GENERATION_MODIFIED)
    private val iPending = cursor.getColumnIndex(MediaColumns.IS_PENDING)
    private val iTrashed = cursor.getColumnIndex(MediaColumns.IS_TRASHED)
    private val iOwner = cursor.getColumnIndex(MediaColumns.OWNER_PACKAGE_NAME)

    /** Reads only _ID; null when absent/unreadable. */
    fun id(c: Cursor): Long? = long(c, iId)

    /** Reads the current row; every getter is wrapped: a column that throws or is null yields null. */
    fun row(c: Cursor): MediaRow = MediaRow(
        id = long(c, iId),
        displayName = string(c, iName),
        relativePath = string(c, iPath),
        volumeName = string(c, iVolume),
        mimeType = string(c, iMime),
        mediaType = int(c, iMediaType),
        size = long(c, iSize),
        durationMs = long(c, iDuration),
        dateTaken = long(c, iTaken),
        dateAddedSec = long(c, iAdded),
        dateModifiedSec = long(c, iModified),
        generationModified = long(c, iGeneration),
        isPending = int(c, iPending),
        isTrashed = int(c, iTrashed),
        ownerPackage = string(c, iOwner),
    )

    private fun string(c: Cursor, i: Int): String? = read(c, i) { c.getString(i) }

    private fun long(c: Cursor, i: Int): Long? = read(c, i) { c.getLong(i) }

    private fun int(c: Cursor, i: Int): Int? = read(c, i) { c.getInt(i) }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> read(c: Cursor, i: Int, get: () -> T): T? {
        if (i < 0) return null
        return try {
            if (c.isNull(i)) null else get()
        } catch (_: Exception) {
            null
        }
    }
}
