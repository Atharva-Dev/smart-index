package com.kuriousdragon.smartindex.source.telephony.common

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsOutcome
import com.kuriousdragon.smartindex.source.telephony.contacts.ContactsResolver
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import com.kuriousdragon.smartindex.source.telephony.testing.FakeClock
import com.kuriousdragon.smartindex.source.telephony.testing.FakeIndexWriter
import com.kuriousdragon.smartindex.source.telephony.testing.FakeProgressSink
import com.kuriousdragon.smartindex.source.telephony.testing.FakeSourceIndexer
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addTestRow
import com.kuriousdragon.smartindex.source.telephony.testing.addTestRows
import com.kuriousdragon.smartindex.source.telephony.testing.createTestTable
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderSourceIndexerTest {
    private val providers = FakeTelephonyProviders().also { it.createTestTable() }
    private val writer = FakeIndexWriter()
    private val clock = FakeClock()
    private val countryIso = TelephonyCountryIsoSource(ApplicationProvider.getApplicationContext())
    private val display = NumberDisplay(countryIso, DefaultPhoneNumberNormalizer(countryIso))
    private var verboseBefore = false

    private val contacts = mockk<ContactsResolver>()

    /** `ensureFresh(full)` arguments, and how many provider queries had run when each call happened. */
    private val contactCalls = mutableListOf<Pair<Boolean, Int>>()

    private fun TestScope.indexer(): FakeSourceIndexer = FakeSourceIndexer(
        TelephonyDeps(
            providers,
            writer,
            contacts,
            countryIso,
            display,
            clock,
            UnconfinedTestDispatcher(testScheduler),
        ),
    )

    private fun previous(cursor: Long, lastFullReconcile: Long? = null) = SyncState(
        source = Source.SMS,
        cursor = cursor,
        storeVersion = null,
        lastFullReconcile = lastFullReconcile,
        lastRun = 111L,
        lastError = "old-error",
    )

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

    @Test
    fun `sources and requires come from the subclass`() = runTest {
        val i = indexer()
        assertThat(i.sources).containsExactly(Source.SMS)
        assertThat(i.requires).containsExactly(Group.MESSAGES)
    }

    // LLD §7.3 item 1
    @Test
    fun `FULL with 1201 rows upserts in batches of 500 and reconciles with exactly those ids`() = runTest {
        providers.addTestRows(1_201)
        writer.states[Source.SMS] = previous(cursor = 0)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.succeeded).isTrue()
        assertThat(result.mode).isEqualTo(Mode.FULL)
        assertThat(result.written).isEqualTo(1_201)
        assertThat(writer.upsertCalls).containsExactly(500, 500, 201).inOrder()
        assertThat(IndexConfig.UPSERT_BATCH_SIZE).isEqualTo(500)
        assertThat(writer.items).hasSize(1_201)
        assertThat(writer.reconciles).hasSize(1)
        assertThat(writer.reconciles.single().second.toList()).isEqualTo((1L..1_201L).toList())
        val final = writer.states.getValue(Source.SMS)
        assertThat(final.cursor).isEqualTo(1_201L)
        assertThat(final.lastFullReconcile).isEqualTo(clock.now)
        assertThat(final.lastRun).isEqualTo(111L)
        assertThat(final.lastError).isEqualTo("old-error")
        assertThat(final.storeVersion).isNull()
    }

    @Test
    fun `FULL ignores the stored cursor and the first checkpoint starts from zero`() = runTest {
        providers.addTestRows(5_500)
        writer.states[Source.SMS] = previous(cursor = 999_999)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(writer.upsertCalls.sum()).isEqualTo(5_500)
        assertThat(writer.savedStates.first().cursor).isEqualTo(5_000L)
    }

    @Test
    fun `INCREMENTAL without a stored state is escalated to FULL and reported as FULL`() = runTest {
        providers.addTestRows(3)
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()
        assertThat(result.mode).isEqualTo(Mode.FULL)
        assertThat(providers.queries.map { it.args }).doesNotContain(listOf("0"))
        assertThat(writer.items).hasSize(3)
    }

    // item 3
    @Test
    fun `a row with a null date is skipped and counted but its id is still reconciled`() = runTest {
        providers.addTestRows(3)
        providers.addTestRow(id = 4, date = null)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        assertThat(result.written).isEqualTo(3)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(writer.items.keys.map { it.sourceId }).containsExactly(1L, 2L, 3L)
        assertThat(writer.reconciles.single().second.toList()).containsExactly(1L, 2L, 3L, 4L).inOrder()
    }

    // item 4
    @Test
    fun `FULL checkpoints the cursor after every 10 batches and saves it again at the end`() = runTest {
        providers.addTestRows(5_500)
        indexer().run(Mode.FULL, ProgressSink.NONE)

        assertThat(TelephonyConfig.CHECKPOINT_EVERY_BATCHES).isEqualTo(10)
        assertThat(writer.savedStates.map { it.cursor }).containsExactly(5_000L, 5_500L).inOrder()
        assertThat(writer.savedStates.first().lastFullReconcile).isNull()
        assertThat(writer.savedStates.last().lastFullReconcile).isEqualTo(clock.now)
        assertThat(writer.reconciles).hasSize(1)
    }

    // item 5
    @Test
    fun `INCREMENTAL reads new rows and the 48 hour window and reconciles deletions`() = runTest {
        val old = clock.now - 10L * 24 * 60 * 60 * 1000
        val recent = clock.now - 60 * 60 * 1000
        providers.addTestRow(1, date = old, value = "old one")
        providers.addTestRow(2, date = old, value = "old two")
        providers.addTestRow(3, date = recent, value = "recent")
        providers.addTestRow(4, date = recent, value = "to delete")
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(writer.items).hasSize(4)
        writer.upsertCalls.clear()
        providers.queries.clear()

        providers.addTestRow(5, date = clock.now, value = "brand new") // _id > cursor
        providers.addTestRow(3, date = recent, value = "recent edited") // inside 48 h: re-read
        providers.addTestRow(2, date = old, value = "old edited") // outside 48 h: must NOT be re-read
        providers.db.execSQL("DELETE FROM test_rows WHERE _id = 4")
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        val specs = providers.queries
        assertThat(specs.map { it.selection }).containsExactly(
            "_id > ?",
            "date >= ? AND _id <= ?",
            null,
        ).inOrder()
        assertThat(specs[0].args).containsExactly("4")
        assertThat(
            specs[1].args,
        ).containsExactly((clock.now - TelephonyConfig.RECENT_WINDOW_MS).toString(), "4").inOrder()
        assertThat(specs[2].projection).containsExactly("_id")

        fun title(id: Long) = writer.items.getValue(SourceKey(Source.SMS, id)).title
        assertThat(title(5)).isEqualTo("brand new")
        assertThat(title(3)).isEqualTo("recent edited")
        assertThat(title(2)).isEqualTo("old two")
        assertThat(writer.items).doesNotContainKey(SourceKey(Source.SMS, 4))
        assertThat(result.deleted).isEqualTo(1)
        assertThat(writer.states.getValue(Source.SMS).cursor).isEqualTo(5L)
        assertThat(writer.states.getValue(Source.SMS).lastFullReconcile).isEqualTo(clock.now)
    }

    // item 6
    @Test
    fun `a run resumed after a simulated process death finishes the remaining rows and reconciles`() = runTest {
        providers.addTestRows(5_500)
        writer.states[Source.SMS] = previous(cursor = 5_000, lastFullReconcile = null)
        val result = indexer().run(Mode.INCREMENTAL, ProgressSink.NONE).sources.single()

        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        assertThat(result.written).isAtLeast(500)
        assertThat(writer.items.keys.map { it.sourceId }).containsAtLeastElementsIn((5_001L..5_500L).toList())
        assertThat(writer.reconciles).hasSize(1)
        assertThat(writer.reconciles.single().second.size).isEqualTo(5_500)
        assertThat(writer.states.getValue(Source.SMS).cursor).isEqualTo(5_500L)
        assertThat(writer.states.getValue(Source.SMS).lastFullReconcile).isEqualTo(clock.now)
    }

    // item 7
    @Test
    fun `a SecurityException after 600 rows becomes permission_revoked without reconcile or cursor save`() = runTest {
        providers.addTestRows(1_000)
        providers.failOn("content://test/rows", SecurityException("secret +919876543210"), afterRows = 600)
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()

        result.assertFailed("sms:permission_revoked:SecurityException")
        assertThat(writer.upsertCalls).containsExactly(500)
        assertThat(writer.reconciles).isEmpty()
        assertThat(writer.savedStates).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 8
    @Test
    fun `a null cursor becomes provider_unavailable`() = runTest {
        providers.addTestRows(3)
        providers.returnNullFor("content://test/rows")
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("sms:provider_unavailable:ProviderUnavailableException")
        assertThat(writer.reconciles).isEmpty()
        assertThat(writer.savedStates).isEmpty()
    }

    @Test
    fun `any other provider exception becomes provider_error`() = runTest {
        providers.addTestRows(3)
        providers.failOn("content://test/rows", IllegalArgumentException("bad"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("sms:provider_error:IllegalArgumentException")
        assertThat(writer.reconciles).isEmpty()
    }

    @Test
    fun `an exception while reading rows closes the cursor and becomes provider_error`() = runTest {
        providers.addTestRows(10)
        providers.failOn("content://test/rows", IllegalStateException(), afterRows = 4)
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("sms:provider_error:IllegalStateException")
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    @Test
    fun `a writer failure becomes provider_error with its class name`() = runTest {
        providers.addTestRows(3)
        writer.failNextUpsert(IllegalStateException("db"))
        indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
            .assertFailed("sms:provider_error:IllegalStateException")
        assertThat(writer.reconciles).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 9
    @Test
    fun `cancellation during the second batch propagates, closes cursors and skips reconcile`() = runTest {
        providers.addTestRows(1_600)
        val indexer = indexer()
        lateinit var run: kotlinx.coroutines.Deferred<*>
        val sink = FakeProgressSink { _, _ -> if (writer.upsertCalls.size == 2) run.cancel() }
        run = async { indexer.run(Mode.FULL, sink) }

        val failure = runCatching { run.await() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
        assertThat(writer.upsertCalls).containsExactly(500, 500).inOrder()
        assertThat(writer.reconciles).isEmpty()
        assertThat(writer.savedStates).isEmpty()
        assertThat(providers.allCursorsClosed()).isTrue()
    }

    // item 11
    @Test
    fun `logs never contain numbers, bodies or titles`() = runTest {
        providers.addTestRow(1, value = "secret-body-123")
        providers.addTestRow(2, date = null, value = "secret-body-456")
        indexer().run(Mode.FULL, ProgressSink.NONE)
        providers.failOn("content://test/rows", SecurityException("+91 98765 43210 secret-body-123"))
        indexer().run(Mode.FULL, ProgressSink.NONE)

        val logs = ShadowLog.getLogs().filter { it.tag.startsWith("SI.Tel") }
        assertThat(logs).hasSize(2)
        assertThat(logs.map { it.msg }.first()).isEqualTo(
            "run mode=FULL read=2 written=1 skipped={no_date=1} notes={} ms=0",
        )
        assertThat(logs.map { it.msg }.last()).isEqualTo(
            "run failed mode=FULL error=PERMISSION_REVOKED class=SecurityException read=0",
        )
        val text = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" }
        assertThat(text).doesNotContain("98765")
        assertThat(text).doesNotContain("9876543210")
        assertThat(text).doesNotContain("secret-body")
        assertThat(text).doesNotContain("row 1")
    }

    // item 12
    @Test
    fun `progress is non-decreasing and ends at the cursor total`() = runTest {
        providers.addTestRows(1_201)
        val sink = FakeProgressSink()
        indexer().run(Mode.FULL, sink)

        val done = sink.reports.map { it.first }
        assertThat(done).isInOrder()
        assertThat(sink.reports.map { it.second }.toSet()).containsExactly(1_201)
        assertThat(sink.reports.first().first).isEqualTo(500)
        assertThat(sink.reports.last()).isEqualTo(1_201 to 1_201)
    }

    @Test
    fun `INCREMENTAL progress totals the new rows and the recent window`() = runTest {
        providers.addTestRows(10, date = clock.now)
        writer.states[Source.SMS] = previous(cursor = 6, lastFullReconcile = 1L)
        val sink = FakeProgressSink()
        indexer().run(Mode.INCREMENTAL, sink)

        assertThat(sink.reports.map { it.first }).isInOrder()
        // 4 new rows (7..10) + 6 recent rows (1..6)
        assertThat(sink.reports.last()).isEqualTo(10 to 10)
    }

    // LLD §7.3 item 10
    @Test
    fun `contacts are resolved with full true on FULL before the first provider query`() = runTest {
        providers.addTestRows(3)
        indexer().run(Mode.FULL, ProgressSink.NONE)
        assertThat(contactCalls).containsExactly(true to 0)
    }

    @Test
    fun `contacts are resolved with full false on INCREMENTAL before the first provider query`() = runTest {
        providers.addTestRows(3)
        writer.states[Source.SMS] = previous(cursor = 3, lastFullReconcile = clock.now)
        indexer().run(Mode.INCREMENTAL, ProgressSink.NONE)
        assertThat(contactCalls).containsExactly(false to 0)
    }

    @Test
    fun `a Failed contacts outcome does not fail the run`() = runTest {
        providers.addTestRows(3)
        coEvery { contacts.ensureFresh(any()) } returns ContactsOutcome.Failed("SecurityException")
        val result = indexer().run(Mode.FULL, ProgressSink.NONE).sources.single()
        assertThat(result.succeeded).isTrue()
        assertThat(result.written).isEqualTo(3)
    }
}
