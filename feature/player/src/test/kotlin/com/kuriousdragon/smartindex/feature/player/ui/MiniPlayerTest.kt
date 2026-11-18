package com.kuriousdragon.smartindex.feature.player.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.feature.player.PlayRequest
import com.kuriousdragon.smartindex.feature.player.PlayerEvent
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import com.kuriousdragon.smartindex.feature.player.PlayerUiState
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakePlayer(initial: PlayerUiState = PlayerUiState()) : RecordingPlayer {
    val flow = MutableStateFlow(initial)
    override val state: StateFlow<PlayerUiState> = flow
    override val events: Flow<PlayerEvent> = emptyFlow()
    val calls = mutableListOf<String>()

    override fun play(request: PlayRequest) {
        calls += "play"
    }
    override fun togglePlayPause() {
        calls += "toggle"
    }
    override fun pause() {
        calls += "pause"
    }
    override fun seekTo(positionMs: Long) {
        calls += "seekTo:$positionMs"
    }
    override fun seekBy(deltaMs: Long) {
        calls += "seekBy:$deltaMs"
    }
    override fun retry() {
        calls += "retry"
    }
    override fun close() {
        calls += "close"
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MiniPlayerTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun state(
        phase: PlayerPhase,
        position: Long = 83_000,
        duration: Long = 252_000,
        title: String = "Rahul Sharma",
    ) =
        PlayerUiState(phase, 1L, title, "Incoming call · Tue 3 Nov, 09:42", position, duration)

    private fun show(state: PlayerUiState, dark: Boolean = false) {
        rule.setContent {
            SmartIndexTheme(darkTheme = dark) {
                MiniPlayer(
                    state = state,
                    onTogglePlay = { calls += "toggle" },
                    onSeekTo = { calls += "seekTo:$it" },
                    onSeekBy = { calls += "seekBy:$it" },
                    onClose = { calls += "close" },
                    onRetry = { calls += "retry" },
                )
            }
        }
    }

    private fun iconDescription(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    @Test
    fun `playing shows pause enabled slider and live time`() {
        show(state(PlayerPhase.PLAYING))
        assertThat(iconDescription(PlayerTestTags.PLAY)).isEqualTo("Pause")
        rule.onNodeWithTag(PlayerTestTags.PLAY).assertIsEnabled()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).assertIsEnabled()
        rule.onNodeWithText("1:23 / 4:12").assertIsDisplayed()
        rule.onNodeWithText("Incoming call · Tue 3 Nov, 09:42").assertIsDisplayed()
    }

    @Test
    fun `paused shows play enabled`() {
        show(state(PlayerPhase.PAUSED))
        assertThat(iconDescription(PlayerTestTags.PLAY)).isEqualTo("Play")
        rule.onNodeWithTag(PlayerTestTags.PLAY).assertIsEnabled()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).assertIsEnabled()
    }

    @Test
    fun `loading disables play and slider and shows unknown time`() {
        show(state(PlayerPhase.LOADING, position = 0, duration = 0))
        assertThat(iconDescription(PlayerTestTags.PLAY)).isEqualTo("Play")
        rule.onNodeWithTag(PlayerTestTags.PLAY).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).assertIsNotEnabled()
        rule.onNodeWithText("0:00 / 0:00").assertIsDisplayed()
    }

    @Test
    fun `error shows retry, error subtitle in error colour and disabled slider`() {
        var errorColor = Color.Unspecified
        var onSurfaceVariant = Color.Unspecified
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                errorColor = MaterialTheme.colorScheme.error
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                MiniPlayer(state(PlayerPhase.ERROR), {}, {}, {}, {}, { calls += "retry" })
            }
        }
        assertThat(iconDescription(PlayerTestTags.PLAY)).isEqualTo("Retry")
        rule.onNodeWithTag(PlayerTestTags.PLAY).assertIsEnabled()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).assertIsNotEnabled()
        rule.onNodeWithText("Can't play this recording").assertIsDisplayed()
        rule.onNodeWithText("Incoming call · Tue 3 Nov, 09:42").assertDoesNotExist()
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Can't play this recording")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertThat(results.single().layoutInput.style.color).isEqualTo(errorColor)
        assertThat(errorColor).isNotEqualTo(onSurfaceVariant)
        rule.onNodeWithTag(PlayerTestTags.PLAY).performClick()
        assertThat(calls).containsExactly("retry")
    }

    @Test
    fun `subtitle uses on surface variant outside error`() {
        var expected = Color.Unspecified
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                expected = MaterialTheme.colorScheme.onSurfaceVariant
                MiniPlayer(state(PlayerPhase.PLAYING), {}, {}, {}, {}, {})
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Incoming call · Tue 3 Nov, 09:42")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertThat(results.single().layoutInput.style.color).isEqualTo(expected)
    }

    @Test
    fun `play and close callbacks fire`() {
        show(state(PlayerPhase.PAUSED))
        rule.onNodeWithTag(PlayerTestTags.PLAY).performClick()
        rule.onNodeWithTag(PlayerTestTags.CLOSE).performClick()
        assertThat(calls).containsExactly("toggle", "close").inOrder()
    }

    @Test
    fun `slider release seeks to the chosen position once`() {
        show(state(PlayerPhase.PAUSED, position = 0, duration = 100_000))
        rule.onNodeWithTag(PlayerTestTags.SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(60_000f) }
        rule.waitForIdle()
        assertThat(calls).containsExactly("seekTo:60000")
    }

    @Test
    fun `arrow keys seek by 10 seconds`() {
        show(state(PlayerPhase.PLAYING))
        rule.onNodeWithTag(PlayerTestTags.SLIDER).requestFocus()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(PlayerTestTags.SLIDER).performKeyInput { pressKey(Key.DirectionLeft) }
        assertThat(calls).containsExactly("seekBy:10000", "seekBy:-10000").inOrder()
    }

    @Test
    fun `custom actions rewind and forward 10 seconds`() {
        show(state(PlayerPhase.PLAYING))
        val actions = rule.onNodeWithTag(
            PlayerTestTags.SLIDER,
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Rewind 10 seconds", "Forward 10 seconds").inOrder()
        rule.runOnUiThread {
            actions.first { it.label == "Rewind 10 seconds" }.action()
            actions.first { it.label == "Forward 10 seconds" }.action()
        }
        assertThat(calls).containsExactly("seekBy:-10000", "seekBy:10000").inOrder()
    }

    @Test
    fun `semantics labels`() {
        show(state(PlayerPhase.PLAYING))
        rule.onNodeWithTag(PlayerTestTags.MINI_PLAYER).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)!!.let {
            assertThat(it).containsExactly("Recording player")
        }
        assertThat(iconDescription(PlayerTestTags.CLOSE)).isEqualTo("Stop and close player")
        val slider = rule.onNodeWithTag(PlayerTestTags.SLIDER).fetchSemanticsNode().config
        assertThat(slider[SemanticsProperties.StateDescription]).isEqualTo("Playback position, 1:23 of 4:12")
    }

    @Test
    fun `200 percent font does not clip the title`() {
        val long = "Rahul Sharma and a very long contact name"
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                SmartIndexTheme(darkTheme = false) {
                    MiniPlayer(state(PlayerPhase.PLAYING, title = long), {}, {}, {}, {}, {}, Modifier.width(360.dp))
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(long).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertThat(layout.layoutInput.density.fontScale).isEqualTo(2f)
        assertThat(layout.layoutInput.maxLines).isAtLeast(2)
        assertThat(layout.didOverflowHeight).isFalse()
        rule.onNodeWithText(long).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.PLAY).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.CLOSE).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).assertIsDisplayed()
    }

    @Test
    fun `host shows nothing when closed and the player when playing`() {
        val player = FakePlayer()
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                SmartIndexTheme(darkTheme = false) { MiniPlayerHost(player) }
            }
        }
        rule.onNodeWithTag(PlayerTestTags.MINI_PLAYER).assertDoesNotExist()
        player.flow.value = state(PlayerPhase.PLAYING)
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.MINI_PLAYER).assertIsDisplayed()
        rule.onNodeWithText("Rahul Sharma").assertIsDisplayed()
        player.flow.value = PlayerUiState()
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.MINI_PLAYER).assertDoesNotExist()
    }

    @Test
    fun `host wires callbacks to the player`() {
        val player = FakePlayer(state(PlayerPhase.PAUSED))
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                SmartIndexTheme(darkTheme = false) { MiniPlayerHost(player) }
            }
        }
        rule.onNodeWithTag(PlayerTestTags.PLAY).performClick()
        rule.onNodeWithTag(PlayerTestTags.CLOSE).performClick()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).requestFocus()
        rule.onNodeWithTag(PlayerTestTags.SLIDER).performKeyInput { pressKey(Key.DirectionRight) }
        assertThat(player.calls).containsExactly("toggle", "close", "seekBy:10000").inOrder()
    }

    @Test
    fun `host retry in error state`() {
        val player = FakePlayer(state(PlayerPhase.ERROR))
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                SmartIndexTheme(darkTheme = false) { MiniPlayerHost(player) }
            }
        }
        rule.onNodeWithTag(PlayerTestTags.PLAY).performClick()
        assertThat(player.calls).containsExactly("retry")
    }
}
