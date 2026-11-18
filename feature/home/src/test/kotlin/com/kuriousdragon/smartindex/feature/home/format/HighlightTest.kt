package com.kuriousdragon.smartindex.feature.home.format

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlightTest {
    @Test
    fun `clamp clips to the text and drops ranges outside it`() {
        val out = Highlight.clamp(listOf(-3..2, 8..20, 12..15, 5..4), 10)
        assertThat(out).containsExactly(0..2, 8..9).inOrder()
    }

    @Test
    fun `merge joins overlapping ranges and keeps touching ones apart`() {
        val out = Highlight.merge(listOf(5..8, 0..2, 1..3, 4..4, 7..12))
        assertThat(out).containsExactly(0..3, 4..4, 5..12).inOrder()
    }

    @Test
    fun `merge of nothing is empty`() {
        assertThat(Highlight.merge(emptyList())).isEmpty()
    }

    @Test
    fun `shift moves ranges and clips them to the new length`() {
        assertThat(Highlight.shift(listOf(0..2, 4..6), 3, 8)).containsExactly(3..5, 7..7).inOrder()
        assertThat(Highlight.shift(listOf(0..2), 8, 8)).isEmpty()
    }

    @Test
    fun `folder matches are shifted onto the last segment`() {
        // "DCIM/Camera": the segment "Camera" starts at 5, so a match on "Cam" (5..7) becomes 0..2.
        val folder = "DCIM/Camera"
        val segment = folder.substringAfterLast('/')
        val shifted = Highlight.shift(listOf(5..7, 0..3), -(folder.length - segment.length), segment.length)
        assertThat(shifted).containsExactly(0..2)
    }

    @Test
    fun `surrogate pairs are indexed by UTF-16 code units`() {
        val text = "a😀b" // the emoji takes indexes 1 and 2
        assertThat(Highlight.clamp(listOf(1..2), text.length)).containsExactly(1..2)
        assertThat(Highlight.shift(listOf(1..2), 1, text.length + 1)).containsExactly(2..3)
    }

    @Test
    fun `toAnnotated styles every merged range with an exclusive end`() {
        val style = SpanStyle(fontWeight = FontWeight.Bold)
        val annotated = HighlightedText("hello world", listOf(0..1, 1..2, 6..99)).toAnnotated(style)
        assertThat(annotated.text).isEqualTo("hello world")
        assertThat(annotated.spanStyles.map { it.start to it.end }).containsExactly(0 to 3, 6 to 11).inOrder()
        assertThat(annotated.spanStyles.all { it.item == style }).isTrue()
    }
}
