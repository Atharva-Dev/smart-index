package com.kuriousdragon.smartindex.sync

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.CallLog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncScheduler
import com.kuriousdragon.smartindex.sync.work.TriggerUris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundSyncTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val timeScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(timeScheduler)
    private val scope = TestScope(dispatcher)
    private val prefs = FakeSyncPrefs()
    private val scheduler = FakeSyncScheduler()
    private val control = DefaultIndexControl(prefs, scheduler, scope, dispatcher)
    private val observers = ContentChangeObservers(context, control, scope)
    private val foreground = ForegroundSync(control, prefs, observers, scope, dispatcher)
    private val owner = TestLifecycleOwner(Lifecycle.State.INITIALIZED, UnconfinedTestDispatcher(timeScheduler))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        owner.lifecycle.addObserver(foreground)
    }

    @After
    fun tearDown() {
        observers.unregister()
        Dispatchers.resetMain()
    }

    private fun enable(vararg groups: Group) {
        prefs.enabled = true
        prefs.grantedGroups = groups.toSet()
    }

    private fun idleLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun start() {
        owner.currentState = Lifecycle.State.STARTED
        timeScheduler.runCurrent()
    }

    private fun observerCount(uri: Uri) = shadowOf(resolver).getContentObservers(uri).size

    private fun observerSyncs() = scheduler.enqueued.count { it.first == SyncReason.OBSERVER }

    private fun notifyCallLog() {
        resolver.notifyChange(CallLog.Calls.CONTENT_URI, null)
        idleLooper()
    }

    private fun advance(ms: Long) {
        timeScheduler.advanceTimeBy(ms)
        timeScheduler.runCurrent()
    }

    @Test
    fun onStart_catchesUpAndRegistersExactlyTheTriggerUris() {
        enable(Group.CALL_LOG, Group.CONTACTS)
        start()

        assertThat(scheduler.enqueued.map { it.first }).containsExactly(SyncReason.CATCH_UP)
        val expected = TriggerUris.forGroups(setOf(Group.CALL_LOG, Group.CONTACTS))
        expected.forEach { assertThat(observerCount(it)).isEqualTo(1) }
        assertThat(observerCount(android.provider.Telephony.Sms.CONTENT_URI)).isEqualTo(0)
    }

    @Test
    fun threeChangesWithinOneSecond_requestOneObserverSyncTwoSecondsAfterTheLast() {
        enable(Group.CALL_LOG)
        start()

        notifyCallLog()
        advance(500)
        notifyCallLog()
        advance(500)
        notifyCallLog()
        advance(1_999)
        assertThat(observerSyncs()).isEqualTo(0)
        advance(1)
        assertThat(observerSyncs()).isEqualTo(1)
        advance(10_000)
        assertThat(observerSyncs()).isEqualTo(1)
    }

    @Test
    fun onStop_unregistersAndLaterChangesDoNothing() {
        enable(Group.CALL_LOG)
        start()
        assertThat(observerCount(CallLog.Calls.CONTENT_URI)).isEqualTo(1)

        owner.currentState = Lifecycle.State.CREATED
        timeScheduler.runCurrent()
        assertThat(observerCount(CallLog.Calls.CONTENT_URI)).isEqualTo(0)

        notifyCallLog()
        advance(5_000)
        assertThat(observerSyncs()).isEqualTo(0)
    }

    @Test
    fun notEnabled_registersNoObservers() {
        prefs.enabled = false
        prefs.grantedGroups = setOf(Group.CALL_LOG)
        start()

        assertThat(observerCount(CallLog.Calls.CONTENT_URI)).isEqualTo(0)
        notifyCallLog()
        advance(5_000)
        assertThat(observerSyncs()).isEqualTo(0)
    }

    @Test
    fun refreshObserversWhileStarted_swapsTheUriSet() {
        enable(Group.CALL_LOG)
        start()

        foreground.refreshObservers(setOf(Group.CONTACTS))

        assertThat(observerCount(CallLog.Calls.CONTENT_URI)).isEqualTo(0)
        TriggerUris.forGroups(setOf(Group.CONTACTS)).forEach { assertThat(observerCount(it)).isEqualTo(1) }
    }

    @Test
    fun refreshObserversWhileStopped_doesNothing() {
        enable(Group.CALL_LOG)
        start()
        owner.currentState = Lifecycle.State.CREATED
        timeScheduler.runCurrent()

        foreground.refreshObservers(setOf(Group.CALL_LOG))

        assertThat(observerCount(CallLog.Calls.CONTENT_URI)).isEqualTo(0)
    }
}
