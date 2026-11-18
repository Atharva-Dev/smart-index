package com.kuriousdragon.smartindex.sync.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.sync.SyncReason
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** WorkManager-backed [SyncScheduler]: two-slot run queue and the 15-minute periodic backstop (LLD §5.3). */
@Singleton
class WorkSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {
    private val lock = Mutex()

    override suspend fun enqueueSync(reason: SyncReason, expedited: Boolean) {
        lock.withLock {
            // in-process Mutex: check-then-enqueue is atomic within the app
            val first = activeInfo(SyncWork.SYNC)
            if (first == null) {
                enqueue(SyncWork.SYNC, reason, expedited)
            } else if (first.state == WorkInfo.State.RUNNING && activeInfo(SyncWork.SYNC_NEXT) == null) {
                enqueue(SyncWork.SYNC_NEXT, reason, expedited)
            } // else: a run that has not read cursors yet already covers this request
        }
    }

    override fun ensurePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            SyncWork.PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(
                IndexConfig.PERIODIC_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
                IndexConfig.PERIODIC_FLEX_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInputData(workDataOf(SyncWork.KEY_REASON to SyncReason.PERIODIC.name))
                .addTag(SyncWork.TAG)
                .build(),
        )
    }

    override fun armTriggers(groups: Set<Group>, policy: ArmPolicy) {
        val uris = TriggerUris.forGroups(groups)
        if (uris.isEmpty()) {
            workManager.cancelUniqueWork(SyncWork.TRIGGER)
            return
        }
        val request = OneTimeWorkRequestBuilder<ContentTriggerWorker>()
            .setConstraints(
                Constraints.Builder()
                    .apply { uris.forEach { addContentUriTrigger(it, true) } }
                    .setTriggerContentUpdateDelay(Duration.ofMillis(IndexConfig.CONTENT_TRIGGER_UPDATE_DELAY_MS))
                    .setTriggerContentMaxDelay(Duration.ofMillis(IndexConfig.CONTENT_TRIGGER_MAX_DELAY_MS))
                    .build(),
            )
            .addTag(SyncWork.TAG)
            .build()
        val existing = when (policy) {
            ArmPolicy.KEEP -> ExistingWorkPolicy.KEEP
            ArmPolicy.REPLACE -> ExistingWorkPolicy.REPLACE
            ArmPolicy.APPEND -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        workManager.enqueueUniqueWork(SyncWork.TRIGGER, existing, request)
        SiLog.d("SyncWork", "arm policy=$policy uris=${uris.size}")
    }

    private suspend fun activeInfo(name: String): WorkInfo? =
        workManager.getWorkInfosForUniqueWorkFlow(name).first().firstOrNull {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.BLOCKED
        }

    private fun enqueue(name: String, reason: SyncReason, expedited: Boolean) {
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, buildSyncRequest(reason, expedited))
    }

    internal fun buildSyncRequest(reason: SyncReason, expedited: Boolean): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWork.KEY_REASON to reason.name))
            .addTag(SyncWork.TAG)
            .apply { if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
}
