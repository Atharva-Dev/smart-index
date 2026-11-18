package com.kuriousdragon.smartindex.feature.onboarding.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.feature.onboarding.R
import com.kuriousdragon.smartindex.feature.onboarding.model.ONBOARDING_GROUPS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeAndStepPagesTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(id: Int) = context.getString(id)

    private fun content(block: @Composable () -> Unit) {
        rule.setContent { SmartIndexTheme(darkTheme = false) { Box(Modifier.fillMaxSize()) { block() } } }
    }

    @Test
    fun `welcome shows title, body, three features, privacy and Get started without Skip`() {
        content { WelcomePage(onGetStarted = {}) }
        rule.onNodeWithText(str(R.string.welcome_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.welcome_body)).assertExists()
        listOf(R.string.welcome_feature_1, R.string.welcome_feature_2, R.string.welcome_feature_3).forEach {
            rule.onNodeWithText(str(it)).assertExists()
        }
        rule.onNodeWithText(str(R.string.welcome_privacy)).assertExists()
        rule.onNodeWithText(str(R.string.welcome_cta)).assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTestTags.WELCOME).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.GET_STARTED).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.SKIP).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.step_skip)).assertDoesNotExist()
    }

    @Test
    fun `Get started invokes its callback`() {
        var clicks = 0
        content { WelcomePage(onGetStarted = { clicks++ }) }
        rule.onNodeWithTag(OnboardingTestTags.GET_STARTED).performClick()
        assertThat(clicks).isEqualTo(1)
    }

    private fun assertStepTexts(
        group: Group,
        title: Int,
        body: Int,
        cta: Int,
        reads: Int,
        readsHeading: Int,
    ) {
        content { StepPage(group = group, onAllow = {}, onSkip = {}) }
        rule.onNodeWithText(str(title)).assertExists()
        rule.onNodeWithText(str(body)).assertExists()
        rule.onNodeWithText(str(readsHeading)).assertExists()
        context.resources.getStringArray(reads).forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText(str(R.string.step_privacy)).assertExists()
        rule.onNodeWithText(str(cta)).assertExists()
        rule.onNodeWithText(str(R.string.step_skip)).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.step(group)).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.ALLOW).assertExists()
        rule.onNodeWithTag(OnboardingTestTags.SKIP).assertExists()
    }

    @Test
    fun `step 1 photos shows its texts`() =
        assertStepTexts(
            Group.PHOTOS_VIDEOS,
            R.string.step1_title,
            R.string.step1_body,
            R.string.step_cta,
            R.array.step1_reads,
            R.string.step_reads_heading,
        )

    @Test
    fun `step 2 files shows Continue to settings`() {
        assertStepTexts(
            Group.FILES,
            R.string.step2_title,
            R.string.step2_body,
            R.string.step2_cta,
            R.array.step2_reads,
            R.string.step_reads_heading,
        )
        rule.onNodeWithText("Continue to settings").assertExists()
        rule.onNodeWithText(str(R.string.step_cta)).assertDoesNotExist()
    }

    @Test
    fun `step 3 messages uses the never-sends reads heading`() =
        assertStepTexts(
            Group.MESSAGES,
            R.string.step3_title,
            R.string.step3_body,
            R.string.step_cta,
            R.array.step3_reads,
            R.string.step3_reads_heading,
        )

    @Test
    fun `step 4 call history shows its texts`() =
        assertStepTexts(
            Group.CALL_LOG,
            R.string.step4_title,
            R.string.step4_body,
            R.string.step_cta,
            R.array.step4_reads,
            R.string.step_reads_heading,
        )

    @Test
    fun `step 5 contacts shows its texts`() =
        assertStepTexts(
            Group.CONTACTS,
            R.string.step5_title,
            R.string.step5_body,
            R.string.step_cta,
            R.array.step5_reads,
            R.string.step_reads_heading,
        )

    private fun assertDots(group: Group) {
        val step = ONBOARDING_GROUPS.indexOf(group) + 1
        content { StepPage(group = group, onAllow = {}, onSkip = {}) }
        rule.onNodeWithText("Step $step of 5").assertExists()
        rule.onNodeWithContentDescription("Step $step of 5").assertContentDescriptionEquals("Step $step of 5")
        for (index in 1..5) {
            val dot = rule.onNodeWithTag(OnboardingTestTags.stepDot(index), useUnmergedTree = true)
            dot.assertWidthIsEqualTo(if (index == step) 20.dp else 8.dp)
        }
    }

    @Test
    fun `dots and label for step 1`() = assertDots(Group.PHOTOS_VIDEOS)

    @Test
    fun `dots and label for step 2`() = assertDots(Group.FILES)

    @Test
    fun `dots and label for step 3`() = assertDots(Group.MESSAGES)

    @Test
    fun `dots and label for step 4`() = assertDots(Group.CALL_LOG)

    @Test
    fun `dots and label for step 5`() = assertDots(Group.CONTACTS)

    @Test
    fun `Allow and Skip invoke their callbacks`() {
        var allow = 0
        var skip = 0
        content { StepPage(group = Group.MESSAGES, onAllow = { allow++ }, onSkip = { skip++ }) }
        rule.onNodeWithTag(OnboardingTestTags.ALLOW).performClick()
        rule.onNodeWithTag(OnboardingTestTags.SKIP).performClick()
        assertThat(allow).isEqualTo(1)
        assertThat(skip).isEqualTo(1)
    }

    @Test
    fun `all clickable nodes are at least 48 dp`() {
        content { StepPage(group = Group.PHOTOS_VIDEOS, onAllow = {}, onSkip = {}) }
        rule.onAllNodes(hasClickAction()).assertAll48()
    }

    @Test
    fun `welcome clickable nodes are at least 48 dp`() {
        content { WelcomePage(onGetStarted = {}) }
        rule.onAllNodes(hasClickAction()).assertAll48()
    }

    @Test
    fun `at 200 percent font the primary button stays pinned and the long step 2 content scrolls`() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                SmartIndexTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize()) { StepPage(group = Group.FILES, onAllow = {}, onSkip = {}) }
                }
            }
        }
        rule.onNodeWithTag(OnboardingTestTags.ALLOW).assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTestTags.SKIP).assertIsDisplayed()
        rule.onNode(hasScrollAction()).assertExists()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertAll48() {
    val count = fetchSemanticsNodes().size
    assertThat(count).isAtLeast(1)
    for (i in 0 until count) {
        get(i).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}
