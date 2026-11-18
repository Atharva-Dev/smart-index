package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentResolver
import android.provider.CallLog
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.gen.PHONE_ACCOUNT_ID
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedCall
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import kotlin.coroutines.cancellation.CancellationException

/** Bulk-inserts calls in chunks of [CHUNK]; ids are read back by the marker account and the chunk's date range. */
class CallLogWriter(private val resolver: ContentResolver) {
    suspend fun write(calls: List<SeedCall>, ledger: Ledger, progress: (Int) -> Unit): WriteStats {
        var written = 0
        var failed = 0
        var done = 0
        val seen = HashSet<Long>()
        for (chunk in calls.chunked(CHUNK)) {
            val inserted = try {
                resolver.bulkInsert(CallLog.Calls.CONTENT_URI, chunk.map(Rows::callValues).toTypedArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SeedLog.error("CALLS SecurityException")
                return WriteStats(written, failed, stopped = true)
            } catch (e: Exception) {
                0
            }
            written += inserted
            failed += chunk.size - inserted
            if (inserted > 0) readBackIds(chunk.minOf { it.startMs }, chunk.maxOf { it.startMs }, seen, ledger)
            done += chunk.size
            progress(done)
        }
        return WriteStats(written, failed)
    }

    private fun readBackIds(minMs: Long, maxMs: Long, seen: MutableSet<Long>, ledger: Ledger) {
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            "${CallLog.Calls.PHONE_ACCOUNT_ID} = ? AND ${CallLog.Calls.DATE} BETWEEN ? AND ?",
            arrayOf(PHONE_ACCOUNT_ID, minMs.toString(), maxMs.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (seen.add(id)) ledger.append("call", "${CallLog.Calls.CONTENT_URI}/$id")
            }
        }
    }

    companion object {
        const val CHUNK = 500
    }
}
