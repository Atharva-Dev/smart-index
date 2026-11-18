package com.kuriousdragon.smartindex.feature.home.format

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** A field's text plus the inclusive UTF-16 match ranges into that same text. Holds ranges, never colours. */
@Immutable
data class HighlightedText(val text: String, val ranges: List<IntRange> = emptyList())

/** Pure range helpers; every range is inclusive and indexes UTF-16 code units of the original field text. */
object Highlight {
    /** Clips ranges to `0 until length` and drops the ones that end up empty. */
    fun clamp(ranges: List<IntRange>, length: Int): List<IntRange> = ranges.mapNotNull { r ->
        val start = maxOf(r.first, 0)
        val end = minOf(r.last, length - 1)
        if (start <= end) start..end else null
    }

    /** Sorts and merges overlapping ranges (touching ranges stay separate). */
    fun merge(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.filter { !it.isEmpty() }.sortedWith(compareBy({ it.first }, { it.last }))
        val out = ArrayList<IntRange>(sorted.size)
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last) {
                if (r.last > last.last) out[out.lastIndex] = last.first..r.last
            } else {
                out += r
            }
        }
        return out
    }

    /**
     * Moves every range by [offset] (positive moves right, negative left) and clips the result to a
     * string of [length]. Folder matches use `-(folder.length - segment.length)`; EXT matches mapped onto
     * the title tail use `+(title.length - ext.length)`.
     */
    fun shift(ranges: List<IntRange>, offset: Int, length: Int): List<IntRange> =
        clamp(ranges.map { (it.first + offset)..(it.last + offset) }, length)

    /** Clamps, merges and styles [ranges] of [text]. */
    fun annotate(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
        append(text)
        for (r in merge(clamp(ranges, text.length))) addStyle(style, r.first, r.last + 1)
    }
}

/** Composable-side rendering of a [HighlightedText] (inclusive ranges become `addStyle(style, start, end + 1)`). */
fun HighlightedText.toAnnotated(style: SpanStyle): AnnotatedString = Highlight.annotate(text, ranges, style)
