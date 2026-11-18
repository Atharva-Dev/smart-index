package com.kuriousdragon.smartindex.source.media.open

import android.database.Cursor
import android.provider.MediaStore.MediaColumns

/**
 * The MediaStore columns the open-file resolver needs (`MediaQueries.OPEN_PROJECTION`, LLD — Media & file
 * indexer §3.3.6). [data] is read only to hand a `File` to FileProvider; it is never indexed or logged.
 */
data class OpenRow(
    val id: Long,
    val displayName: String?,
    val mimeType: String?,
    val relativePath: String?,
    val volumeName: String?,
    val data: String?,
    val isPending: Boolean,
    val isTrashed: Boolean,
) {
    companion object {
        /** Reads the cursor's current row with tolerant getters: a missing, null or throwing column is null / false. */
        fun from(c: Cursor): OpenRow = OpenRow(
            id = long(c, MediaColumns._ID) ?: 0L,
            displayName = string(c, MediaColumns.DISPLAY_NAME),
            mimeType = string(c, MediaColumns.MIME_TYPE),
            relativePath = string(c, MediaColumns.RELATIVE_PATH),
            volumeName = string(c, MediaColumns.VOLUME_NAME),
            data = string(c, MediaColumns.DATA),
            isPending = (long(c, MediaColumns.IS_PENDING) ?: 0L) != 0L,
            isTrashed = (long(c, MediaColumns.IS_TRASHED) ?: 0L) != 0L,
        )

        private fun string(c: Cursor, column: String): String? = read(c, column) { c.getString(it) }

        private fun long(c: Cursor, column: String): Long? = read(c, column) { c.getLong(it) }

        @Suppress("TooGenericExceptionCaught")
        private inline fun <T> read(c: Cursor, column: String, get: (Int) -> T): T? = try {
            val i = c.getColumnIndex(column)
            if (i < 0 || c.isNull(i)) null else get(i)
        } catch (_: Exception) {
            null
        }
    }
}
