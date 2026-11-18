package com.kuriousdragon.smartindex.sync

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.sync.di.SyncIo
import com.kuriousdragon.smartindex.sync.di.SyncScope
import com.kuriousdragon.smartindex.sync.work.TriggerUris
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-lifecycle observer (LLD — Indexing orchestration §5.8, §6.5): every time the app comes to the
 * foreground an expedited catch-up runs and the foreground [ContentChangeObservers] are registered; both stop
 * when the app goes to the background.
 */
@Singleton
class ForegroundSync @Inject constructor(
    private val control: IndexControl,
    private val prefs: SyncPrefs,
    private val observers: ContentChangeObservers,
    @SyncScope private val scope: CoroutineScope,
    @SyncIo private val io: CoroutineDispatcher,
) : DefaultLifecycleObserver {
    private var registerJob: Job? = null
    private var owner: LifecycleOwner? = null

    override fun onStart(owner: LifecycleOwner) {
        this.owner = owner
        control.catchUpNow()
        registerJob = scope.launch {
            val groups = withContext(io) { if (prefs.enabled) prefs.grantedGroups.orEmpty() else emptySet() }
            withContext(Dispatchers.Main) {
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    observers.register(TriggerUris.forGroups(groups))
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        registerJob?.cancel()
        registerJob = null
        this.owner = null
        observers.unregister()
    }

    /** The usable groups changed: swap the observed URIs, only while the app is in the foreground. */
    @MainThread
    fun refreshObservers(groups: Set<Group>) {
        val current = owner ?: return
        if (current.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            observers.register(TriggerUris.forGroups(groups))
        }
    }
}
