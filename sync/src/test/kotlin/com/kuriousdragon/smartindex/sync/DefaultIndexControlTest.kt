package com.kuriousdragon.smartindex.sync

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIndexControlTest {
    private val prefs = FakeSyncPrefs()
    private val scheduler = FakeSyncScheduler()

    private fun TestScope.control() =
        DefaultIndexControl(prefs, scheduler, this, StandardTestDispatcher(testScheduler))

    @Test
    fun reindexAll_setsBothFlagsAndEnqueuesExpeditedReindex() = runTest {
        val control = control()
        control.reindexAll()
        // Returns immediately: nothing has run yet.
        assertThat(prefs.enabled).isFalse()
        testScheduler.advanceUntilIdle()

        assertThat(prefs.enabled).isTrue()
        assertThat(prefs.reindexRequested).isTrue()
        assertThat(scheduler.periodicCalls).isEqualTo(1)
        assertThat(scheduler.enqueued).containsExactly(SyncReason.REINDEX to true)
    }

    @Test
    fun otherMethods_areNoOpsWhenNotEnabled() = runTest {
        val control = control()
        control.catchUpNow()
        control.onPermissionsChanged()
        control.requestObserverSync()
        testScheduler.advanceUntilIdle()

        assertThat(scheduler.enqueued).isEmpty()
        assertThat(scheduler.periodicCalls).isEqualTo(0)
    }

    @Test
    fun catchUpNow_whenEnabled_enqueuesExpeditedCatchUp() = runTest {
        prefs.enabled = true
        control().catchUpNow()
        testScheduler.advanceUntilIdle()
        assertThat(scheduler.enqueued).containsExactly(SyncReason.CATCH_UP to true)
    }

    @Test
    fun onPermissionsChanged_whenEnabled_enqueuesExpeditedPermissions() = runTest {
        prefs.enabled = true
        control().onPermissionsChanged()
        testScheduler.advanceUntilIdle()
        assertThat(scheduler.enqueued).containsExactly(SyncReason.PERMISSIONS to true)
    }

    @Test
    fun requestObserverSync_whenEnabled_enqueuesExpeditedObserver() = runTest {
        prefs.enabled = true
        control().requestObserverSync()
        testScheduler.advanceUntilIdle()
        assertThat(scheduler.enqueued).containsExactly(SyncReason.OBSERVER to true)
    }
}
