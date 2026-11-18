package com.kuriousdragon.smartindex.feature.player

import android.net.Uri
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.player.di.PlayerMain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The player state machine (Home & Search UI LLD §5.6). Everything runs on [main]; the engine is created lazily
 * and released on close and on every error.
 */
@Singleton
class DefaultRecordingPlayer @Inject constructor(
    private val engines: PlaybackEngineFactory,
    private val index: IndexQuery,
    private val resolver: ItemUriResolver,
    @PlayerMain main: CoroutineDispatcher,
) : RecordingPlayer, PlaybackEngine.Listener {
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val _state = MutableStateFlow(PlayerUiState())
    private val _events = Channel<PlayerEvent>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var engine: PlaybackEngine? = null
    private var request: PlayRequest? = null
    private var resolveJob: Job? = null
    private var pollJob: Job? = null

    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    override val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    override fun play(request: PlayRequest) {
        val current = _state.value
        if (current.recordingId == request.recordingId) {
            when (current.phase) {
                PlayerPhase.PLAYING, PlayerPhase.LOADING -> return

                PlayerPhase.PAUSED -> {
                    resumePlayback()
                    return
                }

                PlayerPhase.ERROR, PlayerPhase.CLOSED -> Unit
            }
        }
        load(request)
    }

    override fun togglePlayPause() {
        when (_state.value.phase) {
            PlayerPhase.PLAYING -> pause()
            PlayerPhase.PAUSED -> resumePlayback()
            else -> Unit
        }
    }

    override fun pause() {
        if (_state.value.phase != PlayerPhase.PLAYING) return
        stopPoller()
        engine?.pause()
        setPhase(PlayerPhase.PAUSED)
    }

    override fun seekTo(positionMs: Long) {
        val phase = _state.value.phase
        if (phase != PlayerPhase.PLAYING && phase != PlayerPhase.PAUSED) return
        val target = positionMs.coerceIn(0, _state.value.durationMs)
        engine?.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    override fun seekBy(deltaMs: Long) {
        seekTo(_state.value.positionMs + deltaMs)
    }

    override fun retry() {
        if (_state.value.phase != PlayerPhase.ERROR) return
        request?.let(::load)
    }

    override fun close() {
        resolveJob?.cancel()
        stopPoller()
        releaseEngine()
        request = null
        _state.value = PlayerUiState()
        SiLog.d(TAG, "player phase=CLOSED")
    }

    // --- PlaybackEngine.Listener -------------------------------------------------------------

    override fun onReady(durationMs: Long) {
        if (engine == null) return
        _state.update { it.copy(durationMs = durationMs) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val phase = _state.value.phase
        if (engine == null) return
        if (isPlaying) {
            if (phase == PlayerPhase.LOADING || phase == PlayerPhase.PAUSED) {
                setPhase(PlayerPhase.PLAYING)
                startPoller()
            }
        } else if (phase == PlayerPhase.PLAYING) {
            stopPoller()
            setPhase(PlayerPhase.PAUSED)
        }
    }

    override fun onEnded() {
        val e = engine ?: return
        stopPoller()
        e.seekTo(0)
        e.pause()
        _state.update { it.copy(phase = PlayerPhase.PAUSED, positionMs = 0) }
        SiLog.d(TAG, "player phase=PAUSED ended")
    }

    override fun onError() {
        if (engine == null) return
        fail()
    }

    // --- internals ---------------------------------------------------------------------------

    private fun load(request: PlayRequest) {
        this.request = request
        resolveJob?.cancel()
        stopPoller()
        engine?.pause()
        _state.value = PlayerUiState(
            phase = PlayerPhase.LOADING,
            recordingId = request.recordingId,
            title = request.title,
            subtitle = request.subtitle,
        )
        SiLog.d(TAG, "player phase=LOADING")
        resolveJob = scope.launch {
            val uri = try {
                val detail = index.item(request.recordingId)
                val target = detail?.let { resolver.resolve(ItemType.RECORDING, it.contentUri) }
                (target as? OpenTarget.Ready)?.uri
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SiLog.w(TAG, "resolve failed", e)
                null
            }
            if (!isActive) return@launch
            if (uri == null) {
                fail()
            } else {
                startEngine(uri)
            }
        }
    }

    private fun startEngine(uri: Uri) {
        val e = engine ?: engines.create().also {
            it.setListener(this)
            engine = it
        }
        e.start(uri)
    }

    private fun resumePlayback() {
        engine?.resume()
        setPhase(PlayerPhase.PLAYING)
        startPoller()
    }

    private fun fail() {
        resolveJob?.cancel()
        stopPoller()
        releaseEngine()
        setPhase(PlayerPhase.ERROR)
        _events.trySend(PlayerEvent.Failed)
    }

    private fun releaseEngine() {
        val e = engine ?: return
        engine = null
        e.release()
    }

    private fun setPhase(phase: PlayerPhase) {
        _state.update { it.copy(phase = phase) }
        SiLog.d(TAG, "player phase=$phase")
    }

    private fun startPoller() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                val e = engine ?: break
                _state.update { it.copy(positionMs = e.positionMs.coerceIn(0, it.durationMs)) }
                delay(POLL_MS)
            }
        }
    }

    private fun stopPoller() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val TAG = "Player"
        const val POLL_MS = 250L
    }
}
