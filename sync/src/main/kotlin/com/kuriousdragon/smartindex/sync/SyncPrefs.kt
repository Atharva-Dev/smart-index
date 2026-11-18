package com.kuriousdragon.smartindex.sync

import android.content.Context
import androidx.core.content.edit
import com.kuriousdragon.smartindex.core.model.Group
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small durable orchestration state (LLD — Indexing orchestration §4.2). Call off the main thread. */
interface SyncPrefs {
    /** Set by the first re-index (onboarding "Start indexing"); default false. */
    var enabled: Boolean

    /** Set by `reindexAll()`, cleared by the permission gate once the resets are saved; default false. */
    var reindexRequested: Boolean

    /** Usable groups when the last gate completed (the ledger); null = never ran. Unknown names are ignored. */
    var grantedGroups: Set<Group>?

    /** Start time of the last successful matching stage, epoch ms; null = never. */
    var lastMatchRun: Long?
}

/**
 * [SyncPrefs] on the app-private SharedPreferences file `sidx_sync`. Writes use `commit()`, so they block:
 * call off the main thread.
 */
@Singleton
class SharedPrefsSyncPrefs @Inject constructor(
    @ApplicationContext context: Context,
) : SyncPrefs {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit(commit = true) { putBoolean(KEY_ENABLED, value) }
        }

    override var reindexRequested: Boolean
        get() = prefs.getBoolean(KEY_REINDEX_REQUESTED, false)
        set(value) {
            prefs.edit(commit = true) { putBoolean(KEY_REINDEX_REQUESTED, value) }
        }

    override var grantedGroups: Set<Group>?
        get() {
            val names = prefs.getStringSet(KEY_GRANTED_GROUPS, null) ?: return null
            return names.mapNotNullTo(mutableSetOf()) { name -> Group.entries.firstOrNull { it.name == name } }
        }
        set(value) {
            prefs.edit(commit = true) {
                if (value == null) {
                    remove(KEY_GRANTED_GROUPS)
                } else {
                    putStringSet(KEY_GRANTED_GROUPS, value.mapTo(HashSet()) { it.name })
                }
            }
        }

    override var lastMatchRun: Long?
        get() = if (prefs.contains(KEY_LAST_MATCH_RUN)) prefs.getLong(KEY_LAST_MATCH_RUN, 0L) else null
        set(value) {
            prefs.edit(commit = true) {
                if (value == null) remove(KEY_LAST_MATCH_RUN) else putLong(KEY_LAST_MATCH_RUN, value)
            }
        }

    private companion object {
        const val FILE = "sidx_sync"
        const val KEY_ENABLED = "enabled"
        const val KEY_REINDEX_REQUESTED = "reindex_requested"
        const val KEY_GRANTED_GROUPS = "granted_groups"
        const val KEY_LAST_MATCH_RUN = "last_match_run"
    }
}
