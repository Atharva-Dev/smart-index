package com.kuriousdragon.smartindex.tools.seeder.bytes

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/** Header/magic bytes per document extension (LLD §4.7). The rest of a file is a sparse extension. Pure Kotlin. */
object DocBytes {
    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(),
        0xCF.toByte(),
        0x11,
        0xE0.toByte(),
        0xA1.toByte(),
        0xB1.toByte(),
        0x1A,
        0xE1.toByte(),
    )

    fun header(ext: String, text: String?): ByteArray = when (ext.lowercase()) {
        "pdf" -> pdf(text ?: "Document")
        "docx", "xlsx", "pptx" -> zip(listOf("[Content_Types].xml" to CONTENT_TYPES.toByteArray(Charsets.UTF_8)))
        "doc", "xls", "ppt" -> OLE2_MAGIC + ByteArray(OLE2_BLOCK - OLE2_MAGIC.size)
        "txt", "csv" -> (text ?: "Document").plus("\n").toByteArray(Charsets.UTF_8)
        "zip", "apk" -> zip(emptyList())
        else -> ByteArray(0)
    }

    private const val OLE2_BLOCK = 512
    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"

    /** A valid one-page PDF with uncompressed content; non-Latin-1 characters become '?'. */
    private fun pdf(text: String): ByteArray {
        val safe = buildString {
            for (c in text.take(MAX_PDF_TEXT)) {
                when {
                    c == '(' || c == ')' || c == '\\' -> append('\\').append(c)
                    c.code in 32..126 -> append(c)
                    else -> append('?')
                }
            }
        }
        val stream = "BT /F1 14 Tf 72 720 Td ($safe) Tj ET"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R " +
                "/Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${stream.length} >>\nstream\n$stream\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = ArrayList<Int>()
        objects.forEachIndexed { i, body ->
            offsets += sb.length
            sb.append("${i + 1} 0 obj\n$body\nendobj\n")
        }
        val xref = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) sb.append("%010d 00000 n \n".format(o))
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private const val MAX_PDF_TEXT = 2000

    /** Stored (uncompressed) zip; no entries = the 22-byte empty archive. */
    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        for ((name, data) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value
            val localOffset = out.size()
            out.le32(0x04034B50)
            out.le16(20).le16(0).le16(0).le16(0).le16(0x21) // version, flags, stored, time, date (1980-01-01)
            out.le32(crc).le32(data.size.toLong()).le32(data.size.toLong())
            out.le16(nameBytes.size).le16(0)
            out.write(nameBytes)
            out.write(data)
            central.le32(0x02014B50)
            central.le16(20).le16(20).le16(0).le16(0).le16(0).le16(0x21)
            central.le32(crc).le32(data.size.toLong()).le32(data.size.toLong())
            central.le16(nameBytes.size).le16(0).le16(0).le16(0).le16(0).le32(0).le32(localOffset.toLong())
            central.write(nameBytes)
        }
        val centralBytes = central.toByteArray()
        val centralOffset = out.size()
        out.write(centralBytes)
        out.le32(0x06054B50)
        out.le16(0).le16(0).le16(entries.size).le16(entries.size)
        out.le32(centralBytes.size.toLong()).le32(centralOffset.toLong()).le16(0)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.le16(v: Int) = apply {
        write(v)
        write(v ushr 8)
    }

    private fun ByteArrayOutputStream.le32(v: Int) = le32(v.toLong() and 0xFFFF_FFFFL)

    private fun ByteArrayOutputStream.le32(v: Long) = apply {
        for (k in 0 until 4) write((v ushr (8 * k)).toInt())
    }
}
