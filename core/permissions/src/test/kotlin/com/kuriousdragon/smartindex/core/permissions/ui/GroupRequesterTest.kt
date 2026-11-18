package com.kuriousdragon.smartindex.core.permissions.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GroupRequesterTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val service = FakeService()
    private val results = mutableListOf<Pair<Group, GroupState>>()
    private val registry = TestRegistry()

    private fun setUp(
        rationaleBefore: Set<String> = emptySet(),
        rationaleAfter: Set<String> = emptySet(),
    ): (Group) -> Unit {
        lateinit var launch: (Group) -> Unit
        var launchedOnce = false
        rule.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                launch = rememberGroupRequester(
                    service = service,
                    onResult = { group, state -> results += group to state },
                    shouldShowRationale = { _: Activity, permission: String ->
                        // Before the launch the registry has not answered yet; afterwards it has.
                        if (registry.answered) permission in rationaleAfter else permission in rationaleBefore
                    },
                )
                launchedOnce = true
            }
        }
        rule.waitForIdle()
        assertThat(launchedOnce).isTrue()
        return launch
    }

    @Test
    fun `already granted reports GRANTED without launching`() {
        service.requests[Group.CONTACTS] = PermissionRequest.AlreadyGranted(Group.CONTACTS)
        val launch = setUp()

        rule.runOnIdle { launch(Group.CONTACTS) }

        assertThat(results).containsExactly(Group.CONTACTS to GroupState.GRANTED)
        assertThat(registry.launches).isEmpty()
    }

    @Test
    fun `runtime launches the exact permission list and passes before and after rationale`() {
        val perms = listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        service.requests[Group.PHOTOS_VIDEOS] = PermissionRequest.Runtime(Group.PHOTOS_VIDEOS, perms)
        service.stateAfterRuntime = GroupState.DENIED
        registry.permissionResult = mapOf(perms[0] to false, perms[1] to false)
        val launch = setUp(rationaleBefore = setOf(perms[0]), rationaleAfter = setOf(perms[1]))

        rule.runOnIdle { launch(Group.PHOTOS_VIDEOS) }

        assertThat(registry.launches).containsExactly(perms)
        assertThat(service.runtimeResults).containsExactly(
            RuntimeResult(
                group = Group.PHOTOS_VIDEOS,
                grants = mapOf(perms[0] to false, perms[1] to false),
                rationaleBefore = setOf(perms[0]),
                rationaleAfter = setOf(perms[1]),
            ),
        )
        assertThat(results).containsExactly(Group.PHOTOS_VIDEOS to GroupState.DENIED)
    }

    @Test
    fun `settings launches the intent then refreshes and reports the current state`() {
        val intent = Intent("test.SETTINGS")
        service.requests[Group.FILES] = PermissionRequest.Settings(Group.FILES, intent)
        service.states.value = service.states.value + (Group.FILES to GroupState.GRANTED)
        val launch = setUp()

        rule.runOnIdle { launch(Group.FILES) }

        assertThat(registry.intents.map { it.action }).containsExactly("test.SETTINGS")
        assertThat(service.refreshCount).isEqualTo(1)
        assertThat(results).containsExactly(Group.FILES to GroupState.GRANTED)
    }

    @Test
    fun `missing settings activity is caught and reports the current state`() {
        service.requests[Group.CALL_LOG] = PermissionRequest.Settings(Group.CALL_LOG, Intent("test.NONE"))
        registry.failSettingsLaunch = true
        val launch = setUp()

        rule.runOnIdle { launch(Group.CALL_LOG) }

        assertThat(service.refreshCount).isEqualTo(0)
        assertThat(results).containsExactly(Group.CALL_LOG to GroupState.DENIED_PERMANENTLY)
    }

    private class FakeService : PermissionService {
        val requests = mutableMapOf<Group, PermissionRequest>()
        val runtimeResults = mutableListOf<RuntimeResult>()
        var refreshCount = 0
        var stateAfterRuntime: GroupState? = null
        val states = MutableStateFlow(
            mapOf(
                Group.PHOTOS_VIDEOS to GroupState.DENIED,
                Group.FILES to GroupState.DENIED,
                Group.MESSAGES to GroupState.DENIED,
                Group.CALL_LOG to GroupState.DENIED_PERMANENTLY,
                Group.CONTACTS to GroupState.GRANTED,
            ),
        )
        override val state: StateFlow<Map<Group, GroupState>> = states

        override fun request(group: Group): PermissionRequest = requests.getValue(group)

        override fun settingsIntent(group: Group): Intent = Intent("test.SETTINGS")

        override fun onRuntimeResult(result: RuntimeResult) {
            runtimeResults += result
            stateAfterRuntime?.let { states.value = states.value + (result.group to it) }
        }

        override fun refresh() {
            refreshCount++
        }
    }

    private class TestRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<List<String>>()
        val intents = mutableListOf<Intent>()
        var permissionResult: Map<String, Boolean> = emptyMap()
        var failSettingsLaunch = false
        var answered = false

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            when (contract) {
                is ActivityResultContracts.RequestMultiplePermissions -> {
                    @Suppress("UNCHECKED_CAST")
                    launches += (input as Array<String>).toList()
                    answered = true
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, permissionResult as O)
                }

                is ActivityResultContracts.StartActivityForResult -> {
                    if (failSettingsLaunch) throw ActivityNotFoundException("no settings")
                    intents += input as Intent
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(
                        requestCode,
                        androidx.activity.result.ActivityResult(Activity.RESULT_CANCELED, null) as O,
                    )
                }

                else -> error("unexpected contract $contract")
            }
        }
    }
}
