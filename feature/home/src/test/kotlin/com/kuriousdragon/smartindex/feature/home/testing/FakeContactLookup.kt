package com.kuriousdragon.smartindex.feature.home.testing

import android.net.Uri
import com.kuriousdragon.smartindex.feature.home.action.ContactLookup

/** [ContactLookup] returning [result]; records the numbers asked for. */
internal class FakeContactLookup(var result: Uri? = null) : ContactLookup {
    val numbers = mutableListOf<String>()

    override suspend fun lookupUri(number: String): Uri? {
        numbers += number
        return result
    }
}
