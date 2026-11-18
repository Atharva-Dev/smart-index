package com.kuriousdragon.smartindex.source.telephony.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.RecordHasher
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyClock
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyProviders
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ContactsOutcome {
    data class Updated(val pushed: Int, val removed: Int) : ContactsOutcome

    data object Unchanged : ContactsOutcome

    /** Skipped: built less than 30 s ago. */
    data object Fresh : ContactsOutcome

    data class NoPermission(val cleared: Int) : ContactsOutcome

    data class Failed(val errorClass: String) : ContactsOutcome
}

/**
 * Builds `number_norm → display name` from ContactsContract and pushes only what changed with
 * `IndexWriter.setContactNames` (LLD — Telephony indexer §4.8, §5.4). Without READ_CONTACTS the
 * names are cleared and items show numbers only (ADR-008). Logs never contain numbers or names.
 */
@Singleton
class ContactsResolver @Inject constructor(
    private val providers: TelephonyProviders,
    private val writer: IndexWriter,
    private val permissions: PermissionService,
    private val normalizer: PhoneNumberNormalizer,
    private val snapshot: ContactSnapshotStore,
    private val clock: TelephonyClock,
) {
    private val mutex = Mutex()
    private var lastBuiltElapsed: Long? = null

    /** Never throws except [CancellationException]. [full] = push the whole map (FULL runs), else the diff. */
    suspend fun ensureFresh(full: Boolean): ContactsOutcome = mutex.withLock {
        val startedAt = clock.elapsedMs()
        val outcome = try {
            resolve(full, startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ContactsOutcome.Failed(e.javaClass.simpleName)
        }
        log(outcome, clock.elapsedMs() - startedAt)
        outcome
    }

    private suspend fun resolve(full: Boolean, startedAt: Long): ContactsOutcome {
        val last = lastBuiltElapsed
        if (last != null && startedAt - last < TelephonyConfig.CONTACTS_FRESH_MS) return ContactsOutcome.Fresh

        if (permissions.state.value[Group.CONTACTS] != GroupState.GRANTED) {
            val known = snapshot.read()
            if (known.isEmpty()) return ContactsOutcome.NoPermission(0)
            pushChunks(known.keys.associateWith { null })
            snapshot.delete()
            lastBuiltElapsed = null
            return ContactsOutcome.NoPermission(known.size)
        }

        val map = buildMap()
        val previous = snapshot.read()
        val hashes = map.mapValues { (_, name) -> nameHash(name) }
        val removed = previous.keys.filter { it !in map }
        val toPush = LinkedHashMap<String, String?>()
        if (full || previous.isEmpty()) {
            toPush.putAll(map)
        } else {
            for ((key, hash) in hashes) if (previous[key] != hash) toPush[key] = map.getValue(key)
        }
        for (key in removed) toPush[key] = null

        // Even when nothing changes the map was just built, so the freshness window starts now.
        if (toPush.isEmpty()) {
            lastBuiltElapsed = startedAt
            return ContactsOutcome.Unchanged
        }
        pushChunks(toPush)
        snapshot.write(hashes)
        lastBuiltElapsed = startedAt
        return ContactsOutcome.Updated(pushed = toPush.size - removed.size, removed = removed.size)
    }

    /** Visible for tests: builds `number_norm → display name` from ContactsContract phones. */
    internal fun buildMap(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val cursor = providers.query(ContactsQueries.phones()) ?: error("contacts provider unavailable")
        cursor.use { c ->
            val reader = CursorReader(c)
            while (c.moveToNext()) {
                val name = reader.string(Phone.DISPLAY_NAME)?.trim()
                if (name.isNullOrEmpty()) continue
                // The query sort makes super-primary, then starred, then the lowest contact id win.
                val keys = setOfNotNull(
                    normalizer.normalize(reader.string(Phone.NORMALIZED_NUMBER)),
                    normalizer.normalize(reader.string(Phone.NUMBER)),
                )
                for (key in keys) map.putIfAbsent(key, name)
            }
        }
        return map
    }

    private suspend fun pushChunks(names: Map<String, String?>) {
        names.entries.chunked(TelephonyConfig.CONTACT_NAMES_CHUNK).forEach { chunk ->
            writer.setContactNames(chunk.associate { it.key to it.value })
        }
    }

    private fun nameHash(name: String): Long = RecordHasher.fnv1a64(name.toByteArray(Charsets.UTF_8))

    private fun log(outcome: ContactsOutcome, ms: Long) {
        when (outcome) {
            is ContactsOutcome.Failed -> SiLog.w(TAG, "contacts failed class=${outcome.errorClass}")

            is ContactsOutcome.Updated ->
                SiLog.i(TAG, "contacts outcome=Updated pushed=${outcome.pushed} removed=${outcome.removed} ms=$ms")

            is ContactsOutcome.NoPermission ->
                SiLog.i(TAG, "contacts outcome=NoPermission cleared=${outcome.cleared} ms=$ms")

            ContactsOutcome.Unchanged -> SiLog.i(TAG, "contacts outcome=Unchanged ms=$ms")

            ContactsOutcome.Fresh -> SiLog.i(TAG, "contacts outcome=Fresh ms=$ms")
        }
    }

    private companion object {
        const val TAG = "Tel.Contacts"
    }
}
