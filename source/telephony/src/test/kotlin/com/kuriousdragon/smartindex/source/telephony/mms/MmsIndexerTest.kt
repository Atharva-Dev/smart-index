package com.kuriousdragon.smartindex.source.telephony.mms

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.common.NumberDisplay
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsOutcome
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsResolver
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import com.kuriousdragon.smartindex.source.telephony.testing.FakeClock
import com.kuriousdragon.smartindex.source.telephony.testing.FakeIndexWriter
import com.kuriousdragon.smartindex.source.telephony.testing.FakeProgressSink
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addAddr
import com.kuriousdragon.smartindex.source.telephony.testing.addCanonical
import com.kuriousdragon.smartindex.source.telephony.testing.addPart
import com.kuriousdragon.smartindex.source.telephony.testing.addPdu
import com.kuriousdragon.smartindex.source.telephony.testing.addThread
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsIndexerTest {
    private val providers = FakeTelephonyProviders()
    private val writer = FakeIndexWriter()
    private val clock = FakeClock()
    private val countryIso = TelephonyCountryIsoSource(ApplicationProvider.getApplicationContext())
    private val display = NumberDisplay(countryIso, DefaultPhoneNumberNormalizer(countryIso))
    private val contacts = mockk<ContactsResolver>()
    private val contactCalls = mutableListOf<Pair<Boolean, Int>>()
    private var verboseBefore = false

    private fun TestScope.indexer() = MmsIndexer(
        TelephonyDeps(
            providers,
            writer,
            contacts,
            countryIso,
            display,
            clock,
            UnconfinedTestDispatcher(testScheduler),
        ),
        MmsRowMapper(display),
        MmsPartsReader(providers),
        MmsAddressResolver(providers),
    )

    private companion object {
        const val HOTEL = "होटल सी व्यू में बुकिंग"
        const val URI_PDU = "content://mms"
    }

    private val nowSeconds get() = clock.now / 1000

    private fun previous(cursor: Long, lastFullReconcile: Long? = null) = SyncState(
        source = Source.MMS,
        cursor = cursor,
        storeVersion = null,
        lastFullReconcile = lastFullReconcile,
        lastRun = 111L,
        lastError = "old-error",
    )

    /** [count] received MMS in one-to-one threads, each with one text part. */
    private fun addMmsRows(count: Int, date: Long = nowSeconds) {
        providers.db.beginTransaction()
        try {
            for (id in 1L..count) {
                providers.addCanonical(id, "+9198765${10_000 + id}")
                providers.addThread(id, id.toString())
                providers.addPdu(id, threadId = id, date = date)
                providers.addPart(id, id, text = "message $id")
            }
            providers.db.setTransactionSuccessful()
        } finally {
            providers.db.endTransaction()
        }
    }

    private fun SourceRunResult.assertFailed(code: String) {
        assertThat(error).isEqualTo(code)
        assertThat(succeeded).isFalse()
    }

    private fun partQueries() = providers.queries.filter { it.uri.toString() == "content://mms/part" }

    @Before
    fun setUp() {
        coEvery { contacts.ensureFresh(any()) } answers {
            contactCalls += firstArg<Boolean>() to providers.queries.size
            ContactsOutcome.Unchanged
        }
        verboseBefore = SiLog.verbose
        SiLog.verbose = true
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        SiLog.verbose = verboseBefore
    }

    // item 13
    @Test
    fun `sources and requires are the MMS ones`() = runTest {
        val i = indexer()
        assertThat(i.sources).containsExactly(Source.MMS)
        assertThat(i.requires).containsExactly(Group.MESSAGES)
    }

    // item 1
    @Test
    fun `FULL with 1201 MMS upserts in batches of 500 and reconciles with exactly those ids`() = runTest {
        addMmsRows(1_201)
        writer.states[Source.MMS] = previous(cursor = 0)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.succeeded).isTrue()
        assertThat(result.source).isEqualTo(Source.MMS)
        assertThat(result.written).isEqualTo(1_201)
        assertThat(writer.upsertCalls).containsExactly(500, 500, 201).inOrder()
        assertThat(writer.items).hasSize(1_201)
        assertThat(writer.reconciles).hasSize(1)
        assertThat(writer.reconciles.single().first).isEqualTo(Source.MMS)
        assertThat(writer.reconciles.single().second.toList()).isEqualTo((1L..1_201L).toList())
        val final = writer.states.getValue(Source.MMS)
        assertThat(final.cursor).isEqualTo(1_201L)
        assertThat(final.lastFullReconcile).isEqualTo(clock.now)
        assertThat(final.lastRun).isEqualTo(111L)
        assertThat(final.lastError).isEqualTo("old-error")
    }

    // item 2 + MMS extras
    @Test
    fun `a draft, a delivery report and a read report are neither upserted nor in the reconcile set`() = runTest {
        providers.addPdu(1, msgBox = 1, mType = 132)
        providers.addPdu(2, msgBox = 3, mType = 128) // draft
        providers.addPdu(3, msgBox = 1, mType = 134) // delivery report
        providers.addPdu(4, msgBox = 1, mType = 136) // read report
        providers.addPdu(5, msgBox = 2, mType = 128)
        providers.addPdu(6, msgBox = 1, mType = 130)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(writer.items.keys.map { it.sourceId }).containsExactly(1L, 5L, 6L)
        assertThat(writer.reconciles.single().second.toList()).containsExactly(1L, 5L, 6L).inOrder()
    }

    @Test
    fun `sent, outbox and failed MMS are SENT and inbox is RECEIVED`() = runTest {
        providers.addPdu(1, msgBox = 1)
        providers.addPdu(2, msgBox = 2, mType = 128)
        providers.addPdu(3, msgBox = 4, mType = 128)
        providers.addPdu(4, msgBox = 5, mType = 128)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.written).isEqualTo(4)
        fun direction(id: Long) = writer.items.getValue(SourceKey(Source.MMS, id)).direction
        assertThat(direction(1)).isEqualTo(Direction.RECEIVED)
        for (id in 2L..4L) assertThat(direction(id)).isEqualTo(Direction.SENT)
    }

    @Test
    fun `a group MMS from 3 participants gets the sender, the plus 2 title and joined parts`() = runTest {
        providers.addCanonical(1, "+919876500001")
        providers.addCanonical(2, "+919876500002")
        providers.addCanonical(3, "+919876500003")
        providers.addThread(5, "1 2 3")
        providers.addPdu(10, threadId = 5, msgBox = 1, subject = "Plan")
        providers.addAddr(1, 10, "+919876500002", 137)
        providers.addAddr(2, 10, "insert-address-token", 151)
        providers.addPart(1, 10, seq = 0, contentType = "application/smil", text = "<smil/>")
        providers.addPart(2, 10, seq = 2, text = "second")
        providers.addPart(3, 10, seq = 1, text = "first")
        providers.addPart(4, 10, seq = 3, contentType = "image/jpeg")
        indexer().run(Mode.FULL, ProgressSink.NONE)

        val item = writer.items.getValue(SourceKey(Source.MMS, 10))
        assertThat(item.type).isEqualTo(ItemType.MESSAGE)
        assertThat(item.msgKind).isEqualTo(MsgKind.MMS)
        assertThat(item.numberRaw).isEqualTo("+919876500002")
        assertThat(item.title).endsWith(" +2")
        assertThat(item.body).isEqualTo("Plan\nfirst\nsecond")
        assertThat(item.hasAttachment).isTrue()
        assertThat(item.primaryTs).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `a Devanagari MMS body is stored exactly`() = runTest {
        providers.addPdu(1)
        providers.addPart(1, 1, text = HOTEL)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(writer.items.getValue(SourceKey(Source.MMS, 1)).body).isEqualTo(HOTEL)
    }

    @Test
    fun `600 MMS cost exactly 2 part queries and one threads plus one canonical query`() = runTest {
        addMmsRows(600)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(partQueries()).hasSize(2)
        assertThat(partQueries().map { it.selection!!.count { c -> c == ',' } + 1 }).containsExactly(500, 100).inOrder()
        assertThat(providers.queries.count { it.uri.path == "/conversations" }).isEqualTo(1)
        assertThat(providers.queries.count { it.uri.path == "/canonical-addresses" }).isEqualTo(1)
        assertThat(providers.queries.count { it.uri.toString().endsWith("/addr") }).isEqualTo(0)
        assertThat(writer.items).hasSize(600)
    }

    // item 3
    @Test
    fun `an MMS with a null date is skipped and counted but its id is still reconciled`() = runTest {
        providers.addPdu(1)
        providers.addPdu(2, date = null)
        providers.addPdu(3, msgBox = 99)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.written).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        assertThat(writer.items.keys.map { it.sourceId }).containsExactly(1L)
        assertThat(writer.reconciles.single().second.toList()).containsExactly(1L, 2L, 3L).inOrder()
    }

    // item 4
    @Test
    fun `FULL checkpoints the cursor after batch 10 and saves it again at the end`() = runTest {
        addMmsRows(5_500)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(writer.savedStates.map { it.cursor }).containsExactly(5_000L, 5_500L).inOrder()
        assertThat(writer.savedStates.first().lastFullReconcile).isNull()
        assertThat(writer.savedStates.last().lastFullReconcile).isEqualTo(clock.now)
        assertThat(writer.reconciles).hasSize(1)
    }

    // item 5
    @Test
    fun `INCREMENTAL reads new MMS and the 48 hour window using seconds and reconciles deletions`() = runTest {
        val old = nowSeconds - 10L * 24 * 60 * 60
        val recent = nowSeconds - 60 * 60
        providers.addPdu(1, date = old, msgBox = 1)
        providers.addPdu(2, date = old, msgBox = 4, mType = 128)
        providers.addPdu(3, date = recent, msgBox = 4, mType = 128)
        providers.addPdu(4, date = recent, msgBox = 1)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(writer.items).hasSize(4)
        providers.queries.clear()
        writer.items.remove(SourceKey(Source.MMS, 2))
        writer.items.remove(SourceKey(Source.MMS, 3))

        providers.addPdu(5, date = nowSeconds, msgBox = 2, mType = 128) // _id > cursor
        providers.db.execSQL("UPDATE pdu SET msg_box = 2 WHERE _id = 3") // in window: re-read
        providers.db.execSQL("UPDATE pdu SET msg_box = 2 WHERE _id = 2") // outside the window: not re-read
        providers.db.execSQL("DELETE FROM pdu WHERE _id = 4")
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        val eligible = "msg_box <> 3 AND m_type IN (128,130,132)"
        val specs = providers.queries.filter { it.uri.toString() == URI_PDU }
        assertThat(specs.map { it.selection }).containsExactly(
            "$eligible AND _id > ?",
            "$eligible AND date >= ? AND _id <= ?",
            eligible,
        ).inOrder()
        assertThat(specs[0].args).containsExactly("4")
        // The window start is passed in SECONDS.
        assertThat(specs[1].args)
            .containsExactly(((clock.now - TelephonyConfig.RECENT_WINDOW_MS) / 1000).toString(), "4").inOrder()
        assertThat(specs[2].projection).containsExactly("_id")
        assertThat(specs[2].sortOrder).isNull()

        assertThat(writer.items.getValue(SourceKey(Source.MMS, 5)).direction).isEqualTo(Direction.SENT)
        assertThat(writer.items.getValue(SourceKey(Source.MMS, 3)).direction).isEqualTo(Direction.SENT)
        assertThat(writer.items).doesNotContainKey(SourceKey(Source.MMS, 2))
        assertThat(writer.items).doesNotContainKey(SourceKey(Source.MMS, 4))
        assertThat(result.deleted).isEqualTo(1)
        assertThat(writer.states.getValue(Source.MMS).cursor).isEqualTo(5L)
    }

    // item 6
    @Test
    fun `a run resumed after a simulated death finishes the remaining MMS and reconciles`() = runTest {
        addMmsRows(5_500)
        writer.states[Source.MMS] = previous(cursor = 5_000, lastFullReconcile = null)
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        assertThat(writer.items.keys.map { it.sourceId }).containsAtLeastElementsIn((5_001L..5_500L).toList())
        assertThat(writer.reconciles.single().second.size).isEqualTo(5_500)
        assertThat(writer.states.getValue(Source.MMS).cursor).isEqualTo(5_500L)
        assertThat(writer.states.getValue(Source.MMS).lastFullReconcile).isEqualTo(clock.now)
    }

    // item 7
    @Test
    fun `a SecurityException after 600 rows becomes permission_revoked without reconcile or cursor save`() = runTest {
        // Bare rows only: "content://mms" is also the prefix of the part, addr and mms-sms URIs, whose
        // cursors stay empty here and so never reach the fault.
        providers.db.beginTransaction()
        try {
            for (id in 1L..1_000L) providers.addPdu(id)
            providers.db.setTransactionSuccessful()
        } finally {
            providers.db.endTransaction()
        }
        providers.failOn(URI_PDU, SecurityException("x"), afterRows = 600)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        result.assertFailed("mms:permission_revoked:SecurityException")
        assertThat(writer.upsertCalls).containsExactly(500)
        assertThat(writer.reconciles).isEmpty()
        assertThat(writer.savedStates).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    @Test
    fun `a SecurityException from the parts query becomes permission_revoked`() = runTest {
        addMmsRows(3)
        providers.failOn("content://mms/part", SecurityException("x"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("mms:permission_revoked:SecurityException")
        assertThat(writer.reconciles).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 8
    @Test
    fun `a null cursor becomes provider_unavailable`() = runTest {
        addMmsRows(3)
        providers.returnNullFor(URI_PDU)
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("mms:provider_unavailable:ProviderUnavailableException")
        assertThat(writer.reconciles).isEmpty()
    }

    @Test
    fun `an IllegalArgumentException from the query becomes provider_error`() = runTest {
        addMmsRows(3)
        providers.failOn(URI_PDU, IllegalArgumentException("bad"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("mms:provider_error:IllegalArgumentException")
        assertThat(writer.reconciles).isEmpty()
    }

    @Test
    fun `a writer failure becomes provider_error`() = runTest {
        addMmsRows(3)
        writer.failNextUpsert(IllegalStateException("db"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("mms:provider_error:IllegalStateException")
        assertThat(writer.reconciles).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    @Test
    fun `a failing threads query does not fail the run and addresses come from per-id lookups`() = runTest {
        providers.failOn("content://mms-sms/conversations", IllegalStateException("boom"))
        providers.addPdu(1, threadId = 5, msgBox = 1)
        providers.addAddr(1, 1, "+919876500002", 137)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.succeeded).isTrue()
        assertThat(writer.items.getValue(SourceKey(Source.MMS, 1)).numberRaw).isEqualTo("+919876500002")
    }

    // item 9
    @Test
    fun `cancellation during the second batch propagates, closes cursors and skips reconcile`() = runTest {
        addMmsRows(1_600)
        val indexer = indexer()
        lateinit var run: Deferred<*>
        val sink = FakeProgressSink { _, _ -> if (writer.upsertCalls.size == 2) run.cancel() }
        run = async { indexer.run(Mode.FULL, sink) }

        val failure = runCatching { run.await() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
        assertThat(writer.upsertCalls).containsExactly(500, 500).inOrder()
        assertThat(writer.reconciles).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 10
    @Test
    fun `contacts are resolved with full equal to FULL before the first provider query`() = runTest {
        addMmsRows(3)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(contactCalls).containsExactly(true to 0)

        contactCalls.clear()
        indexer().run(Mode.INCREMENTAL, ProgressSink.NONE)
        assertThat(contactCalls.single().first).isFalse()
    }

    @Test
    fun `a Failed contacts outcome does not fail the run`() = runTest {
        addMmsRows(3)
        coEvery { contacts.ensureFresh(any()) } returns ContactsOutcome.Failed("SecurityException")
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
        assertThat(result.succeeded).isTrue()
        assertThat(result.written).isEqualTo(3)
    }

    // item 11
    @Test
    fun `logs never contain the number, the body, the subject or the title`() = runTest {
        providers.addCanonical(1, "+91 98765 43210")
        providers.addThread(1, "1")
        providers.addPdu(1, threadId = 1, subject = "secret-subject-9")
        providers.addPart(1, 1, text = "secret-body-123")
        providers.addPdu(2, date = null)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        providers.failOn(URI_PDU, SecurityException("+91 98765 43210 secret-body-123"))
        indexer().run(Mode.FULL, ProgressSink.NONE)

        val logs = ShadowLog.getLogs().filter { it.tag.startsWith("SI.Tel") }
        assertThat(logs).hasSize(2)
        assertThat(logs.first().tag).isEqualTo("SI.Tel.Mms")
        val text = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" }
        assertThat(text).doesNotContain("98765")
        assertThat(text).doesNotContain("9876543210")
        assertThat(text).doesNotContain("secret-")
    }

    // item 12
    @Test
    fun `progress is non-decreasing and ends at the cursor total`() = runTest {
        addMmsRows(1_201)
        val sink = FakeProgressSink()
        indexer().run(Mode.FULL, sink)

        assertThat(sink.reports.map { it.first }).isInOrder()
        assertThat(sink.reports.map { it.second }.toSet()).containsExactly(1_201)
        assertThat(sink.reports.last()).isEqualTo(1_201 to 1_201)
    }
}
