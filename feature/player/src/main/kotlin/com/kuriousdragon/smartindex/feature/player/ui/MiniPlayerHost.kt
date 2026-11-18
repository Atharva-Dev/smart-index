package com.kuriousdragon.smartindex.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuriousdragon.smartindex.core.designsystem.theme.Durations
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.feature.player.PlayerUiState
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer

/**
 * Collects [RecordingPlayer.state] (the only place that collects the 4 Hz position) and docks a [MiniPlayer]
 * that slides up in 200 ms (instant under reduced motion). Renders nothing while the player is closed.
 */
@Composable
fun MiniPlayerHost(player: RecordingPlayer, modifier: Modifier = Modifier) {
    val state by player.state.collectAsStateWithLifecycle()
    // Keep the last visible state so the content does not blank out during the slide-down.
    var lastVisible by remember { mutableStateOf<PlayerUiState?>(null) }
    if (state.isVisible) lastVisible = state
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else slideInVertically(tween(Durations.PLAYER_MS)) { it },
        exit = if (reduced) ExitTransition.None else slideOutVertically(tween(Durations.PLAYER_MS)) { it },
    ) {
        MiniPlayer(
            state = lastVisible ?: state,
            onTogglePlay = player::togglePlayPause,
            onSeekTo = player::seekTo,
            onSeekBy = player::seekBy,
            onClose = player::close,
            onRetry = player::retry,
        )
    }
}
