package com.kuriousdragon.smartindex.source.telephony.mms

import android.provider.Telephony.Mms
import androidx.collection.MutableLongObjectMap
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyProviders
import javax.inject.Inject

/**
 * Finds the counterpart of each MMS (LLD §4.2 "MMS addresses", D8): thread participants come from two
 * queries per run (loaded lazily on the first [resolve] after [reset]); per-message `mms/<id>/addr`
 * queries are used only for group-thread received MMS or unknown threads, at most [budget] per run.
 * A `SecurityException` from any query is rethrown.
 */
class MmsAddressResolver(
    private val providers: TelephonyProviders,
    private val budget: Int = TelephonyConfig.MMS_ADDR_LOOKUP_MAX,
) {
    @Inject constructor(providers: TelephonyProviders) : this(providers, TelephonyConfig.MMS_ADDR_LOOKUP_MAX)

    private var loaded = false
    private var threadsAvailable = false
    private val threads = MutableLongObjectMap<List<String>>()
    private var lookups = 0

    /** Per-run reset: drops the thread cache and the per-id budget. */
    fun reset() {
        loaded = false
        threadsAvailable = false
        threads.clear()
        lookups = 0
    }

    fun resolve(batch: List<MmsRow>, stats: RunStatsBuilder): Map<Long, MmsParticipants> {
        if (!loaded) loadThreads(stats)
        val result = LinkedHashMap<Long, MmsParticipants>(batch.size)
        for (row in batch) result[row.id] = resolve(row, stats)
        return result
    }

    private fun resolve(row: MmsRow, stats: RunStatsBuilder): MmsParticipants {
        val received = when (row.msgBox) {
            BOX_INBOX -> true
            BOX_SENT, BOX_OUTBOX, BOX_FAILED -> false
            else -> return MmsParticipants.UNKNOWN // the mapper skips this row
        }
        val participants = if (threadsAvailable) row.threadId?.let { threads[it] }.orEmpty() else emptyList()
        return when {
            participants.size == 1 -> MmsParticipants(participants[0], emptyList())

            participants.size >= 2 && !received -> withOthers(participants[0], participants)

            participants.size >= 2 -> {
                val from = lookup(row.id, stats)?.from
                // a sender outside the thread list keeps every participant; one inside is dropped from the others
                if (from == null) MmsParticipants.UNKNOWN else withOthers(from, participants)
            }

            received -> MmsParticipants(lookup(row.id, stats)?.from, emptyList())

            else -> {
                val to = lookup(row.id, stats)?.to.orEmpty()
                val first = to.firstOrNull()
                if (first == null) MmsParticipants(null, emptyList()) else withOthers(first, to)
            }
        }
    }

    /** [counterpart] with [candidates] as others: trimmed, blanks, the counterpart and duplicates removed. */
    private fun withOthers(counterpart: String, candidates: List<String>): MmsParticipants {
        val others = ArrayList<String>()
        for (candidate in candidates) {
            val c = candidate.trim()
            if (c.isEmpty() || sameAddress(c, counterpart) || others.any { sameAddress(it, c) }) continue
            others.add(c)
        }
        return MmsParticipants(counterpart, others)
    }

    private class Addresses(val from: String?, val to: List<String>)

    /** One `content://mms/<id>/addr` query, counted against the budget; null when over budget or on failure. */
    private fun lookup(messageId: Long, stats: RunStatsBuilder): Addresses? {
        if (lookups >= budget) {
            stats.note("addr_budget")
            return null
        }
        lookups++
        var from: String? = null
        val to = ArrayList<String>()
        try {
            providers.query(MmsQueries.addr(messageId))?.use { c ->
                val reader = CursorReader(c)
                while (c.moveToNext()) {
                    val address = reader.string(Mms.Addr.ADDRESS)?.trim()
                        ?.takeIf { it.isNotEmpty() && it != MmsQueries.INSERT_ADDRESS_TOKEN } ?: continue
                    when (reader.int(Mms.Addr.TYPE)) {
                        MmsQueries.ADDR_TYPE_FROM -> if (from == null) from = address
                        MmsQueries.ADDR_TYPE_TO -> to.add(address)
                    }
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            stats.note("addr_error")
            return null
        }
        return Addresses(from, to)
    }

    /** Loads canonical addresses and threads once; a non-security failure switches the run to per-id lookups. */
    private fun loadThreads(stats: RunStatsBuilder) {
        loaded = true
        try {
            val canonical = MutableLongObjectMap<String>()
            val canonicalCursor = providers.query(MmsQueries.canonicalAddresses())
                ?: throw IllegalStateException("canonical-addresses unavailable")
            canonicalCursor.use { c ->
                val reader = CursorReader(c)
                while (c.moveToNext()) {
                    val id = reader.long(Mms._ID) ?: continue
                    val address = reader.string(Mms.Addr.ADDRESS)?.trim()
                    if (!address.isNullOrEmpty()) canonical[id] = address
                }
            }
            val threadsCursor = providers.query(MmsQueries.threads())
                ?: throw IllegalStateException("conversations unavailable")
            threadsCursor.use { c ->
                val reader = CursorReader(c)
                while (c.moveToNext()) {
                    val id = reader.long(Mms._ID) ?: continue
                    val recipients = reader.string(RECIPIENT_IDS).orEmpty()
                        .split(' ')
                        .mapNotNull { it.toLongOrNull()?.let(canonical::get) }
                    threads[id] = recipients
                }
            }
            threadsAvailable = true
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            threads.clear()
            threadsAvailable = false
            stats.note("threads_unavailable")
        }
    }

    /** Same address: equal ignoring case, or numbers with the same last 10 digits (country-code tolerant). */
    private fun sameAddress(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        return da.length >= MIN_DIGITS && db.length >= MIN_DIGITS &&
            da.takeLast(MATCH_DIGITS) == db.takeLast(MATCH_DIGITS)
    }

    private companion object {
        const val RECIPIENT_IDS = android.provider.Telephony.ThreadsColumns.RECIPIENT_IDS
        const val BOX_INBOX = 1
        const val BOX_SENT = 2
        const val BOX_OUTBOX = 4
        const val BOX_FAILED = 5
        const val MIN_DIGITS = 7
        const val MATCH_DIGITS = 10
    }
}
