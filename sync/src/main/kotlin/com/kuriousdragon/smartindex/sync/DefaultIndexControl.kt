package com.kuriousdragon.smartindex.sync

import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.sync.di.SyncIo
import com.kuriousdragon.smartindex.sync.di.SyncScope
import com.kuriousdragon.smartindex.sync.work.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The [IndexControl] the onboarding and Index status screens call (LLD — Indexing orchestration §3.4, §5.7,
 * §5.12). Every method returns immediately; the work happens on [scope] / [io] (prefs writes are blocking).
 */
@Singleton
class DefaultIndexControl @Inject constructor(
    private val prefs: SyncPrefs,
    private val scheduler: SyncScheduler,
    @SyncScope private val scope: CoroutineScope,
    @SyncIo private val io: CoroutineDispatcher,
) : IndexControl {
    override fun catchUpNow() = enqueueIfEnabled(SyncReason.CATCH_UP)

    override fun reindexAll() {
        scope.launch(io) {
            prefs.enabled = true
            prefs.reindexRequested = true
            scheduler.ensurePeriodic()
            scheduler.enqueueSync(SyncReason.REINDEX, expedited = true)
        }
    }

    override fun onPermissionsChanged() = enqueueIfEnabled(SyncReason.PERMISSIONS)

    /** A foreground content change was observed (used by the foreground observers). */
    fun requestObserverSync() = enqueueIfEnabled(SyncReason.OBSERVER)

    private fun enqueueIfEnabled(reason: SyncReason) {
        scope.launch(io) {
            if (prefs.enabled) scheduler.enqueueSync(reason, expedited = true)
        }
    }
}
