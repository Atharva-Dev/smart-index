package com.kuriousdragon.smartindex.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.SyncReason
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkSyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkSyncScheduler
    private var latch = CompletableDeferred<Unit>()

    private inner class HeldWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
        override suspend fun doWork(): Result {
            latch.await()
            return Result.success()
        }
    }

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = HeldWorker(appContext, workerParameters)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkSyncScheduler(workManager)
    }

    @After
    fun tearDown() {
        latch.complete(Unit)
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    /**
     * Seeds [name] with a delayed (hence ENQUEUED) work: the test scheduler starts undelayed work at once,
     * so this is the only way to observe a stable ENQUEUED state under a unique work name.
     */
    private fun seedEnqueued(name: String) {
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(1, TimeUnit.HOURS).addTag(SyncWork.TAG).build(),
        ).result.get()
        assertThat(infos(name).single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    /** Starts the seeded work; its worker then suspends on [latch], so it stays RUNNING. */
    private fun startSeeded(name: String) {
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(infos(name).single().id)
        assertThat(infos(name).single().state).isEqualTo(WorkInfo.State.RUNNING)
    }

    @Test
    fun `empty queue enqueues one sync with tag`() = runBlocking {
        scheduler.enqueueSync(SyncReason.CATCH_UP, expedited = false)
        val list = infos(SyncWork.SYNC)
        assertThat(list).hasSize(1)
        // Undelayed work is started at once by the test scheduler, so it may already be RUNNING (held by the latch).
        assertThat(list.single().state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
        assertThat(list.single().tags).contains(SyncWork.TAG)
        assertThat(infos(SyncWork.SYNC_NEXT)).isEmpty()
    }

    @Test
    fun `reason is passed as input data`() = runBlocking {
        val request = scheduler.buildSyncRequest(SyncReason.OBSERVER, expedited = false)
        assertThat(request.workSpec.input.getString(SyncWork.KEY_REASON)).isEqualTo("OBSERVER")
        assertThat(request.tags).contains(SyncWork.TAG)
    }

    @Test
    fun `second call while enqueued adds nothing`() = runBlocking {
        seedEnqueued(SyncWork.SYNC)
        scheduler.enqueueSync(SyncReason.OBSERVER, false)
        assertThat(infos(SyncWork.SYNC)).hasSize(1)
        assertThat(infos(SyncWork.SYNC).single().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(infos(SyncWork.SYNC_NEXT)).isEmpty()
    }

    @Test
    fun `call while sync is running enqueues the next slot and then nothing more`() = runBlocking {
        seedEnqueued(SyncWork.SYNC)
        startSeeded(SyncWork.SYNC)

        scheduler.enqueueSync(SyncReason.OBSERVER, false)
        assertThat(infos(SyncWork.SYNC_NEXT)).hasSize(1)
        assertThat(infos(SyncWork.SYNC_NEXT).single().tags).contains(SyncWork.TAG)

        scheduler.enqueueSync(SyncReason.PERIODIC, false)
        assertThat(infos(SyncWork.SYNC)).hasSize(1)
        assertThat(infos(SyncWork.SYNC_NEXT)).hasSize(1)
    }

    @Test
    fun `expedited flag is set only when requested`() {
        assertThat(scheduler.buildSyncRequest(SyncReason.CATCH_UP, true).workSpec.expedited).isTrue()
        assertThat(scheduler.buildSyncRequest(SyncReason.CATCH_UP, false).workSpec.expedited).isFalse()
    }

    @Test
    fun `ensurePeriodic twice keeps one 15 minute work with 5 minute flex and battery not low`() {
        scheduler.ensurePeriodic()
        scheduler.ensurePeriodic()
        val list = infos(SyncWork.PERIODIC)
        assertThat(list).hasSize(1)
        val spec = workManager.getWorkInfoById(list.single().id).get()!!
        assertThat(spec.tags).contains(SyncWork.TAG)
        assertThat(spec.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(spec.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(15))
        assertThat(spec.periodicityInfo!!.flexIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(5))
    }

    private val groups = setOf(Group.PHOTOS_VIDEOS, Group.MESSAGES, Group.CONTACTS)

    @Test
    fun `armTriggers enqueues the trigger with the uris and delays`() {
        scheduler.armTriggers(groups, ArmPolicy.KEEP)
        val info = infos(SyncWork.TRIGGER).single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.tags).contains(SyncWork.TAG)
        val triggers = info.constraints.contentUriTriggers
        assertThat(triggers.map { it.uri }).containsExactlyElementsIn(TriggerUris.forGroups(groups)).inOrder()
        assertThat(triggers.all { it.isTriggeredForDescendants }).isTrue()
        assertThat(info.constraints.contentTriggerUpdateDelayMillis).isEqualTo(10_000L)
        assertThat(info.constraints.contentTriggerMaxDelayMillis).isEqualTo(60_000L)
    }

    @Test
    fun `armTriggers with no groups cancels the trigger`() {
        scheduler.armTriggers(groups, ArmPolicy.KEEP)
        scheduler.armTriggers(emptySet(), ArmPolicy.KEEP)
        assertThat(infos(SyncWork.TRIGGER).single().state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `KEEP keeps an enqueued trigger`() {
        scheduler.armTriggers(groups, ArmPolicy.KEEP)
        val id = infos(SyncWork.TRIGGER).single().id
        scheduler.armTriggers(groups, ArmPolicy.KEEP)
        assertThat(infos(SyncWork.TRIGGER).single().id).isEqualTo(id)
    }

    @Test
    fun `REPLACE replaces the trigger`() {
        scheduler.armTriggers(groups, ArmPolicy.KEEP)
        val id = infos(SyncWork.TRIGGER).single().id
        scheduler.armTriggers(groups, ArmPolicy.REPLACE)
        val now = infos(SyncWork.TRIGGER).single { it.state == WorkInfo.State.ENQUEUED }
        assertThat(now.id).isNotEqualTo(id)
    }
}
