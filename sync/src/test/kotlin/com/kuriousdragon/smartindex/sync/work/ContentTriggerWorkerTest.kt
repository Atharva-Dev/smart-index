package com.kuriousdragon.smartindex.sync.work

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.SyncOutcome
import com.kuriousdragon.smartindex.sync.SyncReason
import com.kuriousdragon.smartindex.sync.SyncRunner
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentTriggerWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkSyncScheduler
    private val prefs = FakeSyncPrefs(enabled = true, grantedGroups = setOf(Group.CONTACTS, Group.CALL_LOG))
    private val runs = mutableListOf<SyncReason>()

    @Before
    fun setUp() {
        val runner = SyncRunner {
            runs += it
            SyncOutcome()
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = when (workerClassName) {
                ContentTriggerWorker::class.java.name ->
                    ContentTriggerWorker(appContext, workerParameters, scheduler, prefs)

                SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, runner)

                else -> null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkSyncScheduler(workManager)
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun fire(): WorkInfo {
        scheduler.armTriggers(prefs.grantedGroups.orEmpty(), ArmPolicy.KEEP)
        val first = infos(SyncWork.TRIGGER).single()
        assertThat(first.state).isEqualTo(WorkInfo.State.ENQUEUED)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first.id)
        return first
    }

    /** The worker body runs on Dispatchers.IO, so poll (bounded) until its effects are visible. */
    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(condition()).isTrue()
    }

    @Test
    fun `trigger enqueues a non expedited content trigger sync and re-arms with a new id`() {
        val first = fire()
        awaitTrue { infos(SyncWork.TRIGGER).any { it.state == WorkInfo.State.ENQUEUED && it.id != first.id } }

        val syncs = infos(SyncWork.SYNC)
        assertThat(syncs).hasSize(1)
        assertThat(syncs.single().tags).contains(SyncWork.TAG)
        // the fake runner saw the reason passed through the input data
        assertThat(runs).containsExactly(SyncReason.CONTENT_TRIGGER)

        val triggers = infos(SyncWork.TRIGGER)
        val rearmed = triggers.single { it.state == WorkInfo.State.ENQUEUED }
        assertThat(rearmed.id).isNotEqualTo(first.id)
    }

    @Test
    fun `disabled does not enqueue a sync nor re-arm`() {
        prefs.enabled = false
        val first = fire()
        awaitTrue { infos(SyncWork.TRIGGER).single().state == WorkInfo.State.SUCCEEDED }

        assertThat(infos(SyncWork.SYNC)).isEmpty()
        assertThat(runs).isEmpty()
        val triggers = infos(SyncWork.TRIGGER)
        assertThat(triggers.map { it.id }).containsExactly(first.id)
        assertThat(triggers.single().state).isEqualTo(WorkInfo.State.SUCCEEDED)
    }
}
