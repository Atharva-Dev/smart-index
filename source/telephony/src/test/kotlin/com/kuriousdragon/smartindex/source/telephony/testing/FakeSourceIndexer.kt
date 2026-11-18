package com.kuriousdragon.smartindex.source.telephony.testing

import android.net.Uri
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.ProviderRow
import com.kuriousdragon.smartindex.source.telephony.common.ProviderSourceIndexer
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec
import com.kuriousdragon.smartindex.source.telephony.common.RecordHasher
import com.kuriousdragon.smartindex.source.telephony.common.RowReader
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.SourceQueries
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps

data class TestRow(override val id: Long, val date: Long?, val value: String?) : ProviderRow

/** Queries over the `test_rows(_id, date, value)` table that [FakeTelephonyProviders.createTestTable] adds. */
object TestQueries : SourceQueries {
    val URI: Uri = Uri.parse("content://test/rows")
    private val PROJECTION = listOf("_id", "date", "value")

    override fun full() = QuerySpec(URI, PROJECTION, sortOrder = "_id ASC")

    override fun newRows(afterId: Long) = QuerySpec(URI, PROJECTION, "_id > ?", listOf(afterId.toString()), "_id ASC")

    override fun recent(sinceMs: Long, upToId: Long) = QuerySpec(
        URI,
        PROJECTION,
        "date >= ? AND _id <= ?",
        listOf(sinceMs.toString(), upToId.toString()),
        "_id ASC",
    )

    override fun idScan() = QuerySpec(URI, listOf("_id"))
}

/** Test-only indexer that exercises the template: one MESSAGE item per `test_rows` row. */
class FakeSourceIndexer(deps: TelephonyDeps) : ProviderSourceIndexer<TestRow>(deps) {
    override val source = Source.SMS
    override val tag = "Tel.Test"
    override val queries = TestQueries
    override val reader = RowReader { r: CursorReader ->
        r.long("_id")?.takeIf { it > 0 }?.let { TestRow(it, r.long("date"), r.string("value")) }
    }
    override val requires = setOf(Group.MESSAGES)

    /** Batches handed to [map], by size. */
    val mappedBatches = mutableListOf<Int>()

    override suspend fun map(batch: List<TestRow>, stats: RunStatsBuilder): List<ItemRecord> {
        mappedBatches += batch.size
        return batch.mapNotNull { row ->
            val date = row.date?.takeIf { it > 0 }
            if (date == null) {
                stats.skip("no_date")
                return@mapNotNull null
            }
            val record = ItemRecord(
                key = SourceKey(source, row.id),
                type = ItemType.MESSAGE,
                title = row.value ?: "untitled",
                primaryTs = date,
                dates = mapOf(DateKind.EVENT to date),
                numberRaw = "+91 98765 43210",
                direction = Direction.RECEIVED,
                msgKind = MsgKind.SMS,
                body = row.value,
                sourceVersion = 0L,
            )
            record.copy(sourceVersion = RecordHasher.hash(record))
        }
    }
}

/** Adds the `test_rows` table used by [FakeSourceIndexer] and routes `content://test/rows` to it. */
fun FakeTelephonyProviders.createTestTable() {
    db.execSQL("CREATE TABLE test_rows (_id INTEGER PRIMARY KEY, date INTEGER, value TEXT)")
    route("test/rows", "test_rows")
}

fun FakeTelephonyProviders.addTestRow(id: Long, date: Long? = 1_700_000_000_000L, value: String? = "row $id") {
    db.execSQL("INSERT OR REPLACE INTO test_rows (_id, date, value) VALUES (?, ?, ?)", arrayOf<Any?>(id, date, value))
}

/** Inserts rows `1..count` with the same date, in one transaction. */
fun FakeTelephonyProviders.addTestRows(count: Int, date: Long = 1_700_000_000_000L) {
    db.beginTransaction()
    try {
        for (id in 1L..count) addTestRow(id, date)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
