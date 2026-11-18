package com.kuriousdragon.smartindex.core.index.query

import androidx.room.Transactor
import androidx.room.useReaderConnection
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.ItemType
import kotlinx.coroutines.runBlocking
import org.junit.Test

class QueryPlanTest : IndexDbTest() {
    private val range = DateRange(0, 100_000)
    private val fts = SearchPlan.Fts("sea view", "\"sea view\"", null, null)

    private fun plan(spec: SqlSpec): String = runBlocking {
        db.useReaderConnection { c: Transactor ->
            c.usePrepared("EXPLAIN QUERY PLAN ${spec.sql}") { st ->
                spec.args.forEachIndexed { i, a ->
                    if (a is Long) st.bindLong(i + 1, a) else st.bindText(i + 1, a as String)
                }
                buildList { while (st.step()) add(st.getText(3)) }.joinToString("\n")
            }
        }
    }

    private fun page(p: SearchPlan) = plan(SearchSql.pageSpec(p, 50, 0))

    private fun counts(p: SearchPlan) = plan(SearchSql.countsSpec(p))

    @Test
    fun `plan A scans the FTS table`() {
        assertThat(page(fts)).contains("SCAN fts_short VIRTUAL TABLE INDEX 0:M1")
        assertThat(counts(fts)).contains("VIRTUAL TABLE INDEX 0:M1")
        assertThat(page(SearchPlan.Fts("sea view", "\"sea view\"", null, ItemType.PHOTO)))
            .contains("SEARCH k USING COVERING INDEX idx_item_key_type_ts (type=?)")
    }

    @Test
    fun `plan B uses the FTS index and item_date`() {
        val p = SearchPlan.Fts("sea view", "\"sea view\"", range, null)
        assertThat(page(p)).contains("VIRTUAL TABLE INDEX 0:M1")
        assertThat(counts(p)).contains("VIRTUAL TABLE INDEX 0:M1")
    }

    @Test
    fun `plan C walks the covering index without a sort`() {
        for (type in listOf(null, ItemType.MESSAGE)) {
            val s = page(SearchPlan.Short("ra", null, type))
            assertThat(s).contains("SCAN s USING COVERING INDEX idx_short_text_scan")
            assertThat(s).doesNotContain("TEMP B-TREE FOR ORDER BY")
        }
        assertThat(counts(SearchPlan.Short("ra", null, null))).contains("SCAN s")
    }

    @Test
    fun `plan D searches item_date by the range index`() {
        val s = page(SearchPlan.Short("ra", range, null))
        assertThat(s).contains("SEARCH d USING COVERING INDEX idx_item_date_ts (ts>? AND ts<?)")
        assertThat(s).contains("SEARCH s USING INTEGER PRIMARY KEY")
        assertThat(counts(SearchPlan.Short("ra", range, null))).contains("idx_item_date_ts")
    }

    @Test
    fun `plan E searches item_date by the range index and the type index`() {
        assertThat(page(SearchPlan.Browse(range, null)))
            .contains("SEARCH d USING COVERING INDEX idx_item_date_ts (ts>? AND ts<?)")
        assertThat(page(SearchPlan.Browse(range, ItemType.PHOTO)))
            .contains("idx_item_date_type_ts (type=? AND ts>? AND ts<?)")
        assertThat(counts(SearchPlan.Browse(range, null))).contains("idx_item_date_ts")
    }
}
