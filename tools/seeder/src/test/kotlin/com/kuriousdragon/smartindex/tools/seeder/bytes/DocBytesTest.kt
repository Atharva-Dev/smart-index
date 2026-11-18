package com.kuriousdragon.smartindex.tools.seeder.bytes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocBytesTest {
    private fun h(ext: String, text: String? = null) = DocBytes.header(ext, text)

    @Test
    fun magicPerExtension() {
        assertThat(String(h("pdf"), 0, 5, Charsets.US_ASCII)).isEqualTo("%PDF-")
        for (e in listOf("docx", "xlsx", "pptx")) {
            assertThat(h(e).take(4)).containsExactly(0x50.toByte(), 0x4B.toByte(), 3.toByte(), 4.toByte()).inOrder()
        }
        for (e in listOf("doc", "xls", "ppt")) {
            assertThat(h(e).take(8).map { it.toInt() and 0xFF })
                .containsExactly(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1).inOrder()
        }
        assertThat(h("zip").take(4)).containsExactly(0x50.toByte(), 0x4B.toByte(), 5.toByte(), 6.toByte()).inOrder()
        assertThat(h("zip")).hasLength(22)
        assertThat(String(h("txt", "hello"), Charsets.UTF_8)).startsWith("hello")
        assertThat(h("xyz")).isEmpty()
    }

    @Test
    fun pdfTextOnlyWherePlanned() {
        val booking = String(DocBytes.header("pdf", "Scan: booking reference ABC123"), Charsets.ISO_8859_1)
        assertThat(booking).contains("booking reference")
        val hotel = String(DocBytes.header("pdf", null), Charsets.ISO_8859_1)
        assertThat(hotel).doesNotContain("booking reference")
        assertThat(hotel).contains("Document")
    }

    @Test
    fun pdfXrefOffsetsPointAtObjects() {
        val s = String(h("pdf", "a (b) \\ c"), Charsets.ISO_8859_1)
        val xref = s.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertThat(s.substring(xref)).startsWith("xref")
        val entries = s.substring(xref).lines().filter { it.endsWith(" n ") }
        entries.forEachIndexed { i, line ->
            assertThat(s.substring(line.take(10).toInt())).startsWith("${i + 1} 0 obj")
        }
    }

    @Test
    fun docxIsAReadableZip() {
        val zip = java.util.zip.ZipInputStream(h("docx").inputStream())
        assertThat(zip.nextEntry.name).isEqualTo("[Content_Types].xml")
    }
}
