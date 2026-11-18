package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.feature.player.PlayRequest
import com.kuriousdragon.smartindex.feature.player.PlayerEvent
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import com.kuriousdragon.smartindex.feature.player.PlayerUiState
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** [RecordingPlayer] that records every call; [mutable] and [emit] drive its state and events. */
internal class FakeRecordingPlayer : RecordingPlayer {
    val mutable = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> get() = mutable

    private val eventChannel = Channel<PlayerEvent>(Channel.BUFFERED)
    override val events: Flow<PlayerEvent> = eventChannel.receiveAsFlow()

    val played = mutableListOf<PlayRequest>()
    var toggles = 0
    var pauses = 0
    var retries = 0
    var closes = 0

    fun emit(event: PlayerEvent) {
        eventChannel.trySend(event)
    }

    fun set(recordingId: Long?, phase: PlayerPhase) {
        mutable.value = PlayerUiState(phase = phase, recordingId = recordingId)
    }

    override fun play(request: PlayRequest) {
        played += request
        mutable.value = PlayerUiState(PlayerPhase.LOADING, request.recordingId, request.title, request.subtitle)
    }

    override fun togglePlayPause() {
        toggles++
    }

    override fun pause() {
        pauses++
    }

    override fun seekTo(positionMs: Long) = Unit

    override fun seekBy(deltaMs: Long) = Unit

    override fun retry() {
        retries++
    }

    override fun close() {
        closes++
    }
}
