package com.kuriousdragon.smartindex.feature.player

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What to play. The texts are built by the caller (UI strings). */
data class PlayRequest(val recordingId: Long, val title: String, val subtitle: String)

enum class PlayerPhase { CLOSED, LOADING, PLAYING, PAUSED, ERROR }

/** [durationMs] is 0 until known. */
@Immutable
data class PlayerUiState(
    val phase: PlayerPhase = PlayerPhase.CLOSED,
    val recordingId: Long? = null,
    val title: String = "",
    val subtitle: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val isVisible get() = phase != PlayerPhase.CLOSED
    val isPlaying get() = phase == PlayerPhase.PLAYING
}

sealed interface PlayerEvent {
    data object Failed : PlayerEvent
}

/** In-app playback of one call recording at a time (FR-012, ADR-011). */
interface RecordingPlayer {
    val state: StateFlow<PlayerUiState>

    /** One-shot events, buffered 1. */
    val events: Flow<PlayerEvent>

    /** Starts or switches to the recording. Same recording and PAUSED resumes; PLAYING is a no-op; ERROR retries. Main-safe. */
    fun play(request: PlayRequest)

    /** PLAYING and PAUSED toggle; an ended recording is PAUSED at 0. */
    fun togglePlayPause()

    fun pause()

    /** Clamped to 0..durationMs; ignored while LOADING. */
    fun seekTo(positionMs: Long)

    /** For example plus or minus 10_000. */
    fun seekBy(deltaMs: Long)

    /** ERROR to LOADING with the same request. */
    fun retry()

    /** Stop, release the engine, phase CLOSED. */
    fun close()
}
