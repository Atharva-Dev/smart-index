package com.kuriousdragon.smartindex.core.index.query

import androidx.room.RoomRawQuery
import com.kuriousdragon.smartindex.core.index.db.code

/** SQL and bind values of one query; values are `Long` or `String`, bound in the order of the `?`. */
internal data class SqlSpec(val sql: String, val args: List<Any>)

/** The search and timeline SQL of LLD §5.6, verbatim. Output columns are `id, ts` (pages) or `type, n` (counts). */
object SearchSql {
    private const val MATCH = "SELECT rowid FROM fts_short WHERE fts_short MATCH ?"

    fun page(plan: SearchPlan, limit: Int, offset: Int): RoomRawQuery = pageSpec(plan, limit, offset).toRaw()

    fun counts(plan: SearchPlan): RoomRawQuery = countsSpec(plan).toRaw()

    internal fun pageSpec(plan: SearchPlan, limit: Int, offset: Int): SqlSpec {
        val args = mutableListOf<Any>()
        val sql =
            when (plan) {
                SearchPlan.Empty -> throw IllegalArgumentException("Empty plan has no query")

                is SearchPlan.Fts ->
                    if (plan.range == null) {
                        args += plan.phrase
                        val t = typeClause(plan, "k", args)
                        "SELECT k.id AS id, k.ts AS ts FROM item_key k WHERE k.id IN ($MATCH)$t " +
                            "ORDER BY k.ts DESC, k.id DESC LIMIT ? OFFSET ?"
                    } else {
                        rangeArgs(plan.range, args)
                        val t = typeClause(plan, "d", args)
                        args += plan.phrase
                        "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ?$t " +
                            "AND d.item_id IN ($MATCH) GROUP BY d.item_id ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?"
                    }

                is SearchPlan.Short ->
                    if (plan.range == null) {
                        args += plan.folded
                        val t = typeClause(plan, "s", args)
                        // The type bind comes after the instr bind, matching the order of the `?`.
                        "SELECT s.id AS id, s.ts AS ts FROM short_text s INDEXED BY idx_short_text_scan " +
                            "WHERE instr(s.skey, ?) > 0$t ORDER BY s.ts DESC, s.id DESC LIMIT ? OFFSET ?"
                    } else {
                        rangeArgs(plan.range, args)
                        val t = typeClause(plan, "d", args)
                        args += plan.folded
                        "SELECT d.item_id AS id, MAX(d.ts) AS ts FROM item_date d " +
                            "JOIN short_text s ON s.id = d.item_id WHERE d.ts BETWEEN ? AND ?$t " +
                            "AND instr(s.skey, ?) > 0 GROUP BY d.item_id ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?"
                    }

                is SearchPlan.Browse -> {
                    rangeArgs(plan.range, args)
                    val t = typeClause(plan, "d", args)
                    "SELECT d.item_id AS id, MIN(d.ts) AS ts FROM item_date d WHERE d.ts BETWEEN ? AND ?$t " +
                        "GROUP BY d.item_id ORDER BY ts ASC, id ASC LIMIT ? OFFSET ?"
                }
            }
        args += limit.toLong()
        args += offset.toLong()
        return SqlSpec(sql, args)
    }

    internal fun countsSpec(plan: SearchPlan): SqlSpec {
        val args = mutableListOf<Any>()
        val sql =
            when (plan) {
                SearchPlan.Empty -> throw IllegalArgumentException("Empty plan has no query")

                is SearchPlan.Fts ->
                    if (plan.range == null) {
                        args += plan.phrase
                        "SELECT k.type AS type, COUNT(*) AS n FROM item_key k WHERE k.id IN ($MATCH) GROUP BY k.type"
                    } else {
                        rangeArgs(plan.range, args)
                        args += plan.phrase
                        "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                            "WHERE d.ts BETWEEN ? AND ? AND d.item_id IN ($MATCH) GROUP BY d.type"
                    }

                is SearchPlan.Short ->
                    if (plan.range == null) {
                        args += plan.folded
                        "SELECT s.type AS type, COUNT(*) AS n FROM short_text s " +
                            "WHERE instr(s.skey, ?) > 0 GROUP BY s.type"
                    } else {
                        rangeArgs(plan.range, args)
                        args += plan.folded
                        "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                            "JOIN short_text s ON s.id = d.item_id WHERE d.ts BETWEEN ? AND ? " +
                            "AND instr(s.skey, ?) > 0 GROUP BY d.type"
                    }

                is SearchPlan.Browse -> {
                    rangeArgs(plan.range, args)
                    "SELECT d.type AS type, COUNT(DISTINCT d.item_id) AS n FROM item_date d " +
                        "WHERE d.ts BETWEEN ? AND ? GROUP BY d.type"
                }
            }
        return SqlSpec(sql, args)
    }

    private fun rangeArgs(range: com.kuriousdragon.smartindex.core.model.DateRange, args: MutableList<Any>) {
        args += range.startMs
        args += range.endMsInclusive
    }

    /** ` AND <alias>.type = ?` and its bind value, only when the plan filters by type. */
    private fun typeClause(plan: SearchPlan, alias: String, args: MutableList<Any>): String {
        val type = plan.type ?: return ""
        args += type.code().toLong()
        return " AND $alias.type = ?"
    }

    private fun SqlSpec.toRaw(): RoomRawQuery =
        RoomRawQuery(sql) { st ->
            args.forEachIndexed { i, a ->
                when (a) {
                    is Long -> st.bindLong(i + 1, a)
                    is String -> st.bindText(i + 1, a)
                    else -> error("unsupported bind ${a.javaClass}")
                }
            }
        }
}
