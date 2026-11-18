package com.kuriousdragon.smartindex.core.index.text

/** Match ranges and message snippets in original-string offsets (LLD §5.8). */
object Highlighter {
    const val SNIPPET_LEAD = 30
    const val SNIPPET_MAX = 160
    private const val ELLIPSIS = "…"

    data class Snippet(val text: String, val ranges: List<IntRange>)

    /** Every non-overlapping occurrence of [foldedQuery] in [text], in original offsets, merged. */
    fun ranges(text: String?, foldedQuery: String): List<IntRange> {
        if (text.isNullOrEmpty() || foldedQuery.isEmpty()) return emptyList()
        val f = TextFolder.foldMapped(text)
        val found = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val at = f.folded.indexOf(foldedQuery, from)
            if (at < 0) break
            found += f.toOriginal(at, at + foldedQuery.length)
            from = at + foldedQuery.length
        }
        return merge(found)
    }

    /** Like [ranges], plus a digits-only match that skips separators (`9876543210` in `+91 98765 43210`). */
    fun numberRanges(number: String?, foldedQuery: String): List<IntRange> {
        val direct = ranges(number, foldedQuery)
        if (direct.isNotEmpty() || number == null || foldedQuery.isEmpty() || !foldedQuery.all { it in '0'..'9' }) {
            return direct
        }
        val digits = StringBuilder()
        val pos = ArrayList<Int>()
        number.forEachIndexed { i, c ->
            if (c in '0'..'9') {
                digits.append(c)
                pos += i
            }
        }
        val found = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val a = digits.indexOf(foldedQuery, from)
            if (a < 0) break
            found += pos[a]..pos[a + foldedQuery.length - 1]
            from = a + foldedQuery.length
        }
        return merge(found)
    }

    fun snippet(body: String?, foldedQuery: String?): Snippet? {
        if (body.isNullOrBlank()) return null
        val q = foldedQuery?.takeIf { it.isNotEmpty() }
        val f = TextFolder.foldMapped(body)
        val first = q?.let { f.folded.indexOf(it) } ?: -1
        var start = 0
        if (first >= 0) {
            val o = f.toOriginal(first, first + q!!.length).first
            if (o > SNIPPET_LEAD) {
                start = o - SNIPPET_LEAD
                while (start > 0 && isContinuation(body, start)) start--
                val ws = (start until o).firstOrNull { body[it].isWhitespace() }
                if (ws != null) start = ws + 1
            }
        }
        var end = minOf(body.length, start + SNIPPET_MAX)
        while (end < body.length && isContinuation(body, end)) end++
        val prefix = if (start > 0) ELLIPSIS else ""
        val text =
            prefix +
                body.substring(start, end).map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }
                    .joinToString("")
        val shift = prefix.length - start
        val ranges =
            if (q == null) {
                emptyList()
            } else {
                ranges(body, q).mapNotNull { r ->
                    val lo = maxOf(r.first, start)
                    val hi = minOf(r.last, end - 1)
                    if (lo <= hi) (lo + shift)..(hi + shift) else null
                }
            }
        return Snippet(text, ranges)
    }

    private fun isContinuation(s: String, i: Int): Boolean {
        val c = s[i]
        if (Character.isLowSurrogate(c)) return true
        return when (Character.getType(c).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val out = mutableListOf<IntRange>()
        val last = ranges.sortedBy { it.first }.let { sorted ->
            var c = sorted[0]
            for (r in sorted.drop(1)) {
                if (r.first <= c.last + 1) {
                    c = c.first..maxOf(c.last, r.last)
                } else {
                    out += c
                    c = r
                }
            }
            c
        }
        out += last
        return out
    }
}
