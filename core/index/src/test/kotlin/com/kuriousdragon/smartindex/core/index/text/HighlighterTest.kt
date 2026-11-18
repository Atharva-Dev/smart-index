package com.kuriousdragon.smartindex.core.index.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlighterTest {
    @Test
    fun `finds every occurrence in original offsets`() {
        assertThat(Highlighter.ranges("Sea view, sea VIEW", "sea")).containsExactly(0..2, 10..12).inOrder()
        assertThat(Highlighter.ranges("hotel", "x")).isEmpty()
        assertThat(Highlighter.ranges(null, "x")).isEmpty()
        assertThat(Highlighter.ranges("abc", "")).isEmpty()
    }

    @Test
    fun `non-overlapping matches merge when adjacent`() {
        assertThat(Highlighter.ranges("aaaa", "aa")).containsExactly(0..3)
    }

    @Test
    fun `ranges stay in original offsets for decomposed, Hangul and dotted I`() {
        assertThat(Highlighter.ranges("Zoë x", "zoë")).containsExactly(0..3)
        assertThat(Highlighter.ranges("a 한 b", "한")).containsExactly(2..4)
        assertThat(Highlighter.ranges("Ab İstanbul", "istanbul")).containsExactly(3..10)
    }

    @Test
    fun `number ranges map digits across separators`() {
        assertThat(Highlighter.numberRanges("+91 98765 43210", "9876543210")).containsExactly(4..14)
        assertThat(Highlighter.numberRanges("+91 98765 43210", "98765")).containsExactly(4..8)
        assertThat(Highlighter.numberRanges("+91 98765 43210", "abc")).isEmpty()
        assertThat(Highlighter.numberRanges(null, "123")).isEmpty()
    }

    @Test
    fun `short body gives the whole text with ranges`() {
        val s = Highlighter.snippet("Hotel Sea View", "sea view")!!
        assertThat(s.text).isEqualTo("Hotel Sea View")
        assertThat(s.ranges).containsExactly(6..13)
    }

    @Test
    fun `snippet starts at a word boundary with an ellipsis and shifted ranges`() {
        val body = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma"
        val s = Highlighter.snippet(body, "kappa")!!
        val kappa = body.indexOf("kappa")
        val start = s.text.substring(1)
        assertThat(s.text).startsWith("…")
        assertThat(body).contains(start)
        assertThat(body.indexOf(start)).isAtLeast(kappa - Highlighter.SNIPPET_LEAD)
        assertThat(body[body.indexOf(start) - 1]).isEqualTo(' ')
        val r = s.ranges.single()
        assertThat(s.text.substring(r.first, r.last + 1)).isEqualTo("kappa")
    }

    @Test
    fun `match after 160 chars still shows the match`() {
        val body = "x".repeat(100) + " " + "y".repeat(100) + " needle tail"
        val s = Highlighter.snippet(body, "needle")!!
        assertThat(s.text).startsWith("…")
        assertThat(s.text.length).isAtMost(Highlighter.SNIPPET_MAX + 1)
        val r = s.ranges.single()
        assertThat(s.text.substring(r.first, r.last + 1)).isEqualTo("needle")
    }

    @Test
    fun `long text is capped at the maximum`() {
        val s = Highlighter.snippet("word ".repeat(100), "word")!!
        assertThat(s.text.length).isEqualTo(Highlighter.SNIPPET_MAX)
        assertThat(s.ranges.last().last).isAtMost(Highlighter.SNIPPET_MAX - 1)
    }

    @Test
    fun `newlines and tabs become spaces`() {
        val s = Highlighter.snippet("one\ntwo\r\nthree\tfour", "two")!!
        assertThat(s.text).isEqualTo("one two  three four")
        assertThat(s.ranges).containsExactly(4..6)
    }

    @Test
    fun `no body match or blank query starts at the beginning`() {
        val body = "z".repeat(200)
        val a = Highlighter.snippet(body, "needle")!!
        assertThat(a.text).isEqualTo("z".repeat(Highlighter.SNIPPET_MAX))
        assertThat(a.ranges).isEmpty()
        assertThat(Highlighter.snippet(body, "")!!.text).isEqualTo(a.text)
        assertThat(Highlighter.snippet(body, null)!!.ranges).isEmpty()
    }

    @Test
    fun `null or blank body gives null`() {
        assertThat(Highlighter.snippet(null, "a")).isNull()
        assertThat(Highlighter.snippet("  \n", "a")).isNull()
    }

    @Test
    fun `Hindi match range is correct`() {
        val s = Highlighter.snippet("आपका होटल बुक हो गया", "होटल")!!
        val r = s.ranges.single()
        assertThat(s.text.substring(r.first, r.last + 1)).isEqualTo("होटल")
    }
}
