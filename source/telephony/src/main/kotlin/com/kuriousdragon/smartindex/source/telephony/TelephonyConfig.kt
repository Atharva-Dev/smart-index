package com.kuriousdragon.smartindex.source.telephony

/** Module constants (LLD — Telephony indexer §6.1). Batch size is `IndexConfig.UPSERT_BATCH_SIZE`. */
object TelephonyConfig {
    const val RECENT_WINDOW_MS = 48L * 60 * 60 * 1000 // INCREMENTAL re-read window
    const val CHECKPOINT_EVERY_BATCHES = 10 // FULL: save cursor every 10 × 500 rows
    const val MAX_BODY_CHARS = 10_000 // SMS/MMS body cap
    const val MMS_TEXT_PART_MAX_BYTES = 65_536 // openText fallback cap
    const val MMS_ADDR_LOOKUP_MAX = 5_000 // per-id addr queries per run
    const val CONTACTS_FRESH_MS = 30_000L // ContactsResolver freshness window
    const val CONTACT_NAMES_CHUNK = 200 // keys per setContactNames call
    const val DISPLAY_CACHE_MAX = 20_000 // NumberDisplay cache size before clear()
    const val MMS_PARTS_IN_MAX = 500 // ids per `mid IN (…)` (= batch size)
}
