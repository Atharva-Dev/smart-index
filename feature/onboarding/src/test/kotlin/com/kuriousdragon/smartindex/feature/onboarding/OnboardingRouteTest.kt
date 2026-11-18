package com.kuriousdragon.smartindex.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage
import com.kuriousdragon.smartindex.feature.onboarding.model.PageFlow
import com.kuriousdragon.smartindex.feature.onboarding.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.onboarding.ui.OnboardingTestTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingRouteTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val service = RouteService()
    private val control = FakeIndexControl()
    private val registry = RouteRegistry()
    private var finished = 0

    private fun setUp(page: OnboardingPage = OnboardingPage.Welcome): OnboardingViewModel {
        val handle = SavedStateHandle(mapOf("page" to PageFlow.encode(page)))
        val viewModel = OnboardingViewModel(service, control, handle)
        rule.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SmartIndexTheme(darkTheme = false) {
                    OnboardingRoute(onFinished = { finished++ }, viewModel = viewModel)
                }
            }
        }
        rule.waitForIdle()
        return viewModel
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `Allow on Messages launches the READ_SMS request and the flow advances after the result`() {
        val viewModel = setUp(OnboardingPage.Step(Group.MESSAGES))

        tap(OnboardingTestTags.ALLOW)

        assertThat(registry.permissionLaunches).containsExactly(listOf(Manifest.permission.READ_SMS))
        assertThat(viewModel.uiState.value.page).isNotEqualTo(OnboardingPage.Step(Group.MESSAGES))
    }

    @Test
    fun `Allow on Files launches the settings intent and advances after it returns`() {
        val viewModel = setUp(OnboardingPage.Step(Group.FILES))

        tap(OnboardingTestTags.ALLOW)

        assertThat(registry.intents.map { it.action }).containsExactly("test.FILES_SETTINGS")
        assertThat(service.refreshCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.page).isNotEqualTo(OnboardingPage.Step(Group.FILES))
    }

    @Test
    fun `a missing settings activity does not stall the flow`() {
        registry.failSettingsLaunch = true
        val viewModel = setUp(OnboardingPage.Step(Group.FILES))

        tap(OnboardingTestTags.ALLOW)

        assertThat(registry.intents).isEmpty()
        assertThat(viewModel.uiState.value.page).isNotEqualTo(OnboardingPage.Step(Group.FILES))
    }

    @Test
    fun `Back on step 2 returns to step 1 when step 1 is unanswered`() {
        val viewModel = setUp(OnboardingPage.Step(Group.FILES))

        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(viewModel.uiState.value.page).isEqualTo(OnboardingPage.Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `Back on step 2 returns to Welcome when step 1 is answered`() {
        service.set(Group.PHOTOS_VIDEOS, GroupState.GRANTED)
        val viewModel = setUp(OnboardingPage.Step(Group.FILES))

        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(viewModel.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `Back on Welcome is not consumed by the route`() {
        val viewModel = setUp()
        var fallbackCalls = 0
        rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.addCallback(
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        fallbackCalls++
                    }
                },
            )
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()

        assertThat(fallbackCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.page).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `Start indexing calls reindexAll once and onFinished once`() {
        setUp(OnboardingPage.Summary)

        tap(OnboardingTestTags.START)

        assertThat(control.reindexCount).isEqualTo(1)
        assertThat(finished).isEqualTo(1)
    }

    private class RouteService : PermissionService {
        private val flow = MutableStateFlow(Group.entries.associateWith { GroupState.DENIED })
        var refreshCount = 0
        override val state: StateFlow<Map<Group, GroupState>> = flow

        fun set(group: Group, state: GroupState) {
            flow.value = flow.value + (group to state)
        }

        override fun request(group: Group): PermissionRequest = when (group) {
            Group.MESSAGES -> PermissionRequest.Runtime(group, listOf(Manifest.permission.READ_SMS))
            Group.FILES -> PermissionRequest.Settings(group, Intent("test.FILES_SETTINGS"))
            else -> PermissionRequest.Runtime(group, emptyList())
        }

        override fun settingsIntent(group: Group): Intent = Intent("test.SETTINGS")

        override fun onRuntimeResult(result: RuntimeResult) = Unit

        override fun refresh() {
            refreshCount++
        }
    }

    private class RouteRegistry : ActivityResultRegistry() {
        val permissionLaunches = mutableListOf<List<String>>()
        val intents = mutableListOf<Intent>()
        var failSettingsLaunch = false

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            when (contract) {
                is ActivityResultContracts.RequestMultiplePermissions -> {
                    @Suppress("UNCHECKED_CAST")
                    val permissions = (input as Array<String>).toList()
                    permissionLaunches += permissions
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, permissions.associateWith { false } as O)
                }

                is ActivityResultContracts.StartActivityForResult -> {
                    if (failSettingsLaunch) throw ActivityNotFoundException("no settings")
                    intents += input as Intent
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, ActivityResult(Activity.RESULT_CANCELED, null) as O)
                }

                else -> error("unexpected contract $contract")
            }
        }
    }
}
