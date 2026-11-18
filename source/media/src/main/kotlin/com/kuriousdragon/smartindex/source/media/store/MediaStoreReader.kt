package com.kuriousdragon.smartindex.source.media.store

import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal

/** The seam over `MediaStore.*` statics and `ContentResolver.query` (LLD — Media & file indexer §3.3.1). */
interface MediaStoreReader {
    /** `MediaStore.getExternalVolumeNames(context)`; never empty (falls back to `{VOLUME_EXTERNAL_PRIMARY}`). */
    fun externalVolumes(): Set<String>

    /** `MediaStore.getVersion(context, volume)`; "?" when the call fails (logged WARN with the class name). */
    fun version(volume: String): String

    /**
     * `ContentResolver.query(uri, projection, Bundle, signal)` with QUERY_ARG_SQL_SELECTION / _ARGS / _SORT_ORDER
     * and QUERY_ARG_MATCH_PENDING = MATCH_EXCLUDE, QUERY_ARG_MATCH_TRASHED = MATCH_EXCLUDE.
     * Returns null when the provider is unavailable. Throws what the provider throws (SecurityException, ...).
     */
    @Suppress("LongParameterList")
    fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String?,
        signal: CancellationSignal?,
    ): Cursor?
}
