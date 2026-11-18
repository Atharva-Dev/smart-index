package com.kuriousdragon.smartindex.feature.status

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.feature.status.model.BannerUi
import com.kuriousdragon.smartindex.feature.status.model.PermissionAction
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusEffect
import com.kuriousdragon.smartindex.feature.status.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.status.testing.FakeIndexStatusSource
import com.kuriousdragon.smartindex.feature.status.testing.FakePermissionService
import com.kuriousdragon.smartindex.feature.status.testing.indexStatus
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
class StatusViewModelTest {
    private val permissions = FakePermissionService()
    private val control = FakeIndexControl()
    private val source = FakeIndexStatusSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) =
        StatusViewModel(source, control, permissions, saved)

    /** Keeps [StatusViewModel.uiState] subscribed so WhileSubscribed does not freeze the value. */
    private fun TestScope.hot(model: StatusViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
    }

    private val running = indexStatus(IndexPhase.INDEXING, Mode.FULL, done = 1, total = 10)

    @Test
    fun `initial value is available synchronously from both flows`() {
        permissions.set(Group.CALL_LOG, GroupState.GRANTED)
        val model = vm()
        val state = model.uiState.value
        assertThat(state.banner).isEqualTo(BannerUi.UpToDate(null))
        assertThat(state.permissionRows.first { it.group == Group.CALL_LOG }.action)
            .isEqualTo(PermissionAction.NONE)
        assertThat(state.dialog).isNull()
    }

    @Test
    fun `uiState follows both fake flows`() = runTest {
        val model = vm()
        hot(model)
        model.uiState.test {
            assertThat(awaitItem().banner).isEqualTo(BannerUi.UpToDate(null))
            source.emit(running)
            assertThat(awaitItem().banner).isInstanceOf(BannerUi.Indexing::class.java)
            permissions.set(Group.MESSAGES, GroupState.GRANTED)
            val last = awaitItem()
            assertThat(last.permissionRows.first { it.group == Group.MESSAGES }.action)
                .isEqualTo(PermissionAction.NONE)
            assertThat(last.reindex.enabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Allow on a denied group emits RequestGroup`() = runTest {
        val model = vm()
        hot(model)
        model.effects.test {
            model.onAllowClick(Group.CALL_LOG)
            assertThat(awaitItem()).isEqualTo(StatusEffect.RequestGroup(Group.CALL_LOG))
        }
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `Allow on a permanently denied group sets the dialog and emits nothing`() = runTest {
        permissions.set(Group.MESSAGES, GroupState.DENIED_PERMANENTLY)
        val model = vm()
        hot(model)
        model.effects.test {
            model.onAllowClick(Group.MESSAGES)
            expectNoEvents()
        }
        assertThat(model.uiState.value.dialog).isEqualTo(StatusDialog.AllowInSettings(Group.MESSAGES))
    }

    @Test
    fun `Allow on FILES always requests even when permanently denied`() = runTest {
        permissions.set(Group.FILES, GroupState.DENIED_PERMANENTLY)
        val model = vm()
        hot(model)
        model.effects.test {
            model.onAllowClick(Group.FILES)
            assertThat(awaitItem()).isEqualTo(StatusEffect.RequestGroup(Group.FILES))
        }
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `Open settings emits RequestGroup and clears the dialog`() = runTest {
        permissions.set(Group.MESSAGES, GroupState.DENIED_PERMANENTLY)
        val model = vm()
        hot(model)
        model.onAllowClick(Group.MESSAGES)
        model.effects.test {
            model.onSettingsDialogOpenSettings()
            assertThat(awaitItem()).isEqualTo(StatusEffect.RequestGroup(Group.MESSAGES))
        }
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `Settings dialog cancel clears it without an effect`() = runTest {
        permissions.set(Group.MESSAGES, GroupState.DENIED_PERMANENTLY)
        val model = vm()
        hot(model)
        model.onAllowClick(Group.MESSAGES)
        model.effects.test {
            model.onSettingsDialogCancel()
            expectNoEvents()
        }
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `onPermissionResult calls onPermissionsChanged exactly once per call`() {
        val model = vm()
        model.onPermissionResult(Group.CALL_LOG, GroupState.GRANTED)
        assertThat(control.onPermissionsChangedCalls).isEqualTo(1)
        model.onPermissionResult(Group.CALL_LOG, GroupState.DENIED)
        assertThat(control.onPermissionsChangedCalls).isEqualTo(2)
        assertThat(control.reindexAllCalls).isEqualTo(0)
        assertThat(control.catchUpNowCalls).isEqualTo(0)
    }

    @Test
    fun `Re-index click is ignored while a run is active`() {
        source.emit(running)
        val model = vm()
        model.onReindexClick()
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `Re-index click sets ConfirmReindex when idle`() = runTest {
        val model = vm()
        hot(model)
        model.onReindexClick()
        assertThat(model.uiState.value.dialog).isEqualTo(StatusDialog.ConfirmReindex)
    }

    @Test
    fun `confirm calls reindexAll once when idle and clears the dialog`() = runTest {
        val model = vm()
        hot(model)
        model.onReindexClick()
        model.onReindexConfirm()
        assertThat(control.reindexAllCalls).isEqualTo(1)
        assertThat(model.uiState.value.dialog).isNull()
        assertThat(control.catchUpNowCalls).isEqualTo(0)
    }

    @Test
    fun `confirm while a run is active does not call reindexAll`() = runTest {
        val model = vm()
        hot(model)
        model.onReindexClick()
        source.emit(running)
        model.onReindexConfirm()
        assertThat(control.reindexAllCalls).isEqualTo(0)
        assertThat(model.uiState.value.dialog).isNull()
    }

    @Test
    fun `cancel clears the dialog`() = runTest {
        val model = vm()
        hot(model)
        model.onReindexClick()
        model.onReindexCancel()
        assertThat(model.uiState.value.dialog).isNull()
        assertThat(control.reindexAllCalls).isEqualTo(0)
    }

    @Test
    fun `dialog survives a second ViewModel built from the same SavedStateHandle`() = runTest {
        val saved = SavedStateHandle()
        hot(vm(saved).also { it.onReindexClick() })
        assertThat(vm(saved).uiState.value.dialog).isEqualTo(StatusDialog.ConfirmReindex)

        permissions.set(Group.CONTACTS, GroupState.DENIED_PERMANENTLY)
        val saved2 = SavedStateHandle()
        hot(vm(saved2).also { it.onAllowClick(Group.CONTACTS) })
        assertThat(vm(saved2).uiState.value.dialog).isEqualTo(StatusDialog.AllowInSettings(Group.CONTACTS))
    }

    @Test
    fun `unknown or FILES dialog value is ignored on restore`() {
        assertThat(vm(SavedStateHandle(mapOf("dialog" to "NOPE"))).uiState.value.dialog).isNull()
        assertThat(vm(SavedStateHandle(mapOf("dialog" to "FILES"))).uiState.value.dialog).isNull()
    }

    @Test
    fun `scrollToPermissions is true only for section permissions`() {
        assertThat(vm(SavedStateHandle(mapOf("section" to "permissions"))).scrollToPermissions).isTrue()
        assertThat(vm(SavedStateHandle(mapOf("section" to "other"))).scrollToPermissions).isFalse()
        assertThat(vm().scrollToPermissions).isFalse()
    }

    @Test
    fun `no control call happens at construction`() {
        vm(SavedStateHandle(mapOf("dialog" to "REINDEX", "section" to "permissions")))
        assertThat(control.reindexAllCalls).isEqualTo(0)
        assertThat(control.catchUpNowCalls).isEqualTo(0)
        assertThat(control.onPermissionsChangedCalls).isEqualTo(0)
    }
}
