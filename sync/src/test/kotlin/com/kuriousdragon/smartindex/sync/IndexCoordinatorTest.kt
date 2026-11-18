@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kuriousdragon.smartindex.sync

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.sync.fakes.FakeFreeSpace
import com.kuriousdragon.smartindex.sync.fakes.FakeIndexWriter
import com.kuriousdragon.smartindex.sync.fakes.FakeRecordingMatcher
import com.kuriousdragon.smartindex.sync.fakes.FakeSourceIndexer
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncScheduler
import com.kuriousdragon.smartindex.sync.fakes.RunTracker
import com.kuriousdragon.smartindex.sync.work.ArmPolicy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class IndexCoordinatorTest {
    private lateinit var sched: TestCoroutineScheduler
    private val base = FakeSourceIndexer.RECONCILED + 1_000L
    private val now: () -> Long = { base + sched.currentTime }

    private val all = Group.entries.toSet()
    private val states = MutableStateFlow(stateOf(all))
    private val permissions = mockk<PermissionService> { every { state } returns states }
    private val writer = FakeIndexWriter()
    private val prefs = FakeSyncPrefs(enabled = true)
    private val scheduler = FakeSyncScheduler()
    private val freeSpace = FakeFreeSpace()
    private val matcher = FakeRecordingMatcher()
    private val tracker = RunTracker()
    private val statsFlow = MutableSharedFlow<IndexStats>(replay = 1)
    private val sinces = mutableListOf<Long>()
    private val query = mockk<IndexQuery>(relaxed = true) {
        every { stats() } returns statsFlow
        coEvery { recordingCandidates(any()) } answers {
            sinces += firstArg<Long>()
            RecordingInputs(emptyList(), emptyList(), emptyList())
        }
    }

    private val logs = mutableListOf<String>()
    private lateinit var previousSink: SiLog.Sink

    @Before
    fun setUp() {
        previousSink = SiLog.sink
        SiLog.sink = SiLog.Sink { _, _, message -> logs += message }
    }

    @After
    fun tearDown() {
        SiLog.sink = previousSink
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest {
        sched = testScheduler
        body()
    }

    private fun stateOf(usable: Set<Group>): Map<Group, GroupState> =
        Group.entries.associateWith { if (it in usable) GroupState.GRANTED else GroupState.DENIED }

    private fun usable(groups: Set<Group>) {
        states.value = stateOf(groups)
        prefs.grantedGroups = groups
    }

    /** Durable "FULL done recently" state for [sources], and a ledger equal to the current permissions. */
    private fun seedRecent(sources: Set<Source> = Source.entries.toSet(), cursor: Long = 7L) {
        prefs.grantedGroups = all
        for (s in sources) writer.states[s] = SyncState(s, cursor, null, base - 1_000L, null, null)
    }

    private fun idx(
        source: Source,
        requires: Set<Group> = when (source) {
            Source.MEDIA_STORE -> setOf(Group.PHOTOS_VIDEOS, Group.FILES)
            Source.CALL_LOG -> setOf(Group.CALL_LOG)
            else -> setOf(Group.MESSAGES)
        },
    ) = FakeSourceIndexer(setOf(source), requires, now, tracker)

    private fun TestScope.coordinator(vararg indexers: SourceIndexer, withMatcher: Boolean = false) =
        IndexCoordinator(
            indexers = indexers.toSet(),
            matcher = java.util.Optional.ofNullable(if (withMatcher) matcher else null),
            writer = writer,
            query = query,
            gate = PermissionGate(permissions, writer, prefs),
            prefs = prefs,
            scheduler = scheduler,
            freeSpace = freeSpace,
            clock = SyncClock { now() },
            scope = backgroundScope,
            io = StandardTestDispatcher(testScheduler),
        )

    private fun TestScope.record(c: IndexCoordinator): MutableList<IndexStatus> {
        val seen = mutableListOf<IndexStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.status.collect { seen += it } }
        return seen
    }

    private fun List<IndexStatus>.phases(): List<IndexPhase> =
        map { it.phase }.fold(emptyList()) { acc, p -> if (acc.lastOrNull() == p) acc else acc + p }

    // ---- single-flight, enablement, storage ----

    @Test
    fun `two concurrent syncs never overlap and the second plans after the first`() = test {
        seedRecent()
        val ix = idx(Source.SMS)
        val gate = CompletableDeferred<Unit>()
        ix.gate = gate
        val c = coordinator(ix)
        val first = async { c.sync(SyncReason.PERIODIC) }
        val second = async { c.sync(SyncReason.OBSERVER) }
        runCurrent()
        assertThat(ix.calls).isEqualTo(1)
        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        assertThat(ix.calls).isEqualTo(2)
        assertThat(ix.starts[1]).isAtLeast(ix.ends[0])
        assertThat(tracker.max).isEqualTo(1)
    }

    @Test
    fun `not enabled skips without calling anything`() = test {
        prefs.enabled = false
        val ix = idx(Source.SMS)
        val c = coordinator(ix)
        val seen = record(c)
        val out = c.sync(SyncReason.PERIODIC)
        assertThat(out.skipped).isEqualTo(SkipReason.NOT_ENABLED)
        assertThat(ix.calls).isEqualTo(0)
        assertThat(seen.phases()).containsExactly(IndexPhase.IDLE)
        assertThat(scheduler.arms).isEmpty()
    }

    @Test
    fun `storage low skips, writes nothing, shows STORAGE_FULL and clears on the next passing run`() = test {
        freeSpace.bytes = IndexConfig.MIN_FREE_BYTES - 1
        val ix = idx(Source.SMS)
        val c = coordinator(ix)
        val out = c.sync(SyncReason.PERIODIC)
        assertThat(out.skipped).isEqualTo(SkipReason.STORAGE_LOW)
        assertThat(ix.calls).isEqualTo(0)
        assertThat(writer.saves).isEmpty()
        assertThat(writer.purges).isEmpty()
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.STORAGE_FULL)
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.IDLE)

        freeSpace.bytes = IndexConfig.MIN_FREE_BYTES
        c.sync(SyncReason.PERIODIC)
        assertThat(ix.calls).isEqualTo(1)
        assertThat(c.status.value.error).isNull()
    }

    @Test
    fun `nothing usable skips and ends idle`() = test {
        usable(emptySet())
        val ix = idx(Source.SMS)
        val c = coordinator(ix)
        val out = c.sync(SyncReason.PERIODIC)
        assertThat(out.skipped).isEqualTo(SkipReason.NOTHING_USABLE)
        assertThat(ix.calls).isEqualTo(0)
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.IDLE)
        assertThat(c.status.value.lastRun).isNull()
    }

    // ---- plan ----

    private suspend fun TestScope.modeFor(state: SyncState?): Mode {
        prefs.grantedGroups = all
        writer.states.clear()
        if (state != null) writer.states[Source.SMS] = state
        val ix = idx(Source.SMS)
        coordinator(ix).sync(SyncReason.PERIODIC)
        return ix.modes.single()
    }

    @Test
    fun `no state plans FULL`() = test {
        assertThat(modeFor(null)).isEqualTo(Mode.FULL)
    }

    @Test
    fun `null lastFullReconcile plans FULL`() = test {
        assertThat(modeFor(SyncState(Source.SMS, 5, null, null, null, null))).isEqualTo(Mode.FULL)
    }

    @Test
    fun `lastFullReconcile older than seven days plans FULL`() = test {
        val old = base - IndexConfig.FULL_RESYNC_INTERVAL_MS - 1
        assertThat(modeFor(SyncState(Source.SMS, 5, null, old, null, null))).isEqualTo(Mode.FULL)
    }

    @Test
    fun `recent lastFullReconcile plans INCREMENTAL`() = test {
        val recent = base - IndexConfig.FULL_RESYNC_INTERVAL_MS
        assertThat(modeFor(SyncState(Source.SMS, 5, null, recent, null, null))).isEqualTo(Mode.INCREMENTAL)
    }

    @Test
    fun `incremental plan runs in SMS, MEDIA_STORE, MMS, CALL_LOG order`() = test {
        seedRecent()
        val ix = Source.entries.map { idx(it).apply { delayMs = 10 } }
        coordinator(*ix.toTypedArray()).sync(SyncReason.PERIODIC)
        assertThat(tracker.startOrder)
            .containsExactly(Source.SMS, Source.MEDIA_STORE, Source.MMS, Source.CALL_LOG).inOrder()
    }

    @Test
    fun `FULL indexers go first, then plan order`() = test {
        seedRecent(Source.entries.toSet() - Source.CALL_LOG)
        val ix = Source.entries.map { idx(it).apply { delayMs = 10 } }
        coordinator(*ix.toTypedArray()).sync(SyncReason.PERIODIC)
        assertThat(tracker.startOrder)
            .containsExactly(Source.CALL_LOG, Source.SMS, Source.MEDIA_STORE, Source.MMS).inOrder()
    }

    // ---- gating ----

    @Test
    fun `indexers without a usable source or required group are not called`() = test {
        usable(setOf(Group.MESSAGES))
        writer.states[Source.SMS] = SyncState(Source.SMS, 1, null, base - 1, null, null)
        val sms = idx(Source.SMS)
        val media = idx(Source.MEDIA_STORE)
        val call = idx(Source.CALL_LOG)
        coordinator(sms, media, call).sync(SyncReason.PERIODIC)
        assertThat(sms.calls).isEqualTo(1)
        assertThat(media.calls).isEqualTo(0)
        assertThat(call.calls).isEqualTo(0)
    }

    @Test
    fun `an indexer whose required groups are all unusable is skipped`() = test {
        usable(setOf(Group.PHOTOS_VIDEOS))
        val ix = idx(Source.MEDIA_STORE, requires = setOf(Group.CALL_LOG))
        val out = coordinator(ix).sync(SyncReason.PERIODIC)
        assertThat(ix.calls).isEqualTo(0)
        assertThat(out.skipped).isEqualTo(SkipReason.NOTHING_USABLE)
    }

    @Test
    fun `a result for a source that is not active is ignored`() = test {
        usable(setOf(Group.PHOTOS_VIDEOS))
        writer.states[Source.MEDIA_STORE] = SyncState(Source.MEDIA_STORE, 1, null, base - 1, null, null)
        val ix = FakeSourceIndexer(setOf(Source.MEDIA_STORE, Source.SMS), setOf(Group.PHOTOS_VIDEOS), now, tracker)
        val out = coordinator(ix).sync(SyncReason.PERIODIC)
        assertThat(out.results.map { it.source }).containsExactly(Source.MEDIA_STORE)
        assertThat(writer.saves.map { it.source }.toSet()).containsExactly(Source.MEDIA_STORE)
    }

    // ---- parallelism, isolation, errors ----

    @Test
    fun `four indexers never run more than two at once`() = test {
        seedRecent()
        val ix = Source.entries.map { idx(it).apply { delayMs = 100 } }
        coordinator(*ix.toTypedArray()).sync(SyncReason.PERIODIC)
        assertThat(tracker.max).isEqualTo(IndexConfig.MAX_PARALLEL_INDEXERS)
        assertThat(ix.all { it.calls == 1 }).isTrue()
    }

    @Test
    fun `a throwing indexer is isolated, keeps its cursor and shows SOURCE_FAILED`() = test {
        seedRecent(cursor = 99)
        val sms = idx(Source.SMS)
        val call = idx(Source.CALL_LOG).apply { throwable = IllegalStateException("boom") }
        val c = coordinator(sms, call)
        val out = c.sync(SyncReason.PERIODIC)
        val callState = writer.states.getValue(Source.CALL_LOG)
        assertThat(callState.lastError).isEqualTo("IllegalStateException")
        assertThat(callState.lastRun).isEqualTo(base)
        assertThat(callState.cursor).isEqualTo(99)
        assertThat(writer.states.getValue(Source.SMS).lastError).isNull()
        assertThat(writer.states.getValue(Source.SMS).lastRun).isEqualTo(base)
        assertThat(out.results.single { it.source == Source.CALL_LOG }.succeeded).isFalse()
        val error = c.status.value.error
        assertThat(error?.kind).isEqualTo(IndexError.Kind.SOURCE_FAILED)
        assertThat(error?.sources).containsExactly(Source.CALL_LOG)
    }

    @Test
    fun `a later successful run clears the source failure`() = test {
        seedRecent()
        val call = idx(Source.CALL_LOG).apply { throwable = IllegalStateException() }
        val c = coordinator(call)
        c.sync(SyncReason.PERIODIC)
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.SOURCE_FAILED)
        call.throwable = null
        c.sync(SyncReason.PERIODIC)
        assertThat(c.status.value.error).isNull()
    }

    @Test
    fun `a composite storage-full code shows STORAGE_FULL`() = test {
        seedRecent()
        val sms = idx(Source.SMS).apply {
            result = { m -> RunResult.failure(sources, m, "sms:storage_full:SQLiteFullException") }
        }
        val c = coordinator(sms)
        c.sync(SyncReason.PERIODIC)
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.STORAGE_FULL)
        assertThat(c.status.value.error?.sources).isEmpty()
    }

    @Test
    fun `a missing result writes nothing and logs`() = test {
        seedRecent()
        val sms = idx(Source.SMS).apply { result = { RunResult(emptyList()) } }
        coordinator(sms).sync(SyncReason.PERIODIC)
        assertThat(writer.saves).isEmpty()
        assertThat(writer.states.getValue(Source.SMS).lastRun).isNull()
        assertThat(logs.any { it.contains("no result source=SMS") }).isTrue()
    }

    @Test
    fun `a duplicate result keeps the first and logs`() = test {
        seedRecent()
        val sms = idx(Source.SMS).apply {
            result = { m ->
                RunResult(
                    listOf(SourceRunResult(Source.SMS, m, written = 1), SourceRunResult.failed(Source.SMS, m, "X")),
                )
            }
        }
        val out = coordinator(sms).sync(SyncReason.PERIODIC)
        assertThat(out.results).hasSize(1)
        assertThat(writer.states.getValue(Source.SMS).lastError).isNull()
        assertThat(logs.any { it.contains("duplicate result source=SMS") }).isTrue()
    }

    @Test
    fun `a failing sync state save marks the source failed and the run continues`() = test {
        seedRecent()
        writer.throwOnSave = true
        val sms = idx(Source.SMS)
        val call = idx(Source.CALL_LOG)
        val c = coordinator(sms, call)
        val out = c.sync(SyncReason.PERIODIC)
        assertThat(out.results).hasSize(2)
        assertThat(call.calls).isEqualTo(1)
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.SOURCE_FAILED)
        assertThat(c.status.value.error?.sources).containsExactly(Source.SMS, Source.CALL_LOG)
    }

    // ---- column ownership, cancellation ----

    @Test
    fun `coordinator preserves the cursor, storeVersion and lastFullReconcile an indexer wrote`() = test {
        val sms = idx(Source.SMS).apply { writesCursor = writer }
        coordinator(sms).sync(SyncReason.REINDEX)
        val s = writer.states.getValue(Source.SMS)
        assertThat(s.cursor).isEqualTo(FakeSourceIndexer.CURSOR)
        assertThat(s.storeVersion).isEqualTo(FakeSourceIndexer.STORE_VERSION)
        assertThat(s.lastFullReconcile).isEqualTo(FakeSourceIndexer.RECONCILED)
        assertThat(s.lastRun).isEqualTo(base)
        assertThat(s.lastError).isNull()
    }

    @Test
    fun `cancelling mid-run rethrows, keeps finished sources and ends idle`() = test {
        seedRecent()
        val sms = idx(Source.SMS)
        val call = idx(Source.CALL_LOG).apply { gate = CompletableDeferred() }
        val c = coordinator(sms, call)
        val job = launch { c.sync(SyncReason.PERIODIC) }
        runCurrent()
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.INDEXING)
        job.cancelAndJoin()
        assertThat(job.isCancelled).isTrue()
        assertThat(writer.states.getValue(Source.SMS).lastRun).isEqualTo(base)
        assertThat(writer.states.getValue(Source.CALL_LOG).lastRun).isNull()
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.IDLE)
        assertThat(c.status.value.isCatchUp).isFalse()
        assertThat(scheduler.arms).isEmpty()
    }

    // ---- matching ----

    private fun matchSetup(): Pair<FakeSourceIndexer, FakeSourceIndexer> =
        idx(Source.CALL_LOG) to idx(Source.MEDIA_STORE)

    @Test
    fun `a FULL run replaces all links and never the incremental replace`() = test {
        val (call, media) = matchSetup()
        val out = coordinator(call, media, withMatcher = true).sync(SyncReason.REINDEX)
        assertThat(out.matched).isTrue()
        assertThat(writer.replaceAllCalls).containsExactly(matcher.links)
        assertThat(writer.linkCalls).isEmpty()
        assertThat(sinces).containsExactly(0L)
        assertThat(prefs.lastMatchRun).isEqualTo(base)
    }

    @Test
    fun `a never-matched INCREMENTAL run replaces all links with since 0`() = test {
        seedRecent()
        val (call, media) = matchSetup()
        coordinator(call, media, withMatcher = true).sync(SyncReason.PERIODIC)
        assertThat(call.modes).containsExactly(Mode.INCREMENTAL)
        assertThat(writer.replaceAllCalls).hasSize(1)
        assertThat(writer.linkCalls).isEmpty()
        assertThat(sinces).containsExactly(0L)
    }

    @Test
    fun `an INCREMENTAL run with a previous match replaces incrementally from lastMatchRun minus overlap`() = test {
        seedRecent()
        val last = base - 3_600_000L
        prefs.lastMatchRun = last
        val (call, media) = matchSetup()
        coordinator(call, media, withMatcher = true).sync(SyncReason.PERIODIC)
        assertThat(writer.linkCalls).containsExactly(matcher.links)
        assertThat(writer.replaceAllCalls).isEmpty()
        assertThat(sinces).containsExactly(last - IndexConfig.RECORDING_MATCH_OVERLAP_MS)
        assertThat(prefs.lastMatchRun).isEqualTo(base)
    }

    @Test
    fun `no matching when nothing changed`() = test {
        seedRecent()
        prefs.lastMatchRun = base - 1
        val call = idx(Source.CALL_LOG).apply {
            result = { m -> RunResult(listOf(SourceRunResult(Source.CALL_LOG, m))) }
        }
        val media = idx(Source.MEDIA_STORE).apply {
            result = { m -> RunResult(listOf(SourceRunResult(Source.MEDIA_STORE, m))) }
        }
        val out = coordinator(call, media, withMatcher = true).sync(SyncReason.PERIODIC)
        assertThat(out.matched).isFalse()
        assertThat(matcher.inputs).isEmpty()
    }

    @Test
    fun `a change in SMS alone does not trigger matching`() = test {
        seedRecent()
        prefs.lastMatchRun = base - 1
        coordinator(idx(Source.SMS), withMatcher = true).sync(SyncReason.PERIODIC)
        assertThat(matcher.inputs).isEmpty()
    }

    @Test
    fun `a failed source with written rows does not trigger matching`() = test {
        seedRecent()
        prefs.lastMatchRun = base - 1
        val call = idx(Source.CALL_LOG).apply {
            result = { m -> RunResult(listOf(SourceRunResult(Source.CALL_LOG, m, written = 3, error = "X"))) }
        }
        coordinator(call, withMatcher = true).sync(SyncReason.PERIODIC)
        assertThat(matcher.inputs).isEmpty()
    }

    @Test
    fun `no matching without a matcher`() = test {
        val (call, media) = matchSetup()
        val out = coordinator(call, media, withMatcher = false).sync(SyncReason.REINDEX)
        assertThat(out.matched).isFalse()
        assertThat(writer.replaceAllCalls).isEmpty()
        assertThat(prefs.lastMatchRun).isNull()
    }

    @Test
    fun `no matching when FILES or CALL_LOG is not usable`() = test {
        for (missing in listOf(Group.FILES, Group.CALL_LOG)) {
            usable(all - missing)
            val (call, media) = matchSetup()
            coordinator(call, media, withMatcher = true).sync(SyncReason.REINDEX)
        }
        assertThat(matcher.inputs).isEmpty()
    }

    @Test
    fun `a matcher failure shows LINKING_FAILED and keeps lastMatchRun`() = test {
        seedRecent()
        val previous = base - 5_000L
        prefs.lastMatchRun = previous
        matcher.throwable = IllegalStateException("x")
        val (call, media) = matchSetup()
        val c = coordinator(call, media, withMatcher = true)
        val out = c.sync(SyncReason.PERIODIC)
        assertThat(out.linkError).isEqualTo("IllegalStateException")
        assertThat(out.matched).isFalse()
        assertThat(prefs.lastMatchRun).isEqualTo(previous)
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.LINKING_FAILED)
        assertThat(scheduler.arms).hasSize(1)
    }

    @Test
    fun `a failing link write shows LINKING_FAILED`() = test {
        seedRecent()
        prefs.lastMatchRun = null
        writer.throwOnLinks = true
        val (call, media) = matchSetup()
        val c = coordinator(call, media, withMatcher = true)
        c.sync(SyncReason.PERIODIC)
        assertThat(prefs.lastMatchRun).isNull()
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.LINKING_FAILED)
    }

    // ---- status sequence ----

    @Test
    fun `a FULL run emits IDLE, PREPARING, INDEXING, LINKING, IDLE with lastRun set`() = test {
        val (call, media) = matchSetup()
        media.delayMs = 250
        val c = coordinator(call, media, withMatcher = true)
        val seen = record(c)
        c.sync(SyncReason.REINDEX)
        assertThat(seen.phases()).containsExactly(
            IndexPhase.IDLE,
            IndexPhase.PREPARING,
            IndexPhase.INDEXING,
            IndexPhase.LINKING,
            IndexPhase.IDLE,
        ).inOrder()
        assertThat(seen.filter { it.phase == IndexPhase.INDEXING }.map { it.mode }.toSet()).containsExactly(Mode.FULL)
        val end = seen.last()
        assertThat(end.lastRun).isEqualTo(base + 250)
        assertThat(end.mode).isNull()
        assertThat(end.done).isEqualTo(0)
        assertThat(end.total).isEqualTo(0)
        assertThat(end.error).isNull()
    }

    @Test
    fun `an INCREMENTAL run under two seconds never sets isCatchUp`() = test {
        seedRecent()
        val sms = idx(Source.SMS).apply { delayMs = 1_999 }
        val c = coordinator(sms)
        val seen = record(c)
        c.sync(SyncReason.PERIODIC)
        assertThat(seen.any { it.isCatchUp }).isFalse()
        assertThat(seen.filter { it.phase == IndexPhase.INDEXING }.map { it.mode }.toSet())
            .containsExactly(Mode.INCREMENTAL)
    }

    @Test
    fun `an INCREMENTAL run sets isCatchUp after two seconds and clears it at IDLE`() = test {
        seedRecent()
        val gate = CompletableDeferred<Unit>()
        val sms = idx(Source.SMS).apply { this.gate = gate }
        val c = coordinator(sms)
        val job = launch { c.sync(SyncReason.PERIODIC) }
        runCurrent()
        advanceTimeBy(IndexConfig.CATCH_UP_PROGRESS_DELAY_MS - 1)
        runCurrent()
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.INDEXING)
        assertThat(c.status.value.isCatchUp).isFalse()
        advanceTimeBy(1)
        runCurrent()
        assertThat(c.status.value.isCatchUp).isTrue()
        gate.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertThat(c.status.value.isCatchUp).isFalse()
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.IDLE)
    }

    @Test
    fun `a FULL run never sets isCatchUp`() = test {
        val sms = idx(Source.SMS).apply { delayMs = 3_000 }
        val c = coordinator(sms)
        val seen = record(c)
        c.sync(SyncReason.REINDEX)
        assertThat(seen.any { it.isCatchUp }).isFalse()
    }

    @Test
    fun `progress updates done and total only while INDEXING`() = test {
        seedRecent()
        val gate = CompletableDeferred<Unit>()
        val sms = idx(Source.SMS).apply {
            this.gate = gate
            reportProgress = 5 to 10
        }
        val c = coordinator(sms)
        val job = launch { c.sync(SyncReason.PERIODIC) }
        runCurrent()
        assertThat(c.status.value.done).isEqualTo(5)
        assertThat(c.status.value.total).isEqualTo(10)
        gate.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertThat(c.status.value.done).isEqualTo(0)
        sms.lastSink!!.report(7, 9)
        assertThat(c.status.value.done).isEqualTo(0)
        assertThat(c.status.value.total).isEqualTo(0)
    }

    // ---- init, stats, triggers ----

    @Test
    fun `init restores lastRun and error from sync_state`() = test {
        writer.states[Source.SMS] = SyncState(Source.SMS, 0, null, null, 100L, null)
        writer.states[Source.CALL_LOG] = SyncState(Source.CALL_LOG, 0, null, null, 200L, "Boom")
        val c = coordinator()
        runCurrent()
        assertThat(c.status.value.lastRun).isEqualTo(200L)
        assertThat(c.status.value.error?.kind).isEqualTo(IndexError.Kind.SOURCE_FAILED)
        assertThat(c.status.value.error?.sources).containsExactly(Source.CALL_LOG)
        assertThat(c.status.value.phase).isEqualTo(IndexPhase.IDLE)
    }

    @Test
    fun `stats follow IndexQuery stats`() = test {
        val c = coordinator()
        runCurrent()
        assertThat(c.status.value.stats).isNull()
        val s1 = IndexStats(mapOf(ItemType.PHOTO to 3), recordingsLinked = 1, totalItems = 3)
        statsFlow.emit(s1)
        runCurrent()
        assertThat(c.status.value.stats).isEqualTo(s1)
        val s2 = s1.copy(totalItems = 4)
        statsFlow.emit(s2)
        runCurrent()
        assertThat(c.status.value.stats).isEqualTo(s2)
    }

    @Test
    fun `triggers are armed with REPLACE when the gate changed`() = test {
        coordinator(idx(Source.SMS)).sync(SyncReason.REINDEX)
        assertThat(scheduler.arms).containsExactly(all to ArmPolicy.REPLACE)
    }

    @Test
    fun `triggers are armed with KEEP when the gate did not change`() = test {
        seedRecent()
        coordinator(idx(Source.SMS)).sync(SyncReason.PERIODIC)
        assertThat(scheduler.arms).containsExactly(all to ArmPolicy.KEEP)
    }
}
