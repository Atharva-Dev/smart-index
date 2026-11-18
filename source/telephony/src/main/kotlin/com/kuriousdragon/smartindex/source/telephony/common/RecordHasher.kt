package com.kuriousdragon.smartindex.source.telephony.common

import com.kuriousdragon.smartindex.core.model.ItemRecord

/** `sourceVersion` of a mapped record: a 64-bit content hash, used by the store as an equality token only. */
object RecordHasher {
    /** Bump whenever a mapper changes its output for the same provider row. */
    const val MAPPING_VERSION = 2

    private const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
    private const val SEPARATOR = "\u0000"
    private const val NULL_MARK = "\u0001"

    /**
     * FNV-1a 64 over the UTF-8 bytes of [MAPPING_VERSION] and every [ItemRecord] field except
     * `sourceVersion`, in declaration order: `source`, `sourceId`, `type`, `title`, `primaryTs`, `dates`,
     * `folder`, `mime`, `ext`, `sizeBytes`, `durationMs`, `contentUri`, `numberRaw`, `direction`, `msgKind`,
     * `threadId`, `hasAttachment`, `body`, then `otherNumbers` joined by `,` in list order but only when it is
     * not empty (so records without further participants keep their pre-v2 hash text). Each field is followed by `\u0000`; null is written as `\u0001`.
     * `dates` is written as `KIND=ts` pairs in `DateKind` order joined by `,`, so map order never matters.
     */
    fun hash(record: ItemRecord): Long {
        var h = FNV_OFFSET
        fun put(value: Any?) {
            h = update(h, (value?.toString() ?: NULL_MARK).toByteArray(Charsets.UTF_8))
            h = update(h, SEPARATOR.toByteArray(Charsets.UTF_8))
        }
        put(MAPPING_VERSION)
        put(record.key.source)
        put(record.key.sourceId)
        put(record.type)
        put(record.title)
        put(record.primaryTs)
        put(record.dates.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key}=${it.value}" })
        put(record.folder)
        put(record.mime)
        put(record.ext)
        put(record.sizeBytes)
        put(record.durationMs)
        put(record.contentUri)
        put(record.numberRaw)
        put(record.direction)
        put(record.msgKind)
        put(record.threadId)
        put(record.hasAttachment)
        put(record.body)
        if (record.otherNumbers.isNotEmpty()) put(record.otherNumbers.joinToString(","))
        return h
    }

    /** FNV-1a 64 of [bytes] (also used for the contact-name hashes in `ContactSnapshotStore`). */
    fun fnv1a64(bytes: ByteArray): Long = update(FNV_OFFSET, bytes)

    private fun update(seed: Long, bytes: ByteArray): Long {
        var h = seed
        for (b in bytes) h = (h xor (b.toLong() and 0xff)) * FNV_PRIME
        return h
    }
}
