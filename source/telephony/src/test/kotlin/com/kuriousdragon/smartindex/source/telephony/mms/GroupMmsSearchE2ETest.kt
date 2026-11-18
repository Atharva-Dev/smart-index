package com.kuriousdragon.smartindex.source.telephony.mms

import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.debug.StorageReporter
import com.kuriousdragon.smartindex.core.index.query.IndexChangeSignal
import com.kuriousdragon.smartindex.core.index.query.RoomIndexQuery
import com.kuriousdragon.smartindex.core.index.write.RoomIndexWriter
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.source.telephony.common.NumberDisplay
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsOutcome
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsResolver
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import com.kuriousdragon.smartindex.source.telephony.testing.FakeClock
import com.kuriousdragon.smartindex.source.telephony.testing.FakeIndexWriter
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addAddr
import com.kuriousdragon.smartindex.source.telephony.testing.addCanonical
import com.kuriousdragon.smartindex.source.telephony.testing.addPart
import com.kuriousdragon.smartindex.source.telephony.testing.addPdu
import com.kuriousdragon.smartindex.source.telephony.testing.addThread
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TC-TEL-006: a group MMS is found by the saved name of every participant (SIDX-108, decision SIDX-116). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMmsSearchE2ETest {
    private val providers = FakeTelephonyProviders()
    private val fake = FakeIndexWriter()
    private val countryIso = TelephonyCountryIsoSource(ApplicationProvider.getApplicationContext())
    private val numbers = DefaultPhoneNumberNormalizer(countryIso)
    private val db = IndexDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val store = RoomIndexWriter(db, numbers, StorageReporter(db))
    private val query = RoomIndexQuery(db, IndexChangeSignal(db), { java.time.ZoneId.of("UTC") })

    @After
    fun tearDown() = db.close()

    private fun find(text: String): List<Long> =
        kotlinx.coroutines.runBlocking {
            query.search(SearchQuery(text, null, null)).asSnapshot { appendScrollWhile { true } }.map { it.id }
        }

    @Test
    fun `a received group MMS is found by the name of the sender and of each other participant`() = runTest {
        providers.addCanonical(1, "+919876500001")
        providers.addCanonical(2, "+919876500002")
        providers.addCanonical(3, "+919876500003")
        providers.addThread(5, "1 2 3")
        providers.addPdu(10, threadId = 5, msgBox = 1)
        providers.addAddr(1, 10, "+919876500002", 137) // sender
        providers.addPart(1, 10, text = "Hotel Sea View")

        val contacts = mockk<ContactsResolver>()
        coEvery { contacts.ensureFresh(any()) } returns ContactsOutcome.Unchanged
        val display = NumberDisplay(countryIso, numbers)
        MmsIndexer(
            TelephonyDeps(
                providers,
                fake,
                contacts,
                countryIso,
                display,
                FakeClock(),
                UnconfinedTestDispatcher(testScheduler),
            ),
            MmsRowMapper(display),
            MmsPartsReader(providers),
            MmsAddressResolver(providers),
        ).run(Mode.FULL, ProgressSink.NONE)

        val record = fake.items.values.single()
        assertThat(record.key.source).isEqualTo(Source.MMS)
        assertThat(record.numberRaw).isEqualTo("+919876500002")
        assertThat(record.otherNumbers).containsExactly("+919876500001", "+919876500003")

        store.upsert(listOf(record))
        store.setContactNames(
            mapOf("+919876500001" to "Meera", "+919876500002" to "Rahul", "+919876500003" to "Sanjay"),
        )
        val id = find("rahul").single()
        for (term in listOf("meera", "sanjay", "me", "sa", "Rahul")) assertThat(find(term)).containsExactly(id)
        assertThat(find("zed")).isEmpty()
    }
}
