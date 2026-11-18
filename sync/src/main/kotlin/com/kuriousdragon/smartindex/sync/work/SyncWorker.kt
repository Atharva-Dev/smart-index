package com.kuriousdragon.smartindex.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.SyncErrors
import com.kuriousdragon.smartindex.sync.SyncReason
import com.kuriousdragon.smartindex.sync.SyncRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one orchestration pass in WorkManager (LLD — Indexing orchestration §5.3).
 * Always succeeds except for cancellation: failed sources are retried by the next trigger,
 * foreground catch-up or the periodic backstop, so WorkManager retries are never requested.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: SyncRunner,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString(SyncWork.KEY_REASON)
            ?.let { runCatching { SyncReason.valueOf(it) }.getOrNull() }
            ?: SyncReason.PERIODIC
        try {
            runner.sync(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SiLog.w("SyncWork", "run failed code=${SyncErrors.code(t)}")
        }
        return Result.success()
    }
}
