package com.kuriousdragon.smartindex.source.recordings.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameFoldingTest {
    @Test
    fun `folds case`() {
        assertThat(NameFolding.fold("RaHuL")).isEqualTo("rahul")
    }

    @Test
    fun `composed and decomposed e acute fold equal`() {
        val decomposed = "e\u0301"
        val composed = "\u00e9"
        assertThat(decomposed).isNotEqualTo(composed)
        assertThat(NameFolding.fold(decomposed)).isEqualTo(NameFolding.fold(composed))
        assertThat(NameFolding.fold(decomposed)).isEqualTo(composed)
    }

    @Test
    fun `separator runs collapse to one space`() {
        assertThat(NameFolding.fold("a_/:b")).isEqualTo("a b")
        assertThat(NameFolding.fold("a  \t b")).isEqualTo("a b")
        assertThat(NameFolding.fold("A__B")).isEqualTo("a b")
    }

    @Test
    fun `trims`() {
        assertThat(NameFolding.fold("  _Rahul_  ")).isEqualTo("rahul")
    }

    @Test
    fun `blank and null fold to null`() {
        assertThat(NameFolding.fold(null)).isNull()
        assertThat(NameFolding.fold("")).isNull()
        assertThat(NameFolding.fold(" _ / ")).isNull()
    }

    @Test
    fun `devanagari is unchanged`() {
        assertThat(NameFolding.fold("राहुल")).isEqualTo("राहुल")
    }
}
