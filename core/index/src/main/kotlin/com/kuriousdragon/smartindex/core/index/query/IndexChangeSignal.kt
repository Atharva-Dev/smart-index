package com.kuriousdragon.smartindex.core.index.query

import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.model.SiLog
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Tells paging sources that the index changed (LLD §5.7): listeners are called on the first change, then
 * further changes are held back for [THROTTLE_MS], so a list refreshes at most about once per second.
 */
@Singleton
class IndexChangeSignal @Inject constructor(db: IndexDatabase) {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            try {
                db.invalidationTracker
                    .createFlow(
                        "item",
                        "item_key",
                        "item_date",
                        "short_text",
                        "recording_link",
                        emitInitialState = false,
                    )
                    .conflate()
                    .collect {
                        listeners.forEach { l -> l() }
                        delay(THROTTLE_MS)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SiLog.w("Index", "change signal stopped", e)
            }
        }
    }

    /** Adds [listener]; the returned function removes it. */
    fun register(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    companion object {
        const val THROTTLE_MS = 1_000L
    }
}
