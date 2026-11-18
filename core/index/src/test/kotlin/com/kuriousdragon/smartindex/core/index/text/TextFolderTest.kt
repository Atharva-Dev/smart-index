package com.kuriousdragon.smartindex.core.index.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextFolderTest {
    @Test
    fun `ascii fast path lowercases and maps to itself`() {
        val f = TextFolder.foldMapped("Hotel Sea View")
        assertThat(f.folded).isEqualTo("hotel sea view")
        assertThat(f.toOriginal(2, 5)).isEqualTo(2..4)
        assertThat(TextFolder.fold("")).isEqualTo("")
    }

    @Test
    fun `Olaf with umlaut folds to lowercase`() {
        assertThat(TextFolder.fold("Ölaf")).isEqualTo("ölaf")
    }

    @Test
    fun `decomposed Zoe composes to length 3 and maps the mark chunk`() {
        val f = TextFolder.foldMapped("Zoë")
        assertThat(f.folded).isEqualTo("zoë")
        assertThat(f.folded).hasLength(3)
        assertThat(f.toOriginal(2, 3)).isEqualTo(2..3)
    }

    @Test
    fun `dotted capital I folds to plain i`() {
        assertThat(TextFolder.fold("İstanbul")).isEqualTo("istanbul")
    }

    @Test
    fun `greek final and medial sigma both fold to sigma`() {
        assertThat(TextFolder.fold("ΟΔΟΣ")).contains("σ")
        assertThat(TextFolder.fold("οδός")).contains("σ")
        assertThat(TextFolder.fold("ΟΔΟΣ")).isEqualTo(TextFolder.fold("οδος"))
    }

    @Test
    fun `hangul jamo compose into one syllable mapping three chars`() {
        val f = TextFolder.foldMapped("한")
        assertThat(f.folded).isEqualTo("한")
        assertThat(f.toOriginal(0, 1)).isEqualTo(0..2)
    }

    @Test
    fun `devanagari is unchanged`() {
        assertThat(TextFolder.fold("हो")).isEqualTo("हो")
        assertThat(TextFolder.fold("नमस्ते")).isEqualTo("नमस्ते")
    }

    @Test
    fun `emoji surrogate pair maps both utf16 units`() {
        val f = TextFolder.foldMapped("😀")
        assertThat(f.folded).isEqualTo("😀")
        assertThat(f.toOriginal(0, 1)).isEqualTo(0..1)
        assertThat(f.toOriginal(1, 2)).isEqualTo(0..1)
    }

    @Test
    fun `code point length counts a surrogate pair once`() {
        assertThat(TextFolder.codePointLength("a😀")).isEqualTo(2)
        assertThat(TextFolder.codePointLength("हो")).isEqualTo(2)
    }
}
