package com.kuriousdragon.smartindex.source.media.open

import android.database.MatrixCursor
import android.provider.MediaStore.MediaColumns
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.media.store.MediaQueries
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenRowTest {
    @Test
    fun `reads every column`() {
        val c = MatrixCursor(MediaQueries.OPEN_PROJECTION)
        c.addRow(arrayOf<Any?>(7L, "a.pdf", "application/pdf", "Download/", "external_primary", "/x/a.pdf", 1, 0))
        c.moveToFirst()
        assertThat(OpenRow.from(c)).isEqualTo(
            OpenRow(7L, "a.pdf", "application/pdf", "Download/", "external_primary", "/x/a.pdf", true, false),
        )
    }

    @Test
    fun `missing and null columns are tolerated`() {
        val c = MatrixCursor(arrayOf(MediaColumns._ID, MediaColumns.IS_TRASHED))
        c.addRow(arrayOf<Any?>(3L, null))
        c.moveToFirst()
        assertThat(OpenRow.from(c)).isEqualTo(OpenRow(3L, null, null, null, null, null, false, false))
    }
}
