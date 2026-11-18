package com.kuriousdragon.smartindex.source.telephony.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec

/** The ContactsContract phone query (LLD — Telephony indexer §4.1). */
object ContactsQueries {
    fun phones() = QuerySpec(
        uri = Phone.CONTENT_URI,
        projection = listOf(
            Phone.CONTACT_ID,
            Phone.NUMBER,
            Phone.NORMALIZED_NUMBER,
            Phone.DISPLAY_NAME,
            Phone.IS_SUPER_PRIMARY,
            Phone.STARRED,
        ),
        sortOrder = "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.STARRED} DESC, ${Phone.CONTACT_ID} ASC",
    )
}
