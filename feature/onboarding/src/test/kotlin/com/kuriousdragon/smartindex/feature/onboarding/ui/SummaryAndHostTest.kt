package com.kuriousdragon.smartindex.feature.onboarding.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.feature.onboarding.OnboardingScreen
import com.kuriousdragon.smartindex.feature.onboarding.R
import com.kuriousdragon.smartindex.feature.onboarding.model.ONBOARDING_GROUPS
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingUiState
import com.kuriousdragon.smartindex.feature.onboarding.model.SummaryRow
import com.kuriousdragon.smartindex.feature.onboarding.model.SummaryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SummaryAndHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(id: Int) = context.getString(id)

    private val rows =
        listOf(
            SummaryRow(Group.PHOTOS_VIDEOS, SummaryStatus.ALLOWED_SELECTED_ONLY),
            SummaryRow(Group.FILES, SummaryStatus.SKIPPED),
            SummaryRow(Group.MESSAGES, SummaryStatus.ALLOWED),
            SummaryRow(Group.CALL_LOG, SummaryStatus.SKIPPED),
            SummaryRow(Group.CONTACTS, SummaryStatus.ALLOWED),
        )

    private fun content(block: @Composable () -> Unit) {
        rule.setContent { SmartIndexTheme(darkTheme = false) { Box(Modifier.fillMaxSize()) { block() } } }
    }

    private class Calls {
        var getStarted = 0
        val allowed = mutableListOf<Group>()
        var skip = 0
        var start = 0
        var cancel = 0
        var openSettings = 0
    }

    @Composable
    private fun Host(state: OnboardingUiState, calls: Calls) {
        OnboardingScreen(
            state = state,
            onGetStarted = { calls.getStarted++ },
            onAllow = { calls.allowed += it },
            onSkip = { calls.skip++ },
            onStartIndexing = { calls.start++ },
            onSettingsDialogCancel = { calls.cancel++ },
            onSettingsDialogOpenSettings = { calls.openSettings++ },
        )
    }

    @Test
    fun `summary shows title, body, hint and five rows with the three statuses`() {
        content { SummaryPage(rows = rows, isStarting = false, onStartIndexing = {}) }
        rule.onNodeWithText(str(R.string.summary_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.summary_body)).assertExists()
        rule.onNodeWithText(str(R.string.summary_hint)).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.SUMMARY).assertExists()
        ONBOARDING_GROUPS.forEach { rule.onNodeWithTag(OnboardingTestTags.summaryRow(it)).assertExists() }
        rule.onNodeWithTag(OnboardingTestTags.summaryRow(Group.MESSAGES))
            .assert(hasContentDescription("${str(R.string.group_messages)}, allowed"))
        rule.onNodeWithTag(OnboardingTestTags.summaryRow(Group.FILES))
            .assert(hasContentDescription("${str(R.string.group_files)}, skipped"))
        rule.onNodeWithTag(OnboardingTestTags.summaryRow(Group.PHOTOS_VIDEOS))
            .assert(
                hasContentDescription(
                    "${str(R.string.group_photos)}, ${str(R.string.summary_status_partial).replaceFirstChar {
                        it.lowercase()
                    }}",
                ),
            )
        rule.onNodeWithText(str(R.string.summary_status_partial)).assertExists()
    }

    @Test
    fun `Start indexing invokes its callback and is disabled while starting`() {
        var clicks = 0
        content { SummaryPage(rows = rows, isStarting = false, onStartIndexing = { clicks++ }) }
        rule.onNodeWithTag(OnboardingTestTags.START).assertIsEnabled().performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `Start indexing is disabled when isStarting`() {
        content { SummaryPage(rows = rows, isStarting = true, onStartIndexing = {}) }
        rule.onNodeWithTag(OnboardingTestTags.START).assertIsNotEnabled()
    }

    @Test
    fun `host shows the page tag for each OnboardingPage`() {
        var state by mutableStateOf(OnboardingUiState(page = OnboardingPage.Welcome))
        val calls = Calls()
        content { Host(state, calls) }
        rule.onNodeWithTag(OnboardingTestTags.ROOT).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.WELCOME).assertExists()
        ONBOARDING_GROUPS.forEach { group ->
            state = OnboardingUiState(page = OnboardingPage.Step(group))
            rule.waitForIdle()
            rule.onNodeWithTag(OnboardingTestTags.step(group)).assertExists()
        }
        state = OnboardingUiState(page = OnboardingPage.Summary, summary = rows)
        rule.waitForIdle()
        rule.onNodeWithTag(OnboardingTestTags.SUMMARY).assertExists()
    }

    @Test
    fun `host wires page callbacks`() {
        val calls = Calls()
        content { Host(OnboardingUiState(page = OnboardingPage.Welcome), calls) }
        rule.onNodeWithTag(OnboardingTestTags.GET_STARTED).performClick()
        assertThat(calls.getStarted).isEqualTo(1)
    }

    @Test
    fun `host wires step and summary callbacks`() {
        val calls = Calls()
        content { Host(OnboardingUiState(page = OnboardingPage.Step(Group.MESSAGES)), calls) }
        rule.onNodeWithTag(OnboardingTestTags.ALLOW).performClick()
        rule.onNodeWithTag(OnboardingTestTags.SKIP).performClick()
        assertThat(calls.allowed).containsExactly(Group.MESSAGES)
        assertThat(calls.skip).isEqualTo(1)
    }

    @Test
    fun `host wires Start indexing`() {
        val calls = Calls()
        content { Host(OnboardingUiState(page = OnboardingPage.Summary, summary = rows), calls) }
        rule.onNodeWithTag(OnboardingTestTags.START).performClick()
        assertThat(calls.start).isEqualTo(1)
    }

    @Test
    fun `dialog is shown with Cancel and Open settings callbacks`() {
        val calls = Calls()
        content {
            Host(OnboardingUiState(page = OnboardingPage.Step(Group.MESSAGES), settingsDialog = Group.MESSAGES), calls)
        }
        rule.onNodeWithTag(OnboardingTestTags.SETTINGS_DIALOG).assertExists()
        rule.onNodeWithText("Allow in Settings").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Open settings").performClick()
        assertThat(calls.cancel).isEqualTo(1)
        assertThat(calls.openSettings).isEqualTo(1)
    }

    @Test
    fun `dialog is hidden when settingsDialog is null`() {
        content { Host(OnboardingUiState(page = OnboardingPage.Step(Group.MESSAGES)), Calls()) }
        rule.onNodeWithTag(OnboardingTestTags.SETTINGS_DIALOG).assertDoesNotExist()
        rule.onNodeWithText("Allow in Settings").assertDoesNotExist()
    }

    @Test
    fun `page switch is instant under reduced motion`() {
        var state by mutableStateOf(OnboardingUiState(page = OnboardingPage.Welcome))
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReducedMotion provides true) { Host(state, Calls()) }
            }
        }
        rule.onNodeWithTag(OnboardingTestTags.WELCOME).assertExists()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            state = OnboardingUiState(page = OnboardingPage.Summary, summary = rows)
            Snapshot.sendApplyNotifications()
        }
        // a single frame is enough: no cross-fade, the old page is gone and the new one is there
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(OnboardingTestTags.SUMMARY).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.WELCOME).assertDoesNotExist()
    }

    @Test
    fun `at 200 percent font Start indexing stays reachable`() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                SmartIndexTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize()) { SummaryPage(rows = rows, isStarting = false, onStartIndexing = {}) }
                }
            }
        }
        rule.onNodeWithTag(OnboardingTestTags.START).assertIsDisplayed()
        rule.onNode(hasScrollAction()).assertExists()
    }
}
