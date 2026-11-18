package com.kuriousdragon.smartindex.feature.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusEnv
import com.kuriousdragon.smartindex.feature.status.model.StatusMapper
import com.kuriousdragon.smartindex.feature.status.model.StatusUiState
import com.kuriousdragon.smartindex.feature.status.testing.indexStats
import com.kuriousdragon.smartindex.feature.status.testing.indexStatus
import com.kuriousdragon.smartindex.feature.status.ui.StatusTestTags
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatusScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val env = StatusEnv(ZoneId.of("UTC"), Locale.US, is24Hour = true)
    private val now = 1_793_000_000_000L
    private val allGranted = Group.entries.associateWith { GroupState.GRANTED }
    private val noneGranted = Group.entries.associateWith { GroupState.DENIED }

    private fun state(
        status: com.kuriousdragon.smartindex.core.model.IndexStatus = indexStatus(
            stats = indexStats(mapOf(ItemType.PHOTO to 18_240)),
            lastRun = now - 1000,
        ),
        permissions: Map<Group, GroupState> = allGranted,
        dialog: StatusDialog? = null,
    ): StatusUiState = StatusMapper.map(status, permissions, dialog)

    private class Calls {
        var back = 0
        val allowed = mutableListOf<Group>()
        var reindexClick = 0
        var reindexConfirm = 0
        var dismiss = 0
        var openSettings = 0
    }

    private fun setScreen(
        state: StatusUiState,
        scrollToPermissions: Boolean = false,
        calls: Calls = Calls(),
        heightDp: Int? = null,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                SmartIndexTheme(darkTheme = false) {
                    val modifier = if (heightDp != null) Modifier.height(heightDp.dp).width(360.dp) else Modifier
                    Box(modifier) { Screen(state, scrollToPermissions, calls) }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(state: StatusUiState, scrollToPermissions: Boolean, calls: Calls) {
        StatusScreen(
            state = state,
            scrollToPermissions = scrollToPermissions,
            env = env,
            nowMs = now,
            onBack = { calls.back++ },
            onAllow = { calls.allowed += it },
            onReindexClick = { calls.reindexClick++ },
            onReindexConfirm = { calls.reindexConfirm++ },
            onDialogDismiss = { calls.dismiss++ },
            onSettingsDialogOpenSettings = { calls.openSettings++ },
        )
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag(StatusTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `permissions header index equals the real position of the hdr_permissions item`() {
        val s = state(permissions = noneGranted)
        // scrollToItem(permissionsHeaderIndex()) puts the item at that index at the very top of the list.
        setScreen(s, scrollToPermissions = true, heightDp = 300)
        rule.waitForIdle()
        val listTop = rule.onNodeWithTag(StatusTestTags.LIST).fetchSemanticsNode().positionInRoot.y
        val headerTop = rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).fetchSemanticsNode().positionInRoot.y
        assertThat(headerTop).isWithin(1f).of(listTop)
        assertThat(StatusMapper.permissionsHeaderIndex(s.typeRows.size)).isEqualTo(9)
    }

    @Test
    fun `renders seven type rows and five permission rows in order`() {
        val s = state(permissions = noneGranted)
        setScreen(s)
        assertThat(s.typeRows.map { it.type }).containsExactly(
            ItemType.PHOTO,
            ItemType.VIDEO,
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.CALL,
            ItemType.RECORDING,
            ItemType.MESSAGE,
        ).inOrder()
        assertThat(s.permissionRows.map { it.group }).containsExactlyElementsIn(Group.entries).inOrder()
        // Visit every row in list order; each one must be reachable by scrolling forward only.
        val tags = s.typeRows.map { StatusTestTags.typeRow(it.type) } +
            StatusTestTags.PERMISSIONS_HEADER +
            s.permissionRows.map { StatusTestTags.permissionRow(it.group) } +
            StatusTestTags.REINDEX
        tags.forEach {
            scrollTo(it)
            rule.onNodeWithTag(it).assertIsDisplayed()
        }
    }

    @Test
    fun `every banner kind renders in the list`() {
        val statuses = listOf(
            indexStatus(lastRun = now - 1000),
            indexStatus(),
            indexStatus(phase = IndexPhase.INDEXING, mode = Mode.INCREMENTAL),
            indexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, done = 10, total = 100),
            indexStatus(error = IndexError(IndexError.Kind.SOURCE_FAILED, setOf(Source.SMS), atMs = now)),
        )
        var current by mutableStateOf(statuses.first())
        rule.setContent {
            SmartIndexTheme(darkTheme = false) { Screen(state(status = current), false, Calls()) }
        }
        statuses.forEach { status ->
            current = status
            rule.waitForIdle()
            rule.onNodeWithTag(StatusTestTags.BANNER).assertIsDisplayed()
        }
    }

    @Test
    fun `back arrow calls onBack and is at least 48 dp`() {
        val calls = Calls()
        setScreen(state(), calls = calls)
        rule.onNodeWithTag(StatusTestTags.BACK).assertHeightIsAtLeast(48.dp).performClick()
        assertThat(calls.back).isEqualTo(1)
        rule.onNodeWithText("Index status").assertIsDisplayed()
    }

    @Test
    fun `allow button calls onAllow with its group`() {
        val calls = Calls()
        setScreen(state(permissions = noneGranted), calls = calls, heightDp = 4000)
        scrollTo(StatusTestTags.allow(Group.CALL_LOG))
        rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG)).performClick()
        assertThat(calls.allowed).containsExactly(Group.CALL_LOG)
    }

    @Test
    fun `scrollToPermissions true brings the Permissions header on screen on a small viewport`() {
        setScreen(state(permissions = noneGranted), scrollToPermissions = true, heightDp = 300)
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).assertIsDisplayed()
        rule.onNodeWithTag(StatusTestTags.BANNER).assertIsNotDisplayed()
    }

    @Test
    fun `scrollToPermissions false leaves the list at the top`() {
        setScreen(state(permissions = noneGranted), scrollToPermissions = false, heightDp = 300)
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.BANNER).assertIsDisplayed()
        rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).assertIsNotDisplayed()
    }

    @Test
    fun `rotation does not scroll to permissions again`() {
        val tester = StateRestorationTester(rule)
        val s = state(permissions = noneGranted)
        tester.setContent {
            SmartIndexTheme(darkTheme = false) {
                Box(Modifier.height(300.dp).width(360.dp)) { Screen(s, true, Calls()) }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).assertIsDisplayed()
        // Scroll back to the top, then recreate the composition: the saved flag must stop a second scroll.
        rule.onNodeWithTag(StatusTestTags.LIST).performScrollToNode(hasTestTag(StatusTestTags.BANNER))
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.BANNER).assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER).assertIsNotDisplayed()
    }

    @Test
    fun `confirm re-index dialog shows and fires its callbacks`() {
        val calls = Calls()
        setScreen(state(dialog = StatusDialog.ConfirmReindex), calls = calls)
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertThat(calls.dismiss).isEqualTo(1)
        rule.onNodeWithTag(StatusTestTags.REINDEX_CONFIRM).performClick()
        assertThat(calls.reindexConfirm).isEqualTo(1)
    }

    @Test
    fun `allow in settings dialog shows and fires its callbacks`() {
        val calls = Calls()
        setScreen(
            state(permissions = noneGranted, dialog = StatusDialog.AllowInSettings(Group.CALL_LOG)),
            calls = calls,
        )
        rule.onNodeWithText("Open settings").assertIsDisplayed().performClick()
        assertThat(calls.openSettings).isEqualTo(1)
        rule.onNodeWithText("Cancel").performClick()
        assertThat(calls.dismiss).isEqualTo(1)
    }

    @Test
    fun `re-index button calls onReindexClick when idle`() {
        val calls = Calls()
        setScreen(state(), calls = calls, heightDp = 4000)
        scrollTo(StatusTestTags.REINDEX)
        rule.onNodeWithTag(StatusTestTags.REINDEX).performClick()
        assertThat(calls.reindexClick).isEqualTo(1)
    }

    @Test
    fun `at 200 percent font every button can be scrolled into view and is at least 48 dp`() {
        setScreen(state(permissions = noneGranted), heightDp = 600, fontScale = 2f)
        val tags = Group.entries.map { StatusTestTags.allow(it) } + StatusTestTags.REINDEX
        tags.forEach { tag ->
            rule.onNodeWithTag(StatusTestTags.LIST).performScrollToNode(hasTestTag(tag))
            rule.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `reduced motion has no effect on layout`() {
        var reduced by mutableStateOf(false)
        val s = state(permissions = noneGranted)
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reduced) {
                SmartIndexTheme(darkTheme = false) {
                    Box(Modifier.height(4000.dp).width(360.dp)) { Screen(s, false, Calls()) }
                }
            }
        }
        rule.waitForIdle()
        val normal = rule.onNodeWithTag(StatusTestTags.LIST).fetchSemanticsNode().size
        reduced = true
        rule.waitForIdle()
        assertThat(rule.onNodeWithTag(StatusTestTags.LIST).fetchSemanticsNode().size).isEqualTo(normal)
    }
}
