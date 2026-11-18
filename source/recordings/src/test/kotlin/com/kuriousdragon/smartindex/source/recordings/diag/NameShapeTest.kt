package com.kuriousdragon.smartindex.source.recordings.diag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NameShapeTest {
    @Test
    fun kdocExamples() {
        assertThat(NameShape.mask("Call recording Rahul_241103_094215.m4a")).isEqualTo("a a a_999999_999999.m4a")
        assertThat(NameShape.mask("Call recording +91 98765 43210_241103_101005.m4a"))
            .isEqualTo("a a +99 99999 99999_999999_999999.m4a")
    }

    @Test
    fun devanagariWithVowelSignsCollapsesToSingleAPerWord() {
        val name = "राहुल शर्मा_241103.m4a"
        val shape = NameShape.mask(name)
        assertThat(shape).isEqualTo("a a_999999.m4a")
        name.filter { it != '_' && it != ' ' && it != '.' }.take(8).forEach { assertThat(shape).doesNotContain("$it") }
    }

    @Test
    fun emojiBecomesA() {
        assertThat(NameShape.mask("Call 😀😀 Bob.mp3")).isEqualTo("a a a.mp3")
    }

    @Test
    fun arabicIndicDigitsBecomeNine() {
        assertThat(NameShape.mask("١٢٣_x.m4a")).isEqualTo("999_a.m4a")
    }

    @Test
    fun extensionKeptLowerCased() {
        assertThat(NameShape.mask("Rec_1.M4A")).isEqualTo("a_9.m4a")
    }

    @Test
    fun longExtensionIsMasked() {
        assertThat(NameShape.mask("Rec.abcdefg")).isEqualTo("a.a")
    }

    @Test
    fun cutsTo120Characters() {
        val shape = NameShape.mask("x".repeat(500) + ".m4a")
        assertThat(shape).isEqualTo("a")
        assertThat(NameShape.mask("1".repeat(500))).hasLength(120)
    }

    @Test
    fun noLetterOtherThanAOutsideExtension() {
        val inputs =
            listOf(
                "Call recording Rahul_241103_094215.m4a",
                "Voice 001 नमस्ते 😀 Zed & Co.mp3",
                "John Smith (work) [x] +1 555.m4a",
            )
        for (input in inputs) {
            val shape = NameShape.mask(input)
            val dot = shape.lastIndexOf('.')
            val body = if (dot >= 0) shape.substring(0, dot) else shape
            assertThat(body.filter { it.isLetter() && it != 'a' }).isEmpty()
        }
    }
}
