package com.kuriousdragon.smartindex.core.index.debug

import androidx.room.useReaderConnection
import com.kuriousdragon.smartindex.core.index.BuildConfig
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.model.SiLog
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Debug-only storage-size logging (LLD §6.3). Logs `db=<MB> wal=<MB> top=[name <MB>, …] items=<n>` and never
 * throws: any failure becomes a WARN carrying only the exception class name.
 */
class StorageReporter @Inject constructor(private val db: IndexDatabase) {
    suspend fun reportIfDebug() {
        if (!BuildConfig.DEBUG) return
        try {
            withContext(Dispatchers.IO) { report() }
        } catch (e: Exception) {
            // A cancelled caller must still stop; a cancellation coming from a closed database is just a failure.
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            SiLog.w(TAG, "storage report failed", e)
        }
    }

    private suspend fun report() {
        val line = db.useReaderConnection { c ->
            val bytes = c.usePrepared(SIZE_SQL) { if (it.step()) it.getLong(0) else 0L }
            val path = c.usePrepared(PATH_SQL) { if (it.step() && !it.isNull(0)) it.getText(0) else "" }
            val items = c.usePrepared("SELECT COUNT(*) FROM item") { if (it.step()) it.getLong(0) else 0L }
            val hasDbstat = c.usePrepared(PROBE_SQL) { it.step() }
            val top = if (hasDbstat) {
                c.usePrepared(TOP_SQL) { st ->
                    buildList { while (st.step()) add("${st.getText(0)} ${mb(st.getLong(1))}") }
                }
            } else {
                emptyList()
            }
            val wal = if (path.isEmpty()) 0L else File("$path-wal").length()
            "db=${mb(bytes)} wal=${mb(wal)} top=$top items=$items"
        }
        SiLog.d(TAG, line)
    }

    private fun mb(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / BYTES_PER_MB)

    private companion object {
        const val TAG = "Index"
        const val BYTES_PER_MB = 1_048_576.0
        const val SIZE_SQL = "SELECT page_count * page_size AS bytes FROM pragma_page_count(), pragma_page_size()"
        const val PATH_SQL = "SELECT file FROM pragma_database_list WHERE name = 'main'"
        const val PROBE_SQL = "SELECT 1 FROM pragma_compile_options WHERE compile_options = 'ENABLE_DBSTAT_VTAB'"
        const val TOP_SQL = "SELECT name, SUM(pgsize) AS bytes FROM dbstat GROUP BY name ORDER BY bytes DESC LIMIT 8"
    }
}
