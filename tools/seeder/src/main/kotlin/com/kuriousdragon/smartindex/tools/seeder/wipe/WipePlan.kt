package com.kuriousdragon.smartindex.tools.seeder.wipe

/** One chunked delete: `<baseUri> WHERE _id IN (ids)`. [kind] is the ledger kind (for the `wipe-end` counts). */
data class WipeStep(val kind: String, val baseUri: String, val ids: List<Long>)

/** Pure planning of the ledger deletes (LLD §4.10): ≤ [CHUNK] ids per statement, per base URI. */
object WipePlan {
    const val CHUNK = 500

    fun plan(entries: List<LedgerEntry>, keepSmsIds: Set<Long>, includeSmsMms: Boolean = true): List<WipeStep> {
        val grouped = LinkedHashMap<Pair<String, String>, MutableSet<Long>>()
        for (e in entries) {
            if (!includeSmsMms && (e.kind == "sms" || e.kind == "mms")) continue
            val cut = e.uri.lastIndexOf('/')
            if (cut <= 0) continue
            val id = e.uri.substring(cut + 1).toLongOrNull() ?: continue
            if (e.kind == "sms" && id in keepSmsIds) continue
            grouped.getOrPut(e.kind to e.uri.substring(0, cut)) { LinkedHashSet() } += id
        }
        return grouped.flatMap { (key, ids) ->
            ids.chunked(CHUNK).map { WipeStep(key.first, key.second, it) }
        }
    }
}
