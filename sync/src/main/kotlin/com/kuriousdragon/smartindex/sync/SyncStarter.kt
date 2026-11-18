package com.kuriousdragon.smartindex.sync

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.sync.di.SyncIo
import com.kuriousdragon.smartindex.sync.di.SyncScope
import com.kuriousdragon.smartindex.sync.work.ArmPolicy
import com.kuriousdragon.smartindex.sync.work.SyncScheduler
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App start-up of the orchestration (LLD — Indexing orchestration §6.5): re-arms the periodic and trigger
 * work when indexing is enabled and reacts to permission changes. Called from `Application.onCreate`.
 */
@Singleton
class SyncStarter @Inject constructor(
    private val prefs: SyncPrefs,
    private val scheduler: SyncScheduler,
    private val permissions: PermissionService,
    private val control: DefaultIndexControl,
    private val foregroundSync: ForegroundSync,
    @SyncScope private val scope: CoroutineScope,
    @SyncIo private val io: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)

    /** The process lifecycle [foregroundSync] observes; replaceable in tests. */
    internal var processLifecycle: () -> Lifecycle = { ProcessLifecycleOwner.get().lifecycle }

    @MainThread
    fun start() {
        if (!started.compareAndSet(false, true)) return
        processLifecycle().addObserver(foregroundSync)
        scope.launch(io) {
            if (prefs.enabled) {
                scheduler.ensurePeriodic()
                scheduler.armTriggers(prefs.grantedGroups.orEmpty(), ArmPolicy.KEEP)
            }
            permissions.state
                .map { TypeGating.usableGroups(it) }
                .distinctUntilChanged()
                .collect { usable ->
                    val ledger = prefs.grantedGroups
                    if (prefs.enabled && ledger != null && usable != ledger) {
                        control.onPermissionsChanged()
                        withContext(Dispatchers.Main) { foregroundSync.refreshObservers(usable) }
                    }
                }
        }
    }
}
