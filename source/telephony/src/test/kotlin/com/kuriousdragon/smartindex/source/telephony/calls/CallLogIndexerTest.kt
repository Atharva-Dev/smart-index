package com.kuriousdragon.smartindex.source.telephony.calls

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.Mode
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
import com.kuriousdragon.smartindex.source.telephony.testing.addCall
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
class CallLogIndexerTest {
    private val providers = FakeTelephonyProviders()
    private val writer = FakeIndexWriter()
    private val clock = FakeClock()
    private val countryIso = TelephonyCountryIsoSource(ApplicationProvider.getApplicationContext())
    private val display = NumberDisplay(countryIso, DefaultPhoneNumberNormalizer(countryIso))
    private val contacts = mockk<ContactsResolver>()
    private val contactCalls = mutableListOf<Pair<Boolean, Int>>()
    private var verboseBefore = false

    private fun TestScope.indexer() = CallLogIndexer(
        TelephonyDeps(
            providers,
            writer,
            contacts,
            countryIso,
            display,
            clock,
            UnconfinedTestDispatcher(testScheduler),
        ),
        CallRowMapper(display),
    )

    private fun previous(cursor: Long, lastFullReconcile: Long? = null) = SyncState(
        source = Source.CALL_LOG,
        cursor = cursor,
        storeVersion = null,
        lastFullReconcile = lastFullReconcile,
        lastRun = 111L,
        lastError = "old-error",
    )

    private fun addCalls(count: Int, date: Long = clock.now) {
        providers.db.beginTransaction()
        try {
            for (id in 1L..count) providers.addCall(id, number = "+9198765${10_000 + id}", date = date)
            providers.db.setTransactionSuccessful()
        } finally {
            providers.db.endTransaction()
        }
    }

    private fun FakeTelephonyProviders.updateCall(id: Long, date: Long, type: Int) {
        db.execSQL("UPDATE calls SET date = ?, type = ? WHERE _id = ?", arrayOf<Any>(date, type, id))
    }

    private fun SourceRunResult.assertFailed(code: String) {
        assertThat(error).isEqualTo(code)
        assertThat(succeeded).isFalse()
    }

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
    fun `sources requires and tag are the call log ones`() = runTest {
        val i = indexer()
        assertThat(i.sources).containsExactly(Source.CALL_LOG)
        assertThat(i.requires).containsExactly(Group.CALL_LOG)
    }

    // item 1
    @Test
    fun `FULL with 1201 calls upserts in batches of 500 and reconciles with exactly those ids`() = runTest {
        addCalls(1_201)
        writer.states[Source.CALL_LOG] = previous(cursor = 0)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.succeeded).isTrue()
        assertThat(result.source).isEqualTo(Source.CALL_LOG)
        assertThat(result.written).isEqualTo(1_201)
        assertThat(writer.upsertCalls).containsExactly(500, 500, 201).inOrder()
        assertThat(writer.items).hasSize(1_201)
        assertThat(writer.reconciles).hasSize(1)
        assertThat(writer.reconciles.single().first).isEqualTo(Source.CALL_LOG)
        assertThat(writer.reconciles.single().second.toList()).isEqualTo((1L..1_201L).toList())
        val final = writer.states.getValue(Source.CALL_LOG)
        assertThat(final.cursor).isEqualTo(1_201L)
        assertThat(final.lastFullReconcile).isEqualTo(clock.now)
        assertThat(final.lastRun).isEqualTo(111L)
        assertThat(final.lastError).isEqualTo("old-error")
    }

    // item 2
    @Test
    fun `a voicemail row is neither upserted nor in the reconcile set`() = runTest {
        providers.addCall(1, type = 1)
        providers.addCall(2, type = 4)
        providers.addCall(3, type = 2)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(writer.items.keys.map { it.sourceId }).containsExactly(1L, 3L)
        assertThat(writer.reconciles.single().second.toList()).containsExactly(1L, 3L).inOrder()
    }

    @Test
    fun `rejected and blocked calls are indexed with those directions`() = runTest {
        providers.addCall(1, type = 5)
        providers.addCall(2, type = 6)
        providers.addCall(3, type = 7)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.written).isEqualTo(3)
        fun direction(id: Long) = writer.items.getValue(SourceKey(Source.CALL_LOG, id)).direction
        assertThat(direction(1)).isEqualTo(Direction.REJECTED)
        assertThat(direction(2)).isEqualTo(Direction.BLOCKED)
        assertThat(direction(3)).isEqualTo(Direction.INCOMING)
    }

    // item 3
    @Test
    fun `a call with a null date is skipped and counted but its id is still reconciled`() = runTest {
        providers.addCall(1)
        providers.addCall(2, date = null)
        providers.addCall(3, type = 99)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.written).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        assertThat(writer.items.keys.map { it.sourceId }).containsExactly(1L)
        assertThat(writer.reconciles.single().second.toList()).containsExactly(1L, 2L, 3L).inOrder()
    }

    // item 4
    @Test
    fun `FULL checkpoints the cursor after batch 10 and saves it again at the end`() = runTest {
        addCalls(5_500)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(writer.savedStates.map { it.cursor }).containsExactly(5_000L, 5_500L).inOrder()
        assertThat(writer.savedStates.first().lastFullReconcile).isNull()
        assertThat(writer.savedStates.last().lastFullReconcile).isEqualTo(clock.now)
        assertThat(writer.reconciles).hasSize(1)
    }

    // item 5
    @Test
    fun `INCREMENTAL reads new calls and the 48 hour window and reconciles deletions`() = runTest {
        val old = clock.now - 10L * 24 * 60 * 60 * 1000
        val recent = clock.now - 60 * 60 * 1000
        providers.addCall(1, date = old, type = 1)
        providers.addCall(2, date = old, type = 1)
        providers.addCall(3, date = recent, type = 3)
        providers.addCall(4, date = recent, type = 1)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(writer.items).hasSize(4)
        providers.queries.clear()

        providers.addCall(5, date = clock.now, type = 2) // _id > cursor
        providers.updateCall(3, recent, 1) // inside 48 h: re-read
        providers.updateCall(2, old, 3) // outside 48 h: not re-read
        providers.db.execSQL("DELETE FROM calls WHERE _id = 4")
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        val specs = providers.queries
        assertThat(specs.map { it.selection }).containsExactly(
            "type <> 4 AND _id > ?",
            "type <> 4 AND date >= ? AND _id <= ?",
            "type <> 4",
        ).inOrder()
        assertThat(specs[0].args).containsExactly("4")
        assertThat(specs[1].args)
            .containsExactly((clock.now - TelephonyConfig.RECENT_WINDOW_MS).toString(), "4").inOrder()
        assertThat(specs[2].projection).containsExactly("_id")
        assertThat(specs[2].sortOrder).isNull()

        fun direction(id: Long) = writer.items.getValue(SourceKey(Source.CALL_LOG, id)).direction
        assertThat(direction(5)).isEqualTo(Direction.OUTGOING)
        assertThat(direction(3)).isEqualTo(Direction.INCOMING)
        assertThat(direction(2)).isEqualTo(Direction.INCOMING)
        assertThat(writer.items).doesNotContainKey(SourceKey(Source.CALL_LOG, 4))
        assertThat(result.deleted).isEqualTo(1)
        assertThat(writer.states.getValue(Source.CALL_LOG).cursor).isEqualTo(5L)
    }

    // item 6
    @Test
    fun `a run resumed after a simulated death finishes the remaining calls and reconciles`() = runTest {
        addCalls(5_500)
        writer.states[Source.CALL_LOG] = previous(cursor = 5_000, lastFullReconcile = null)
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        assertThat(writer.items.keys.map { it.sourceId }).containsAtLeastElementsIn((5_001L..5_500L).toList())
        assertThat(writer.reconciles.single().second.size).isEqualTo(5_500)
        assertThat(writer.states.getValue(Source.CALL_LOG).cursor).isEqualTo(5_500L)
        assertThat(writer.states.getValue(Source.CALL_LOG).lastFullReconcile).isEqualTo(clock.now)
    }

    // item 7
    @Test
    fun `a SecurityException after 600 rows becomes permission_revoked without reconcile or cursor save`() = runTest {
        addCalls(1_000)
        providers.failOn("content://call_log/calls", SecurityException("x"), afterRows = 600)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        result.assertFailed("call_log:permission_revoked:SecurityException")
        assertThat(writer.upsertCalls).containsExactly(500)
        assertThat(writer.reconciles).isEmpty()
        assertThat(writer.savedStates).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 8
    @Test
    fun `a null cursor becomes provider_unavailable`() = runTest {
        addCalls(3)
        providers.returnNullFor("content://call_log/calls")
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("call_log:provider_unavailable:ProviderUnavailableException")
        assertThat(writer.reconciles).isEmpty()
    }

    @Test
    fun `an IllegalArgumentException from the query becomes provider_error`() = runTest {
        addCalls(3)
        providers.failOn("content://call_log/calls", IllegalArgumentException("bad"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("call_log:provider_error:IllegalArgumentException")
        assertThat(writer.reconciles).isEmpty()
    }

    @Test
    fun `a writer failure becomes provider_error`() = runTest {
        addCalls(3)
        writer.failNextUpsert(IllegalStateException("db"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("call_log:provider_error:IllegalStateException")
        assertThat(writer.reconciles).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 9
    @Test
    fun `cancellation during the second batch propagates, closes cursors and skips reconcile`() = runTest {
        addCalls(1_600)
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
        addCalls(3)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(contactCalls).containsExactly(true to 0)

        contactCalls.clear()
        indexer().run(Mode.INCREMENTAL, ProgressSink.NONE)
        assertThat(contactCalls.single().first).isFalse()
    }

    @Test
    fun `a Failed contacts outcome does not fail the run`() = runTest {
        addCalls(3)
        coEvery { contacts.ensureFresh(any()) } returns ContactsOutcome.Failed("SecurityException")
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
        assertThat(result.succeeded).isTrue()
        assertThat(result.written).isEqualTo(3)
    }

    // item 11
    @Test
    fun `logs never contain the number, the body or the title`() = runTest {
        providers.addCall(1, number = "+91 98765 43210")
        providers.addCall(2, number = "+91 98765 43211", date = null)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        providers.failOn("content://call_log/calls", SecurityException("+91 98765 43210 secret-body-123"))
        indexer().run(Mode.FULL, ProgressSink.NONE)

        val logs = ShadowLog.getLogs().filter { it.tag.startsWith("SI.Tel") }
        assertThat(logs).hasSize(2)
        assertThat(logs.first().tag).isEqualTo("SI.Tel.Calls")
        val text = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" }
        assertThat(text).doesNotContain("98765")
        assertThat(text).doesNotContain("9876543210")
        assertThat(text).doesNotContain("secret-body")
    }

    // item 12
    @Test
    fun `progress is non-decreasing and ends at the cursor total`() = runTest {
        addCalls(1_201)
        val sink = FakeProgressSink()
        indexer().run(Mode.FULL, sink)

        assertThat(sink.reports.map { it.first }).isInOrder()
        assertThat(sink.reports.map { it.second }.toSet()).containsExactly(1_201)
        assertThat(sink.reports.last()).isEqualTo(1_201 to 1_201)
    }
}
