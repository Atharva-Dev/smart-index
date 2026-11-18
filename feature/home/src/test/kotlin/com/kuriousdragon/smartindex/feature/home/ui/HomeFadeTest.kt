package com.kuriousdragon.smartindex.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.feature.home.fadeInAfterRefresh
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** SIDX-102 / SIDX-103: an interrupted fade-in must never leave the list at alpha 0. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeFadeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val fade = Animatable(1f)
    private val refresh = MutableStateFlow<LoadState>(LoadState.NotLoading(endOfPaginationReached = false))
    private var commit by mutableIntStateOf(0)

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent { LaunchedEffect(commit) { fade.fadeInAfterRefresh(refresh) } }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun load() {
        refresh.value = LoadState.Loading
        rule.mainClock.advanceTimeByFrame()
        refresh.value = LoadState.NotLoading(endOfPaginationReached = false)
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the fade restarts from 0 after a refresh and ends at 1`() {
        show()
        load()
        assertThat(fade.value).isLessThan(1f)
        rule.mainClock.advanceTimeBy(500)
        assertThat(fade.value).isEqualTo(1f)
    }

    @Test
    fun `a newer commit cancelling the fade mid-way restores alpha to 1`() {
        show()
        load()
        rule.mainClock.advanceTimeBy(30)
        assertThat(fade.value).isLessThan(1f)
        commit = 1
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(fade.value).isEqualTo(1f)
    }

    @Test
    fun `cancelling while waiting for the refresh keeps alpha at 1`() {
        show()
        refresh.value = LoadState.Loading
        rule.mainClock.advanceTimeByFrame()
        commit = 1
        rule.mainClock.advanceTimeByFrame()
        assertThat(fade.value).isEqualTo(1f)
    }
}
