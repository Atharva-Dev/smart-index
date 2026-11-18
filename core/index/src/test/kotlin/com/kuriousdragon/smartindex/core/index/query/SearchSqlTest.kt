package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchSqlTest {
    private val range = DateRange(100, 200)
    private val m = "SELECT rowid FROM fts_short WHERE fts_short MATCH ?"
    private val fts = "\"sea view\""

    private fun fts(range: DateRange?, type: ItemType?) = SearchPlan.Fts("sea view", fts, range, type)

    private fun short(range: DateRange?, type: ItemType?) = SearchPlan.Short("ra", range, type)

    private fun page(plan: SearchPlan) = SearchSql.pageSpec(plan, 50, 100)

    @Test
    fun `plan A page`() {
        val s = page(fts(null, null))
        assertThat(s.sql).isEqualTo(
            "SELECT k.id AS id, k.ts AS ts FROM item_key k WHERE k.id IN ($m) ORDER BY k.ts DESC, k.id DESC LIMIT ? OFFSET ?",
        )
        assertThat(s.args).containsExactly(fts, 50L, 100L).inOrder()
        val t = page(fts(null, ItemType.PHOTO))
        assertThat(t.sql).isEqualTo(
            "SELECT k.id AS id, k.ts AS ts FROM item_key k WHERE k.id IN ($m) AND k.type = ? " +
                "ORDER BY k.ts DESC, k.id DESC LIMIT ? OFFSET ?",
        )
        assertThat(t.args).containsExactly(fts, 1L, 50L, 100L).inOrder()
    }

    @Test
    fun `plan A counts never take the type`() {
        val s = SearchSql.countsSpec(fts(null, ItemType.PHOTO))
        assertThat(
            s.sql,
        ).isEqualTo("SELECT k.type AS type, COUNT(*) AS n FROM item_key k WHERE k.id IN ($m) GROUP BY k.type")
        assertThat(s.args).containsExactly(fts)
    }

    @Test
    fun `plan B page and counts`() {
        assertThat(page(fts(range, null)).sql).isEqualTo(
            "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ? " +
                "AND d.item_id IN ($m) GROUP BY d.item_id ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
        )
        assertThat(page(fts(range, null)).args).containsExactly(100L, 200L, fts, 50L, 100L).inOrder()
        val t = page(fts(range, ItemType.MESSAGE))
        assertThat(t.sql).isEqualTo(
            "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ? AND d.type = ? " +
                "AND d.item_id IN ($m) GROUP BY d.item_id ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
        )
        assertThat(t.args).containsExactly(100L, 200L, 7L, fts, 50L, 100L).inOrder()
        val c = SearchSql.countsSpec(fts(range, ItemType.MESSAGE))
        assertThat(c.sql).isEqualTo(
            "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                "WHERE d.ts BETWEEN ? AND ? AND d.item_id IN ($m) GROUP BY d.type",
        )
        assertThat(c.args).containsExactly(100L, 200L, fts).inOrder()
    }

    @Test
    fun `plan C page and counts`() {
        val s = page(short(null, null))
        assertThat(s.sql).isEqualTo(
            "SELECT s.id AS id, s.ts AS ts FROM short_text s INDEXED BY idx_short_text_scan " +
                "WHERE instr(s.skey, ?) > 0 ORDER BY s.ts DESC, s.id DESC LIMIT ? OFFSET ?",
        )
        assertThat(s.args).containsExactly("ra", 50L, 100L).inOrder()
        val t = page(short(null, ItemType.CALL))
        assertThat(t.sql).isEqualTo(
            "SELECT s.id AS id, s.ts AS ts FROM short_text s INDEXED BY idx_short_text_scan " +
                "WHERE instr(s.skey, ?) > 0 AND s.type = ? ORDER BY s.ts DESC, s.id DESC LIMIT ? OFFSET ?",
        )
        assertThat(t.args).containsExactly("ra", 5L, 50L, 100L).inOrder()
        val c = SearchSql.countsSpec(short(null, ItemType.CALL))
        assertThat(c.sql).isEqualTo(
            "SELECT s.type AS type, COUNT(*) AS n FROM short_text s WHERE instr(s.skey, ?) > 0 GROUP BY s.type",
        )
        assertThat(c.args).containsExactly("ra")
    }

    @Test
    fun `plan D page and counts`() {
        val s = page(short(range, null))
        assertThat(s.sql).isEqualTo(
            "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d JOIN short_text s ON s.id = d.item_id " +
                "WHERE d.ts BETWEEN ? AND ? AND instr(s.skey, ?) > 0 GROUP BY d.item_id " +
                "ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
        )
        assertThat(s.args).containsExactly(100L, 200L, "ra", 50L, 100L).inOrder()
        val t = page(short(range, ItemType.VIDEO))
        assertThat(t.sql).isEqualTo(
            "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d JOIN short_text s ON s.id = d.item_id " +
                "WHERE d.ts BETWEEN ? AND ? AND d.type = ? AND instr(s.skey, ?) > 0 GROUP BY d.item_id " +
                "ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
        )
        assertThat(t.args).containsExactly(100L, 200L, 2L, "ra", 50L, 100L).inOrder()
        val c = SearchSql.countsSpec(short(range, ItemType.VIDEO))
        assertThat(c.sql).isEqualTo(
            "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                "JOIN short_text s ON s.id = d.item_id WHERE d.ts BETWEEN ? AND ? AND instr(s.skey, ?) > 0 GROUP BY d.type",
        )
        assertThat(c.args).containsExactly(100L, 200L, "ra").inOrder()
    }

    @Test
    fun `plan E page and counts`() {
        val s = page(SearchPlan.Browse(range, null))
        assertThat(s.sql).isEqualTo(
            "SELECT d.item_id AS id, MIN(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ? " +
                "GROUP BY d.item_id ORDER BY ts ASC, id ASC LIMIT ? OFFSET ?",
        )
        assertThat(s.args).containsExactly(100L, 200L, 50L, 100L).inOrder()
        val t = page(SearchPlan.Browse(range, ItemType.DOCUMENT))
        assertThat(t.sql).isEqualTo(
            "SELECT d.item_id AS id, MIN(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ? AND d.type = ? " +
                "GROUP BY d.item_id ORDER BY ts ASC, id ASC LIMIT ? OFFSET ?",
        )
        assertThat(t.args).containsExactly(100L, 200L, 3L, 50L, 100L).inOrder()
        val c = SearchSql.countsSpec(SearchPlan.Browse(range, ItemType.DOCUMENT))
        assertThat(c.sql).isEqualTo(
            "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                "WHERE d.ts BETWEEN ? AND ? GROUP BY d.type",
        )
        assertThat(c.args).containsExactly(100L, 200L).inOrder()
    }

    @Test
    fun `every placeholder has a bind value`() {
        val plans = listOf(
            fts(null, null),
            fts(range, ItemType.PHOTO),
            short(null, ItemType.CALL),
            short(range, null),
            SearchPlan.Browse(range, ItemType.VIDEO),
        )
        for (p in plans) {
            val s = page(p)
            assertThat(s.sql.count { it == '?' }).isEqualTo(s.args.size)
            val c = SearchSql.countsSpec(p)
            assertThat(c.sql.count { it == '?' }).isEqualTo(c.args.size)
        }
    }

    @Test
    fun `Empty plan is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SearchSql.page(SearchPlan.Empty, 50, 0) }
        assertThrows(IllegalArgumentException::class.java) { SearchSql.counts(SearchPlan.Empty) }
    }
}
