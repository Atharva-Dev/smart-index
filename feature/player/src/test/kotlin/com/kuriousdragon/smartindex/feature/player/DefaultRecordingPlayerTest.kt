package com.kuriousdragon.smartindex.feature.player

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.ItemDetail
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRecordingPlayerTest {
    private class Rig(val scope: TestScope) {
        val engines = mutableListOf<FakePlaybackEngine>()
        val resolver = FakeItemUriResolver()
        val missing = mutableSetOf<Long>()
        val events = mutableListOf<PlayerEvent>()
        val index = mockk<IndexQuery>()
        val player = DefaultRecordingPlayer(
            engines = { FakePlaybackEngine().also { engines += it } },
            index = index,
            resolver = resolver,
            main = StandardTestDispatcher(scope.testScheduler),
        )
        val engine get() = engines.last()

        init {
            coEvery { index.item(any()) } coAnswers {
                val id = firstArg<Long>()
                if (id in missing) null else detail(id)
            }
        }

        fun collectEvents(): Job = scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            player.events.collect { events += it }
        }

        private fun detail(id: Long) = ItemDetail(
            id = id, key = SourceKey(Source.MEDIA_STORE, id), type = ItemType.RECORDING, title = "t", primaryTs = 0,
            dates = emptyMap(), folder = null, mime = null, ext = null, sizeBytes = null,
            durationMs = null, contentUri = "content://x/$id", numberRaw = null, contactName = null,
            direction = null, msgKind = null, threadId = null, hasAttachment = false, body = null,
            linkedRecordingId = null, linkedCallId = null,
        )
    }

    private fun req(id: Long = 1) = PlayRequest(id, "Title $id", "Sub $id")

    private fun TestScope.playAndStart(r: Rig, id: Long = 1, durationMs: Long = 10_000): FakePlaybackEngine {
        r.player.play(req(id))
        runCurrent()
        val e = r.engine
        e.current!!.onReady(durationMs)
        e.current!!.onIsPlayingChanged(true)
        return e
    }

    @Test
    fun loadPlayPauseResume() = runTest {
        val r = Rig(this)
        r.player.play(req())
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.LOADING)
        assertThat(r.player.state.value.title).isEqualTo("Title 1")
        assertThat(r.player.state.value.isVisible).isTrue()
        runCurrent()
        assertThat(r.engines).hasSize(1)
        assertThat(r.engine.started).hasSize(1)
        assertThat(r.resolver.calls).containsExactly(ItemType.RECORDING to "content://x/1")
        r.engine.current!!.onReady(10_000)
        assertThat(r.player.state.value.durationMs).isEqualTo(10_000)
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.LOADING)
        r.engine.current!!.onIsPlayingChanged(true)
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PLAYING)
        assertThat(r.player.state.value.isPlaying).isTrue()
        r.player.togglePlayPause()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PAUSED)
        assertThat(r.engine.pauses).isEqualTo(1)
        r.player.togglePlayPause()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PLAYING)
        assertThat(r.engine.resumes).isEqualTo(1)
        r.player.close()
    }

    @Test
    fun switchingRecordingRestartsAndResetsState() = runTest {
        val r = Rig(this)
        val e = playAndStart(r, 1)
        e.positionMs = 4_000
        advanceTimeBy(300)
        assertThat(r.player.state.value.positionMs).isEqualTo(4_000)
        r.player.play(req(2))
        val s = r.player.state.value
        assertThat(s.phase).isEqualTo(PlayerPhase.LOADING)
        assertThat(s.recordingId).isEqualTo(2)
        assertThat(s.positionMs).isEqualTo(0)
        assertThat(s.durationMs).isEqualTo(0)
        runCurrent()
        assertThat(e.started).hasSize(2)
        r.player.close()
    }

    @Test
    fun sameIdWhilePausedResumesPlayingIsNoOpErrorRetries() = runTest {
        val r = Rig(this)
        val e = playAndStart(r, 1)
        r.player.play(req(1))
        runCurrent()
        assertThat(e.started).hasSize(1)
        assertThat(e.resumes).isEqualTo(0)
        r.player.pause()
        r.player.play(req(1))
        assertThat(e.resumes).isEqualTo(1)
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PLAYING)
        e.current!!.onError()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.ERROR)
        r.player.play(req(1))
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.LOADING)
        runCurrent()
        assertThat(r.engines).hasSize(2)
        r.player.close()
    }

    private fun TestScope.assertFailsOnce(r: Rig) {
        val job = r.collectEvents()
        r.player.play(req())
        runCurrent()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.ERROR)
        assertThat(r.events).containsExactly(PlayerEvent.Failed)
        assertThat(r.engines).isEmpty()
        job.cancel()
    }

    @Test
    fun missingItemIsError() = runTest {
        val r = Rig(this)
        r.missing += 1
        assertFailsOnce(r)
    }

    @Test
    fun unreadyTargetsAreError() = runTest {
        for (target in listOf(OpenTarget.Missing, OpenTarget.NoAccess, OpenTarget.NotAFile)) {
            val r = Rig(this)
            r.resolver.result = target
            assertFailsOnce(r)
        }
    }

    @Test
    fun engineErrorReleasesAndErrors() = runTest {
        val r = Rig(this)
        val job = r.collectEvents()
        val e = playAndStart(r)
        e.current!!.onError()
        assertThat(e.released).isTrue()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.ERROR)
        runCurrent()
        assertThat(r.events).containsExactly(PlayerEvent.Failed)
        job.cancel()
    }

    @Test
    fun endedResetsPositionAndPauses() = runTest {
        val r = Rig(this)
        val e = playAndStart(r)
        e.positionMs = 9_000
        advanceTimeBy(300)
        e.current!!.onEnded()
        val s = r.player.state.value
        assertThat(s.phase).isEqualTo(PlayerPhase.PAUSED)
        assertThat(s.positionMs).isEqualTo(0)
        assertThat(e.seeks).contains(0L)
        r.player.togglePlayPause()
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PLAYING)
        r.player.close()
    }

    @Test
    fun audioFocusLossPauses() = runTest {
        val r = Rig(this)
        val e = playAndStart(r)
        e.current!!.onIsPlayingChanged(false)
        assertThat(r.player.state.value.phase).isEqualTo(PlayerPhase.PAUSED)
        r.player.close()
    }

    @Test
    fun seekIsClampedAndIgnoredWhileLoading() = runTest {
        val r = Rig(this)
        r.player.play(req())
        r.player.seekTo(500)
        runCurrent()
        assertThat(r.engine.seeks).isEmpty()
        assertThat(r.player.state.value.positionMs).isEqualTo(0)
        r.engine.current!!.onReady(10_000)
        r.engine.current!!.onIsPlayingChanged(true)
        r.player.seekTo(50_000)
        assertThat(r.player.state.value.positionMs).isEqualTo(10_000)
        r.player.seekTo(-5)
        assertThat(r.player.state.value.positionMs).isEqualTo(0)
        r.player.seekBy(3_000)
        assertThat(r.player.state.value.positionMs).isEqualTo(3_000)
        assertThat(r.engine.seeks).containsExactly(10_000L, 0L, 3_000L).inOrder()
        r.player.close()
    }

    @Test
    fun pollerRunsOnlyWhilePlaying() = runTest {
        val r = Rig(this)
        val e = playAndStart(r)
        e.positionMs = 1_000
        advanceTimeBy(260)
        assertThat(r.player.state.value.positionMs).isEqualTo(1_000)
        r.player.pause()
        e.positionMs = 2_000
        advanceTimeBy(1_000)
        assertThat(r.player.state.value.positionMs).isEqualTo(1_000)
        r.player.togglePlayPause()
        advanceTimeBy(10)
        assertThat(r.player.state.value.positionMs).isEqualTo(2_000)
        r.player.close()
    }

    @Test
    fun secondPlayCancelsFirstResolve() = runTest {
        val r = Rig(this)
        r.resolver.gate = CompletableDeferred()
        r.player.play(req(1))
        runCurrent()
        r.player.play(req(2))
        runCurrent()
        r.resolver.gate!!.complete(Unit)
        runCurrent()
        assertThat(r.engines).hasSize(1)
        assertThat(r.engine.started).hasSize(1)
        assertThat(r.player.state.value.recordingId).isEqualTo(2)
        r.player.close()
    }

    @Test
    fun closeReleasesAndNextPlayCreatesNewEngine() = runTest {
        val r = Rig(this)
        val first = playAndStart(r)
        r.player.close()
        assertThat(first.released).isTrue()
        assertThat(r.player.state.value).isEqualTo(PlayerUiState())
        assertThat(r.player.state.value.isVisible).isFalse()
        r.player.play(req())
        runCurrent()
        assertThat(r.engines).hasSize(2)
        assertThat(r.engines[1]).isNotSameInstanceAs(first)
        r.player.close()
    }
}
