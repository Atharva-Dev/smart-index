package com.kuriousdragon.smartindex.sync

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncErrors
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.core.model.TypeGating
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Type gating and purge at the start of every run (LLD — Indexing orchestration §5.5, HLD §7, ADR-008).
 * Purges revoked types, and makes "FULL needed" durable for newly granted groups and requested re-indexes.
 */
@Singleton
class PermissionGate @Inject constructor(
    private val permissions: PermissionService,
    private val writer: IndexWriter,
    private val prefs: SyncPrefs,
) {
    data class GateResult(val usable: Set<Group>, val changed: Boolean)

    suspend fun prepare(): GateResult {
        val usable = TypeGating.usableGroups(permissions.state.value)
        val previous = prefs.grantedGroups // null = never ran
        val reindex = prefs.reindexRequested
        val revoked = previous.orEmpty() - usable
        val granted = usable - previous.orEmpty()
        var purgeFailed = false
        for (g in revoked) {
            for ((source, types) in purgeTargets(g)) {
                try {
                    writer.purge(source, types)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    purgeFailed = true
                    SiLog.w(TAG, "purge failed group=$g code=${SyncErrors.code(t)}")
                }
            }
        }
        val reset = buildSet {
            (revoked + granted).forEach { g -> addAll(TypeGating.sourcesOf(g)) } // CONTACTS has no sources
            if (reindex) addAll(Source.entries)
        }
        for (s in reset) writer.saveSyncState(resetForFull(s, writer.syncState(s)))
        if (reindex) prefs.reindexRequested = false
        if (!purgeFailed && usable != previous) prefs.grantedGroups = usable
        return GateResult(usable, changed = previous == null || revoked.isNotEmpty() || granted.isNotEmpty())
    }

    private fun purgeTargets(g: Group): List<Pair<Source, Set<ItemType>?>> = when (g) {
        Group.PHOTOS_VIDEOS -> listOf(Source.MEDIA_STORE to setOf(ItemType.PHOTO, ItemType.VIDEO))

        Group.FILES ->
            listOf(Source.MEDIA_STORE to setOf(ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING))

        Group.MESSAGES -> listOf(Source.SMS to null, Source.MMS to null)

        Group.CALL_LOG -> listOf(Source.CALL_LOG to null)

        Group.CONTACTS -> emptyList()
    }

    private fun resetForFull(s: Source, prev: SyncState?): SyncState =
        (prev ?: SyncState(s, 0, null, null, null, null))
            .copy(cursor = 0, storeVersion = null, lastFullReconcile = null, lastError = null)

    private companion object {
        const val TAG = "Sync"
    }
}
