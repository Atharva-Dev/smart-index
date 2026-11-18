package com.kuriousdragon.smartindex.sync

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncScheduler
import com.kuriousdragon.smartindex.sync.work.ArmPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncStarterTest {
    private val prefs = FakeSyncPrefs()
    private val scheduler = FakeSyncScheduler()
    private val permissions = FakePermissions()
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakePermissions : PermissionService {
        val flow = MutableStateFlow(states())
        override val state: StateFlow<Map<Group, GroupState>> get() = flow
        override fun request(group: Group): PermissionRequest = error("not used")
        override fun settingsIntent(group: Group): Intent = error("not used")
        override fun onRuntimeResult(result: RuntimeResult) = Unit
        override fun refresh() = Unit

        companion object {
            fun states(vararg granted: Group): Map<Group, GroupState> =
                Group.entries.associateWith { if (it in granted) GroupState.GRANTED else GroupState.DENIED }
        }
    }

    private class Harness(val starter: SyncStarter, val control: DefaultIndexControl)

    private fun TestScope.harness(): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val control = DefaultIndexControl(prefs, scheduler, this, dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        val foreground =
            ForegroundSync(control, prefs, ContentChangeObservers(context, control, this), this, dispatcher)
        val starter = SyncStarter(prefs, scheduler, permissions, control, foreground, this, dispatcher)
        starter.processLifecycle = { lifecycleOwner.lifecycle }
        return Harness(starter, control)
    }

    private fun permissionEnqueues() = scheduler.enqueued.filter { it.first == SyncReason.PERMISSIONS }

    @Test
    fun notEnabled_noSchedulerCalls() = runTest {
        harness().starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(scheduler.periodicCalls).isEqualTo(0)
        assertThat(scheduler.arms).isEmpty()
        assertThat(scheduler.enqueued).isEmpty()
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun enabled_ensuresPeriodicAndArmsTriggersWithLedger() = runTest {
        prefs.enabled = true
        prefs.grantedGroups = setOf(Group.CALL_LOG)
        permissions.flow.value = FakePermissions.states(Group.CALL_LOG)
        harness().starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(scheduler.periodicCalls).isEqualTo(1)
        assertThat(scheduler.arms).containsExactly(setOf(Group.CALL_LOG) to ArmPolicy.KEEP)
        assertThat(permissionEnqueues()).isEmpty()
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun usableSetDiffersFromLedger_callsOnPermissionsChangedOnce() = runTest {
        prefs.enabled = true
        prefs.grantedGroups = setOf(Group.CALL_LOG)
        permissions.flow.value = FakePermissions.states(Group.CALL_LOG, Group.CONTACTS)
        harness().starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(permissionEnqueues()).hasSize(1)
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun usableSetEqualsLedger_doesNothing() = runTest {
        prefs.enabled = true
        prefs.grantedGroups = setOf(Group.CONTACTS)
        permissions.flow.value = FakePermissions.states(Group.CONTACTS)
        harness().starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(permissionEnqueues()).isEmpty()
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun neverRan_ledgerNull_doesNothing() = runTest {
        prefs.enabled = true
        prefs.grantedGroups = null
        permissions.flow.value = FakePermissions.states(Group.CONTACTS)
        harness().starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(permissionEnqueues()).isEmpty()
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun startTwice_launchesOneCollector() = runTest {
        prefs.enabled = true
        prefs.grantedGroups = setOf(Group.CONTACTS)
        permissions.flow.value = FakePermissions.states(Group.CONTACTS)
        val h = harness()
        h.starter.start()
        h.starter.start()
        testScheduler.advanceUntilIdle()
        assertThat(scheduler.periodicCalls).isEqualTo(1)

        permissions.flow.value = FakePermissions.states(Group.CONTACTS, Group.CALL_LOG)
        testScheduler.advanceUntilIdle()
        assertThat(permissionEnqueues()).hasSize(1)
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }

    @Test
    fun startTwice_registersTheLifecycleObserverOnce() = runTest {
        val h = harness()
        h.starter.start()
        h.starter.start()
        testScheduler.advanceUntilIdle()

        assertThat(lifecycleOwner.registry.observerCount).isEqualTo(1)
        coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
    }
}
