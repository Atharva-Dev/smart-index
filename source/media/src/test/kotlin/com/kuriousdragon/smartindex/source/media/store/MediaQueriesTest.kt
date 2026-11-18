package com.kuriousdragon.smartindex.source.media.store

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaQueriesTest {
    @Test
    fun `ROW_PROJECTION is exactly the LLD columns in order`() {
        assertThat(MediaQueries.ROW_PROJECTION.toList()).containsExactly(
            "_id", "_display_name", "relative_path", "volume_name", "mime_type", "media_type", "_size",
            "duration", "datetaken", "date_added", "date_modified", "generation_modified", "is_pending",
            "is_trashed", "owner_package_name",
        ).inOrder()
    }

    @Test
    fun `ID_PROJECTION is exactly the classifier columns in order`() {
        assertThat(MediaQueries.ID_PROJECTION.toList()).containsExactly(
            "_id",
            "_display_name",
            "relative_path",
            "mime_type",
            "media_type",
            "is_pending",
            "is_trashed",
            "owner_package_name",
        ).inOrder()
    }

    @Test
    fun `OPEN_PROJECTION is exactly the resolver columns in order`() {
        assertThat(MediaQueries.OPEN_PROJECTION.toList()).containsExactly(
            "_id",
            "_display_name",
            "mime_type",
            "relative_path",
            "volume_name",
            "_data",
            "is_pending",
            "is_trashed",
        ).inOrder()
    }

    @Test
    fun `FILES_URI is the union of external volumes`() {
        assertThat(MediaQueries.FILES_URI.toString()).isEqualTo("content://media/external/file")
    }

    @Test
    fun `sort constants`() {
        assertThat(MediaQueries.SORT_FULL).isEqualTo("_id ASC")
        assertThat(MediaQueries.SORT_INCREMENTAL).isEqualTo("generation_modified ASC, _id ASC")
        assertThat(MediaQueries.SORT_IDS).isEqualTo("_id ASC")
    }

    @Test
    fun `incrementalSelection without partial media`() {
        assertThat(MediaQueries.incrementalSelection(false)).isEqualTo("generation_modified > ?")
    }

    @Test
    fun `incrementalSelection with partial media`() {
        assertThat(MediaQueries.incrementalSelection(true))
            .isEqualTo("(generation_modified > ? OR media_type IN (1,3))")
    }
}
