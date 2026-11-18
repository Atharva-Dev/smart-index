package com.kuriousdragon.smartindex.tools.seeder.testdata

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import org.junit.Test

class TestdataManifestTest {
    private val root = File(requireNotNull(System.getProperty("sidx.testdata")))

    private data class Row(val path: String, val bytes: Long, val sha256: String)

    private fun rows(): List<Row> {
        val lines = File(root, "MANIFEST.tsv").readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
        assertThat(lines.first()).isEqualTo("path\tbytes\tsha256\texpected_type\tpurpose")
        return lines.drop(1).map {
            val c = it.split('\t')
            assertThat(c).hasSize(5)
            Row(c[0], c[1].toLong(), c[2])
        }
    }

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `every manifest row exists with the listed size and SHA-256`() {
        val rows = rows()
        assertThat(rows).isNotEmpty()
        for (r in rows) {
            val f = File(root, r.path)
            assertThat(f.isFile).isTrue()
            assertThat(f.length()).isEqualTo(r.bytes)
            assertThat(sha256(f)).isEqualTo(r.sha256)
        }
    }

    @Test
    fun `every committed file in the generated folders is listed`() {
        val listed = rows().map { it.path }.toSet()
        val onDisk = listOf("docs", "other", "bad", "us002", "i18n").flatMap { d ->
            File(root, d).walkTopDown().filter {
                it.isFile
            }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        }
        assertThat(onDisk).containsExactlyElementsIn(listed)
    }

    @Test
    fun `scan_0042 pdf contains the body phrase`() {
        val text = File(root, "us002/scan_0042.pdf").readBytes().toString(Charsets.ISO_8859_1)
        assertThat(text).contains("booking reference")
    }

    @Test
    fun `hotel booking pdf body does not contain the phrase`() {
        val text = File(root, "us002/Hotel booking.pdf").readBytes().toString(Charsets.ISO_8859_1)
        assertThat(text).doesNotContain("booking")
    }

    @Test
    fun `zero-byte pdf is empty`() {
        assertThat(File(root, "bad/zero-byte.pdf").length()).isEqualTo(0L)
    }

    @Test
    fun `photo-renamed pdf starts with the PNG signature`() {
        val head = File(root, "bad/photo-renamed.pdf").readBytes().copyOf(8)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertThat(head).isEqualTo(png)
    }
}
