package com.kuriousdragon.smartindex.source.media.store

import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.media.map.MediaRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCursorReaderTest {
    private val values: Array<Any?> = arrayOf(
        42L, "IMG.jpg", "DCIM/Camera/", "external_primary", "image/jpeg", 1, 2048L, 5000L,
        1_700_000_000_000L, 1_700_000_001L, 1_700_000_002L, 77L, 0, 0, "com.foo",
    )

    private fun cursor(columns: Array<String> = MediaQueries.ROW_PROJECTION, row: Array<Any?> = values): Cursor =
        MatrixCursor(columns).apply { addRow(row) }.also { it.moveToFirst() }

    @Test
    fun `all columns are read`() {
        val c = cursor()
        assertThat(MediaCursorReader(c).row(c)).isEqualTo(
            MediaRow(
                id = 42, displayName = "IMG.jpg", relativePath = "DCIM/Camera/", volumeName = "external_primary",
                mimeType = "image/jpeg", mediaType = 1, size = 2048, durationMs = 5000,
                dateTaken = 1_700_000_000_000L, dateAddedSec = 1_700_000_001L, dateModifiedSec = 1_700_000_002L,
                generationModified = 77, isPending = 0, isTrashed = 0, ownerPackage = "com.foo",
            ),
        )
    }

    @Test
    fun `id reads only the id column`() {
        val c = cursor()
        assertThat(MediaCursorReader(c).id(c)).isEqualTo(42L)
    }

    @Test
    fun `a missing column reads as null for that field only`() {
        val cols = MediaQueries.ID_PROJECTION
        val c = cursor(cols, arrayOf(5L, "a.txt", "Download/", "text/plain", 0, 0, 0, null))
        val row = MediaCursorReader(c).row(c)
        assertThat(row.id).isEqualTo(5L)
        assertThat(row.displayName).isEqualTo("a.txt")
        assertThat(row.size).isNull()
        assertThat(row.durationMs).isNull()
        assertThat(row.volumeName).isNull()
        assertThat(row.generationModified).isNull()
        assertThat(row.dateTaken).isNull()
    }

    @Test
    fun `a missing id column gives a null id`() {
        val c = cursor(arrayOf("_display_name"), arrayOf("a"))
        assertThat(MediaCursorReader(c).id(c)).isNull()
    }

    @Test
    fun `a null value reads as null and others stay`() {
        val row = values.copyOf().also {
            it[1] = null
            it[6] = null
            it[7] = null
            it[14] = null
        }
        val c = cursor(row = row)
        val r = MediaCursorReader(c).row(c)
        assertThat(r.displayName).isNull()
        assertThat(r.size).isNull()
        assertThat(r.durationMs).isNull()
        assertThat(r.ownerPackage).isNull()
        assertThat(r.id).isEqualTo(42L)
        assertThat(r.mimeType).isEqualTo("image/jpeg")
    }

    private class ThrowingCursor(c: Cursor, private val badColumn: String) : CursorWrapper(c) {
        private val bad = c.getColumnIndex(badColumn)

        override fun getString(columnIndex: Int): String? =
            if (columnIndex == bad) error("boom $badColumn") else super.getString(columnIndex)

        override fun getLong(columnIndex: Int): Long =
            if (columnIndex == bad) error("boom $badColumn") else super.getLong(columnIndex)

        override fun getInt(columnIndex: Int): Int =
            if (columnIndex == bad) error("boom $badColumn") else super.getInt(columnIndex)
    }

    @Test
    fun `a string getter that throws yields null for that field only`() {
        val c = ThrowingCursor(cursor(), "_display_name")
        val r = MediaCursorReader(c).row(c)
        assertThat(r.displayName).isNull()
        assertThat(r.relativePath).isEqualTo("DCIM/Camera/")
        assertThat(r.id).isEqualTo(42L)
    }

    @Test
    fun `a long getter that throws yields null for that field only`() {
        val c = ThrowingCursor(cursor(), "_size")
        val r = MediaCursorReader(c).row(c)
        assertThat(r.size).isNull()
        assertThat(r.durationMs).isEqualTo(5000L)
    }

    @Test
    fun `an int getter that throws yields null for that field only`() {
        val c = ThrowingCursor(cursor(), "media_type")
        val r = MediaCursorReader(c).row(c)
        assertThat(r.mediaType).isNull()
        assertThat(r.isPending).isEqualTo(0)
    }

    @Test
    fun `an id getter that throws yields a null id`() {
        val c = ThrowingCursor(cursor(), "_id")
        assertThat(MediaCursorReader(c).id(c)).isNull()
    }
}
