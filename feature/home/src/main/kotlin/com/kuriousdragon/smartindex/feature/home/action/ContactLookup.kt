package com.kuriousdragon.smartindex.feature.home.action

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.kuriousdragon.smartindex.feature.home.di.HomeIo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Finds the contact for a phone number. */
fun interface ContactLookup {
    /** The contact's lookup URI; null: no match, no READ_CONTACTS, or any failure. */
    suspend fun lookupUri(number: String): Uri?
}

class ContentResolverContactLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    @HomeIo private val io: CoroutineDispatcher,
) : ContactLookup {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun lookupUri(number: String): Uri? = withContext(io) {
        try {
            val filter = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY)
            context.contentResolver.query(filter, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                } else {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
