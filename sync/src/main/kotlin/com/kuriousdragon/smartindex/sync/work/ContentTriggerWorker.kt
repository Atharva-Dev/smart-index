package com.kuriousdragon.smartindex.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.SyncErrors
import com.kuriousdragon.smartindex.sync.SyncPrefs
import com.kuriousdragon.smartindex.sync.SyncReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fires when a provider changes: enqueues an INCREMENTAL run and re-arms itself, because content-URI
 * triggers are one-shot (LLD — Indexing orchestration §5.3). Always succeeds.
 */
@HiltWorker
class ContentTriggerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: SyncScheduler,
    private val prefs: SyncPrefs,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (prefs.enabled) {
                scheduler.enqueueSync(SyncReason.CONTENT_TRIGGER, expedited = false)
                scheduler.armTriggers(prefs.grantedGroups.orEmpty(), ArmPolicy.APPEND)
                SiLog.d("SyncWork", "triggered uris=${triggeredContentUris.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SiLog.w("SyncWork", "trigger failed code=${SyncErrors.code(t)}")
        }
        Result.success()
    }
}
