package com.kuriousdragon.smartindex.feature.home.action

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** Fake contacts provider: serves [row] (id, lookup key) or throws [failure]. */
class FakeContactsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        failure?.let { throw it }
        lastUri = uri
        lastProjection = projection?.toList()
        val c = MatrixCursor(arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY))
        row?.let { c.addRow(arrayOf<Any>(it.first, it.second)) }
        return c
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        var row: Pair<Long, String>? = null
        var failure: Throwable? = null
        var lastUri: Uri? = null
        var lastProjection: List<String>? = null
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContentResolverContactLookupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val lookup = ContentResolverContactLookup(context, Dispatchers.Unconfined)

    @Before
    fun setUp() {
        FakeContactsProvider.row = null
        FakeContactsProvider.failure = null
        FakeContactsProvider.lastUri = null
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
    }

    @Test
    fun hit_returnsLookupUri_usingPhoneLookupFilter() = runTest {
        FakeContactsProvider.row = 7L to "abc"
        val uri = lookup.lookupUri("+44 7700 900123")
        assertThat(uri).isEqualTo(ContactsContract.Contacts.getLookupUri(7L, "abc"))
        assertThat(FakeContactsProvider.lastUri.toString())
            .startsWith(ContactsContract.PhoneLookup.CONTENT_FILTER_URI.toString())
        assertThat(FakeContactsProvider.lastUri?.lastPathSegment).isEqualTo("+44 7700 900123")
        assertThat(FakeContactsProvider.lastProjection)
            .containsExactly(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY)
    }

    @Test
    fun miss_returnsNull() = runTest {
        assertThat(lookup.lookupUri("07700900123")).isNull()
    }

    @Test
    fun securityException_returnsNull() = runTest {
        FakeContactsProvider.failure = SecurityException("no READ_CONTACTS")
        assertThat(lookup.lookupUri("07700900123")).isNull()
    }

    @Test
    fun anyOtherException_returnsNull() = runTest {
        FakeContactsProvider.failure = IllegalStateException("boom")
        assertThat(lookup.lookupUri("07700900123")).isNull()
    }
}
