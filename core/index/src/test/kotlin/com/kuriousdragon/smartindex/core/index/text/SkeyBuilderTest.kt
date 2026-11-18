package com.kuriousdragon.smartindex.core.index.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkeyBuilderTest {
    private val sep = TextFolder.FIELD_SEPARATOR

    @Test
    fun `fields are in order, folded and separated`() {
        val s =
            SkeyBuilder.build(
                "+91 98765 43210",
                null,
                null,
                "+91 98765 43210",
                "919876543210",
                "Rahul",
                "Hotel Sea View",
            )
        assertThat(s).isEqualTo("+91 98765 43210$sep+91 98765 43210${sep}919876543210${sep}rahul${sep}hotel sea view")
    }

    @Test
    fun `file fields keep title folder ext order`() {
        assertThat(SkeyBuilder.build("Report.PDF", "Download", "PDF", null, null, null, null))
            .isEqualTo("report.pdf${sep}download${sep}pdf")
    }

    @Test
    fun `digits form is added only when non-null and different`() {
        assertThat(SkeyBuilder.build("t", null, null, "12345", "12345", null, null)).isEqualTo("t${sep}12345")
        assertThat(SkeyBuilder.build("t", null, null, "AX-HDFCBK", null, null, null)).isEqualTo("t${sep}ax-hdfcbk")
        assertThat(SkeyBuilder.build("t", null, null, "1 2", "12", null, null)).isEqualTo("t${sep}1 2${sep}12")
    }

    @Test
    fun `null and blank values are skipped`() {
        assertThat(SkeyBuilder.build("Title", "", "  ", null, null, null, "")).isEqualTo("title")
    }

    @Test
    fun `extra names follow the contact name sorted, folded and de-duplicated`() {
        val s =
            SkeyBuilder.build(
                "t",
                null,
                null,
                null,
                null,
                "Rahul",
                "body",
                extraNames = listOf("Sanjay", "Meera", "MEERA", " ", "Éva"),
            )
        val extras = listOf("Sanjay", "Meera", "Éva").map(TextFolder::fold).sorted()
        assertThat(s).isEqualTo((listOf("t", "rahul") + extras + "body").joinToString(sep.toString()))
    }

    @Test
    fun `no extra names gives the same key as before`() {
        assertThat(SkeyBuilder.build("t", null, null, null, null, "Rahul", "b", extraNames = emptyList()))
            .isEqualTo(SkeyBuilder.build("t", null, null, null, null, "Rahul", "b"))
    }
}
