package com.kuriousdragon.smartindex.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.feature.onboarding.model.ONBOARDING_GROUPS
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingEffect
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage.Step
import com.kuriousdragon.smartindex.feature.onboarding.model.SummaryStatus
import com.kuriousdragon.smartindex.feature.onboarding.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.onboarding.testing.FakePermissionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val permissions = FakePermissionService()
    private val control = FakeIndexControl()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(permissions, control, saved)

    /** Keeps uiState subscribed so WhileSubscribed does not freeze the value. */
    private fun TestScope.hot(model: OnboardingViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
    }

    private fun handleOf(model: OnboardingViewModel): SavedStateHandle {
        // The saved state a process restore would hand to a new ViewModel.
        val state = model.uiState.value
        return SavedStateHandle(
            buildMap {
                put("page", com.kuriousdragon.smartindex.feature.onboarding.model.PageFlow.encode(state.page))
                state.settingsDialog?.let { put("dialog", it.name) }
            },
        )
    }

    private fun OnboardingViewModel.toStep(group: Group) {
        onGetStarted()
        var guard = 0
        while (uiState.value.page != Step(group) && guard++ < ONBOARDING_GROUPS.size) onSkip()
        assertThat(uiState.value.page).isEqualTo(Step(group))
    }

    private fun OnboardingViewModel.toSummary() {
        onGetStarted()
        repeat(ONBOARDING_GROUPS.size) { onSkip() }
        assertThat(uiState.value.page).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `starts on Welcome with a synchronous initial state`() {
        val state = vm().uiState.value
        assertThat(state.page).isEqualTo(OnboardingPage.Welcome)
        assertThat(state.groupStates).hasSize(Group.entries.size)
        assertThat(state.summary).hasSize(5)
        assertThat(state.settingsDialog).isNull()
        assertThat(state.isStarting).isFalse()
    }

    @Test
    fun `Get started goes to the first unanswered step`() = runTest {
        val model = vm().also { hot(it) }
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        model.onGetStarted()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.FILES))
    }

    @Test
    fun `Get started with all five answered goes to Summary`() = runTest {
        ONBOARDING_GROUPS.forEach { permissions.setState(it, GroupState.GRANTED) }
        val model = vm().also { hot(it) }
        model.onGetStarted()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `Get started is ignored when not on Welcome`() = runTest {
        val model = vm().also { hot(it) }
        model.toStep(Group.MESSAGES)
        model.onGetStarted()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.MESSAGES))
    }

    @Test
    fun `Allow on DENIED emits RequestGroup`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.effects.test {
            model.onAllowClick(Group.PHOTOS_VIDEOS)
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.RequestGroup(Group.PHOTOS_VIDEOS))
        }
        assertThat(model.uiState.value.settingsDialog).isNull()
    }

    @Test
    fun `Allow on FILES always requests`() = runTest {
        val model = vm().also { hot(it) }
        model.toStep(Group.FILES)
        model.effects.test {
            model.onAllowClick(Group.FILES)
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.RequestGroup(Group.FILES))
        }
    }

    @Test
    fun `Allow on DENIED_PERMANENTLY sets the dialog and emits nothing`() = runTest {
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.effects.test {
            model.onAllowClick(Group.PHOTOS_VIDEOS)
            expectNoEvents()
        }
        assertThat(model.uiState.value.settingsDialog).isEqualTo(Group.PHOTOS_VIDEOS)
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `Allow for a group that is not the current step is ignored`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.effects.test {
            model.onAllowClick(Group.MESSAGES)
            expectNoEvents()
        }
    }

    @Test
    fun `Open settings clears the dialog and emits RequestGroup`() = runTest {
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onAllowClick(Group.PHOTOS_VIDEOS)
        model.effects.test {
            model.onSettingsDialogOpenSettings()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.RequestGroup(Group.PHOTOS_VIDEOS))
        }
        assertThat(model.uiState.value.settingsDialog).isNull()
    }

    @Test
    fun `Open settings without a dialog does nothing`() = runTest {
        val model = vm().also { hot(it) }
        model.effects.test {
            model.onSettingsDialogOpenSettings()
            expectNoEvents()
        }
    }

    @Test
    fun `Cancel clears the dialog and keeps the page`() = runTest {
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onAllowClick(Group.PHOTOS_VIDEOS)
        model.onSettingsDialogCancel()
        assertThat(model.uiState.value.settingsDialog).isNull()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `onGroupResult advances whatever the result state`() = runTest {
        GroupState.entries.forEach { result ->
            val model = OnboardingViewModel(FakePermissionService(), control, SavedStateHandle()).also { hot(it) }
            model.onGetStarted()
            model.onGroupResult(Group.PHOTOS_VIDEOS, result)
            assertThat(model.uiState.value.page).isEqualTo(Step(Group.FILES))
        }
    }

    @Test
    fun `onGroupResult skips steps answered by the result`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        permissions.setState(Group.FILES, GroupState.GRANTED)
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.MESSAGES))
    }

    @Test
    fun `a stale onGroupResult for another group does not skip a step`() = runTest {
        val model = vm().also { hot(it) }
        model.toStep(Group.MESSAGES)
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.MESSAGES))
    }

    @Test
    fun `a duplicate onGroupResult does not skip the next step`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.DENIED)
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.DENIED)
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.FILES))
    }

    @Test
    fun `onGroupResult on Welcome or Summary is ignored`() = runTest {
        val model = vm().also { hot(it) }
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
        model.toSummary()
        model.onGroupResult(Group.CONTACTS, GroupState.GRANTED)
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `Skip advances without touching the permission service`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onSkip()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.FILES))
        assertThat(permissions.requested).isEmpty()
        assertThat(permissions.refreshCount).isEqualTo(0)
    }

    @Test
    fun `Skip on Welcome or Summary is ignored`() = runTest {
        val model = vm().also { hot(it) }
        model.onSkip()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
        model.toSummary()
        model.onSkip()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `Skip on the last step goes to Summary`() = runTest {
        val model = vm().also { hot(it) }
        model.toStep(Group.CONTACTS)
        model.onSkip()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `Back closes the dialog first`() = runTest {
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onAllowClick(Group.PHOTOS_VIDEOS)
        assertThat(model.onBack()).isTrue()
        assertThat(model.uiState.value.settingsDialog).isNull()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `Back goes to the previous unanswered step then Welcome`() = runTest {
        val model = vm().also { hot(it) }
        model.toStep(Group.MESSAGES)
        permissions.setState(Group.FILES, GroupState.GRANTED)
        assertThat(model.onBack()).isTrue()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.PHOTOS_VIDEOS))
        assertThat(model.onBack()).isTrue()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `Back from Summary goes to the last unanswered step`() = runTest {
        val model = vm().also { hot(it) }
        model.toSummary()
        assertThat(model.onBack()).isTrue()
        assertThat(model.uiState.value.page).isEqualTo(Step(Group.CONTACTS))
    }

    @Test
    fun `Back on Welcome returns false`() = runTest {
        val model = vm().also { hot(it) }
        assertThat(model.onBack()).isFalse()
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `page and dialog are restored from the SavedStateHandle`() = runTest {
        permissions.setState(Group.FILES, GroupState.GRANTED)
        permissions.setState(Group.MESSAGES, GroupState.DENIED_PERMANENTLY)
        val first = vm().also { hot(it) }
        first.toStep(Group.MESSAGES)
        first.onAllowClick(Group.MESSAGES)

        val second = vm(handleOf(first))
        assertThat(second.uiState.value.page).isEqualTo(Step(Group.MESSAGES))
        assertThat(second.uiState.value.settingsDialog).isEqualTo(Group.MESSAGES)
    }

    @Test
    fun `the first ViewModel writes page and dialog into its own handle`() = runTest {
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        val saved = SavedStateHandle()
        val model = vm(saved).also { hot(it) }
        model.onGetStarted()
        model.onAllowClick(Group.PHOTOS_VIDEOS)
        assertThat(saved.get<Int>("page")).isEqualTo(1)
        assertThat(saved.get<String>("dialog")).isEqualTo("PHOTOS_VIDEOS")
        model.onSettingsDialogCancel()
        assertThat(saved.get<String>("dialog")).isNull()
    }

    @Test
    fun `an invalid saved page or a FILES dialog is ignored`() {
        val model = vm(SavedStateHandle(mapOf("page" to 99, "dialog" to "FILES")))
        assertThat(model.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
        assertThat(model.uiState.value.settingsDialog).isNull()
        val unknown = vm(SavedStateHandle(mapOf("dialog" to "NOPE")))
        assertThat(unknown.uiState.value.settingsDialog).isNull()
    }

    @Test
    fun `Start indexing outside Summary does nothing`() = runTest {
        val model = vm().also { hot(it) }
        model.effects.test {
            model.onStartIndexing()
            model.onGetStarted()
            model.onStartIndexing()
            expectNoEvents()
        }
        assertThat(control.reindexCount).isEqualTo(0)
        assertThat(model.uiState.value.isStarting).isFalse()
    }

    @Test
    fun `Start indexing twice calls reindexAll once and emits Finish once`() = runTest {
        val model = vm().also { hot(it) }
        model.toSummary()
        model.effects.test {
            model.onStartIndexing()
            model.onStartIndexing()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finish)
            expectNoEvents()
        }
        assertThat(control.reindexCount).isEqualTo(1)
        assertThat(model.uiState.value.isStarting).isTrue()
    }

    @Test
    fun `onPermissionsChanged is never called`() = runTest {
        val model = vm().also { hot(it) }
        model.onGetStarted()
        model.onGroupResult(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        permissions.setState(Group.FILES, GroupState.GRANTED)
        model.onSkip()
        model.toSummary()
        model.onStartIndexing()
        assertThat(control.permissionsChangedCount).isEqualTo(0)
        assertThat(control.catchUpCount).isEqualTo(0)
    }

    @Test
    fun `Summary follows permission changes in the background`() = runTest {
        val model = vm().also { hot(it) }
        model.toSummary()
        assertThat(model.uiState.value.summary.map { it.status }).containsExactly(
            SummaryStatus.SKIPPED,
            SummaryStatus.SKIPPED,
            SummaryStatus.SKIPPED,
            SummaryStatus.SKIPPED,
            SummaryStatus.SKIPPED,
        )
        permissions.setState(Group.MESSAGES, GroupState.GRANTED)
        permissions.setState(Group.PHOTOS_VIDEOS, GroupState.PARTIAL)
        val rows = model.uiState.value.summary.associate { it.group to it.status }
        assertThat(rows[Group.MESSAGES]).isEqualTo(SummaryStatus.ALLOWED)
        assertThat(rows[Group.PHOTOS_VIDEOS]).isEqualTo(SummaryStatus.ALLOWED_SELECTED_ONLY)
        assertThat(model.uiState.value.groupStates[Group.MESSAGES]).isEqualTo(GroupState.GRANTED)
    }
}
