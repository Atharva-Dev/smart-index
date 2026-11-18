package com.kuriousdragon.smartindex.source.media.testing

import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.CancellationSignal
import com.kuriousdragon.smartindex.source.media.map.MediaRow
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader

/** One recorded [MediaStoreReader.query] call. */
class RecordedQuery(
    val uri: Uri,
    val projection: List<String>,
    val selection: String?,
    val args: List<String>?,
    val sort: String?,
    val signal: CancellationSignal?,
)

/** In-memory [MediaStoreReader]: selections, sorts, projections, per-volume versions and fault injection. */
internal class FakeMediaStoreReader(rows: List<MediaRow> = emptyList()) : MediaStoreReader {
    val rows: MutableList<MediaRow> = rows.toMutableList()

    /** Volume name → version; mutate to simulate a mounted / unmounted / rebuilt volume. */
    val versions: MutableMap<String, String> = linkedMapOf("external_primary" to "v1")
    val queries: MutableList<RecordedQuery> = mutableListOf()

    /** Number of cursors handed out that were closed. */
    var closedCursors = 0
        private set
    var openedCursors = 0
        private set
    private val failures = mutableMapOf<Int, Throwable>()
    private val nulls = mutableSetOf<Int>()

    /** The n-th query (1-based, counted from now on over all queries of this fake) throws [t]. */
    fun failOnQuery(n: Int, t: Throwable) {
        failures[n] = t
    }

    /** The n-th query (1-based) returns null. */
    fun nullCursorOnQuery(n: Int) {
        nulls += n
    }

    override fun externalVolumes(): Set<String> = versions.keys.toSet()

    override fun version(volume: String): String = versions[volume] ?: "?"

    override fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String?,
        signal: CancellationSignal?,
    ): Cursor? {
        queries += RecordedQuery(uri, projection.toList(), selection, args?.toList(), sortOrder, signal)
        val n = queries.size
        failures[n]?.let { throw it }
        if (n in nulls) return null
        var out = rows.toList()
        if (selection != null) {
            val c0 = args!![0].toLong()
            val partial = selection.contains("media_type IN (1,3)")
            out = out.filter { (it.generationModified ?: 0L) > c0 || (partial && it.mediaType in setOf(1, 3)) }
        }
        out = when (sortOrder) {
            "generation_modified ASC, _id ASC" -> out.sortedWith(compareBy({ it.generationModified }, { it.id }))
            "_id ASC" -> out.sortedBy { it.id }
            else -> out
        }
        openedCursors++
        return object : CursorWrapper(cursorOf(out, projection)) {
            override fun close() {
                if (!isClosed) closedCursors++
                super.close()
            }
        }
    }
}
