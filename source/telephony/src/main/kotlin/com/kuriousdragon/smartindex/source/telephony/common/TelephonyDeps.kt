package com.kuriousdragon.smartindex.source.telephony.common

import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.source.telephony.TelephonyIo
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsResolver
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** Bundle of shared dependencies of the three indexers. */
class TelephonyDeps @Inject constructor(
    val providers: TelephonyProviders,
    val writer: IndexWriter,
    val contacts: ContactsResolver,
    val country: TelephonyCountryIsoSource,
    val display: NumberDisplay,
    val clock: TelephonyClock,
    @TelephonyIo val dispatcher: CoroutineDispatcher,
)
