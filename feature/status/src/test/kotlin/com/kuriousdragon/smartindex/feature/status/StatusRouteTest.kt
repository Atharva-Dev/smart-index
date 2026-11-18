package com.kuriousdragon.smartindex.feature.status

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
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.feature.status.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.status.testing.FakeIndexStatusSource
import com.kuriousdragon.smartindex.feature.status.testing.FakePermissionService
import com.kuriousdragon.smartindex.feature.status.testing.indexStatus
import com.kuriousdragon.smartindex.feature.status.ui.StatusTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatusRouteTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val source = FakeIndexStatusSource()
    private val control = FakeIndexControl()
    private val permissions = FakePermissionService()
    private val registry = TestRegistry(permissions)

    private fun setRoute(section: String? = null) {
        val vm = StatusViewModel(
            source,
            control,
            permissions,
            SavedStateHandle(if (section == null) emptyMap() else mapOf("section" to section)),
        )
        rule.setContent {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SmartIndexTheme { StatusRoute(onBack = {}, viewModel = vm) }
            }
        }
        rule.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag(StatusTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `full indexing shows progress and disables re-index`() {
        setRoute()
        source.emit(indexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, done = 50, total = 100))
        rule.waitForIdle()

        rule.onNodeWithTag(StatusTestTags.BANNER).assertContentDescriptionContains("Indexing… 50%", substring = true)
        scrollTo(StatusTestTags.REINDEX)
        rule.onNodeWithTag(StatusTestTags.REINDEX).assertIsNotEnabled()
    }

    @Test
    fun `granting call log flips the row to allowed and notifies index control once`() {
        setRoute()
        registry.grantOnLaunch = Group.CALL_LOG

        scrollTo(StatusTestTags.allow(Group.CALL_LOG))
        rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG)).performClick()
        rule.waitForIdle()

        assertThat(permissions.requested).contains(Group.CALL_LOG)
        assertThat(control.onPermissionsChangedCalls).isEqualTo(1)
        rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG)).assertDoesNotExist()
    }

    @Test
    fun `allow on a permanently denied group shows the settings dialog and open settings launches the intent`() {
        permissions.set(Group.CALL_LOG, GroupState.DENIED_PERMANENTLY)
        setRoute()

        scrollTo(StatusTestTags.allow(Group.CALL_LOG))
        rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Open settings").assertIsDisplayed()
        assertThat(registry.intents).isEmpty()

        rule.onNodeWithText("Open settings").performClick()
        rule.waitForIdle()

        assertThat(registry.intents).hasSize(1)
    }

    @Test
    fun `cancelling the settings dialog closes it without launching`() {
        permissions.set(Group.CALL_LOG, GroupState.DENIED_PERMANENTLY)
        setRoute()

        scrollTo(StatusTestTags.allow(Group.CALL_LOG))
        rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Open settings").assertDoesNotExist()
        assertThat(registry.intents).isEmpty()
    }

    @Test
    fun `re-index everything then confirm calls reindexAll once`() {
        setRoute()

        scrollTo(StatusTestTags.REINDEX)
        rule.onNodeWithTag(StatusTestTags.REINDEX).assertIsEnabled().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.REINDEX_CONFIRM).performClick()
        rule.waitForIdle()

        assertThat(control.reindexAllCalls).isEqualTo(1)
    }

    @Test
    fun `cancelling the re-index dialog does not re-index`() {
        setRoute()

        scrollTo(StatusTestTags.REINDEX)
        rule.onNodeWithTag(StatusTestTags.REINDEX).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        assertThat(control.reindexAllCalls).isEqualTo(0)
        rule.onNodeWithTag(StatusTestTags.REINDEX_CONFIRM).assertDoesNotExist()
    }

    @Test
    fun `section permissions displays the permissions header`() {
        setRoute(section = STATUS_SECTION_PERMISSIONS)

        rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).assertIsDisplayed()
    }

    private class TestRegistry(private val service: FakePermissionService) : ActivityResultRegistry() {
        val intents = mutableListOf<Intent>()
        var grantOnLaunch: Group? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            when (contract) {
                is ActivityResultContracts.RequestMultiplePermissions -> {
                    grantOnLaunch?.let { service.set(it, GroupState.GRANTED) }
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, emptyMap<String, Boolean>() as O)
                }

                is ActivityResultContracts.StartActivityForResult -> {
                    intents += input as Intent
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, ActivityResult(Activity.RESULT_CANCELED, null) as O)
                }

                else -> throw ActivityNotFoundException("unexpected contract $contract")
            }
        }
    }
}
