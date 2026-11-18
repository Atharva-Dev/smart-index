package com.kuriousdragon.smartindex.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import com.kuriousdragon.smartindex.feature.player.PlayerUiState
import com.kuriousdragon.smartindex.feature.player.R

private const val SEEK_STEP_MS = 10_000L
private val MaxContentWidth = 640.dp
private val MicSize = 20.dp
private val PlayerShape = RoundedCornerShape(topStart = Radius.Player, topEnd = Radius.Player)

/**
 * The docked recording player (Design Spec §6.7). Stateless: [state] in, callbacks out. Seeks by dragging the
 * slider (applied on release), by Left/Right arrow keys and by TalkBack custom actions (±10 s).
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val region = stringResource(R.string.mini_player_region)
    val isError = state.phase == PlayerPhase.ERROR
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.MINI_PLAYER)
            .semantics { contentDescription = region },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = PlayerShape,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(start = Spacing.L, top = Spacing.S, bottom = Spacing.S),
            ) {
                InfoRow(state, isError, onClose)
                ControlRow(state, isError, onTogglePlay, onRetry, onSeekTo, onSeekBy)
            }
        }
    }
}

@Composable
private fun InfoRow(state: PlayerUiState, isError: Boolean, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = SmartIndexTheme.colors.types.getValue(TypeColorKey.REC).icon,
            modifier = Modifier.size(MicSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isError) stringResource(R.string.mini_player_error_sub) else state.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(Spacing.MinTouch).testTag(PlayerTestTags.CLOSE),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mini_player_close))
        }
    }
}

@Composable
private fun ControlRow(
    state: PlayerUiState,
    isError: Boolean,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    val loading = state.phase == PlayerPhase.LOADING
    val seekable = state.phase == PlayerPhase.PLAYING || state.phase == PlayerPhase.PAUSED
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        val (icon, label) = when {
            isError -> Icons.Filled.Refresh to stringResource(R.string.mini_player_retry)
            state.isPlaying -> Icons.Filled.Pause to stringResource(R.string.mini_player_pause)
            else -> Icons.Filled.PlayArrow to stringResource(R.string.mini_player_play)
        }
        FilledIconButton(
            onClick = if (isError) onRetry else onTogglePlay,
            enabled = !loading,
            colors = IconButtonDefaults.filledIconButtonColors(),
            modifier = Modifier.size(Spacing.MinTouch).testTag(PlayerTestTags.PLAY),
        ) {
            Icon(icon, contentDescription = label)
        }
        PositionSlider(state, seekable, onSeekTo, onSeekBy, Modifier.weight(1f))
        Text(
            text = stringResource(
                R.string.mini_player_time,
                formatClock(state.positionMs),
                formatClock(state.durationMs),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.L),
        )
    }
}

@Composable
private fun PositionSlider(
    state: PlayerUiState,
    enabled: Boolean,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = state.durationMs.coerceAtLeast(1L).toFloat()
    var dragging by remember { mutableStateOf<Float?>(null) }

    // Synchronous copy of the drag value: the release can arrive before the next recomposition.
    val pending = remember { floatArrayOf(Float.NaN) }
    val value = (dragging ?: state.positionMs.toFloat()).coerceIn(0f, max)
    val description = stringResource(
        R.string.mini_player_position,
        formatClock(state.positionMs),
        formatClock(state.durationMs),
    )
    val rewind = stringResource(R.string.mini_player_rewind_10)
    val forward = stringResource(R.string.mini_player_forward_10)
    Slider(
        value = value,
        onValueChange = {
            pending[0] = it
            dragging = it
        },
        onValueChangeFinished = {
            if (!pending[0].isNaN()) onSeekTo(pending[0].toLong())
            pending[0] = Float.NaN
            dragging = null
        },
        valueRange = 0f..max,
        enabled = enabled,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .testTag(PlayerTestTags.SLIDER)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekBy(-SEEK_STEP_MS)
                        true
                    }

                    Key.DirectionRight -> {
                        onSeekBy(SEEK_STEP_MS)
                        true
                    }

                    else -> false
                }
            }
            .semantics {
                stateDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(rewind) {
                        onSeekBy(-SEEK_STEP_MS)
                        true
                    },
                    CustomAccessibilityAction(forward) {
                        onSeekBy(SEEK_STEP_MS)
                        true
                    },
                )
            },
    )
}
