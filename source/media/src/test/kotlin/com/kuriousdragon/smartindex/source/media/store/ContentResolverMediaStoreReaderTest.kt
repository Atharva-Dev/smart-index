package com.kuriousdragon.smartindex.source.media.store

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class RecordingMediaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        lastArgs = queryArgs
        lastProjection = projection?.toList()
        lastSignal = cancellationSignal
        return if (returnNull) null else MatrixCursor(projection ?: emptyArray())
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = throw IllegalStateException("no")

    companion object {
        var lastArgs: Bundle? = null
        var lastProjection: List<String>? = null
        var lastSignal: CancellationSignal? = null
        var returnNull = false
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentResolverMediaStoreReaderTest {
    private val reader = ContentResolverMediaStoreReader(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        RecordingMediaProvider.lastArgs = null
        RecordingMediaProvider.returnNull = false
        Robolectric.buildContentProvider(RecordingMediaProvider::class.java).create("media")
    }

    @Test
    fun `query passes selection args sort and excludes pending and trashed`() {
        val signal = CancellationSignal()
        val cursor = reader.query(
            MediaQueries.FILES_URI,
            arrayOf("_id", "_display_name"),
            "generation_modified > ?",
            arrayOf("5"),
            "_id ASC",
            signal,
        )
        assertThat(cursor).isNotNull()
        val b = RecordingMediaProvider.lastArgs!!
        assertThat(b.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)).isEqualTo("generation_modified > ?")
        assertThat(b.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)).asList().containsExactly("5")
        assertThat(b.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)).isEqualTo("_id ASC")
        assertThat(b.getInt(MediaStore.QUERY_ARG_MATCH_PENDING)).isEqualTo(MediaStore.MATCH_EXCLUDE)
        assertThat(b.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED)).isEqualTo(MediaStore.MATCH_EXCLUDE)
        assertThat(RecordingMediaProvider.lastProjection).containsExactly("_id", "_display_name").inOrder()
    }

    @Test
    fun `query without selection leaves the selection keys out`() {
        reader.query(MediaQueries.FILES_URI, arrayOf("_id"), null, null, null, null)
        val b = RecordingMediaProvider.lastArgs!!
        assertThat(b.containsKey(ContentResolver.QUERY_ARG_SQL_SELECTION)).isFalse()
        assertThat(b.containsKey(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)).isFalse()
        assertThat(b.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED)).isEqualTo(MediaStore.MATCH_EXCLUDE)
    }

    @Test
    fun `a null cursor from the provider is passed through`() {
        RecordingMediaProvider.returnNull = true
        assertThat(reader.query(MediaQueries.FILES_URI, arrayOf("_id"), null, null, null, null)).isNull()
    }

    @Test
    fun `version returns a question mark when the provider call fails`() {
        assertThat(reader.version("external_primary")).isEqualTo("?")
    }

    @Test
    fun `externalVolumes is never empty`() {
        assertThat(reader.externalVolumes()).isNotEmpty()
    }
}
