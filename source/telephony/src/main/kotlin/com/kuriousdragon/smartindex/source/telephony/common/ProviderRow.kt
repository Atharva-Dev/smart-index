package com.kuriousdragon.smartindex.source.telephony.common

/** One provider row, identified by the provider's `_id`. */
interface ProviderRow {
    val id: Long
}

fun interface RowReader<R : ProviderRow> {
    /** Returns null ONLY when `_id` is missing or ≤ 0. Every other odd value is kept and handled by the mapper. */
    fun read(reader: CursorReader): R?
}

/** The four queries every provider-backed indexer needs. */
interface SourceQueries {
    /** All eligible rows, `_id ASC`. */
    fun full(): QuerySpec

    /** Eligible rows with `_id > afterId`, `_id ASC`. */
    fun newRows(afterId: Long): QuerySpec

    /** Eligible rows dated ≥ [sinceMs] with `_id ≤ upToId`, `_id ASC`. */
    fun recent(sinceMs: Long, upToId: Long): QuerySpec

    /** Projection `[_id]` only, eligible rows, no sort. */
    fun idScan(): QuerySpec
}
