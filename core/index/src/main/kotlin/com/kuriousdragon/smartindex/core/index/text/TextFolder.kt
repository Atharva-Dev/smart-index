package com.kuriousdragon.smartindex.core.index.text

import java.text.Normalizer
import java.util.Locale

/** The one folding function for index text and queries (LLD §4.4, FR-008 / NFR-009). */
object TextFolder {
    const val FIELD_SEPARATOR = '\u0001'

    private const val DOTTED_CAPITAL_I = 0x0130
    private const val ZWJ = 0x200D
    private const val JAMO_FIRST = 0x1160
    private const val JAMO_LAST = 0x11FF

    fun fold(s: String): String = foldMapped(s).folded

    fun codePointLength(s: String): Int = s.codePointCount(0, s.length)

    fun foldMapped(s: String): FoldedText {
        if (s.all { it.code < 0x80 }) {
            val n = s.length
            return FoldedText(s, s.asciiLowercase(), IntArray(n) { it }, IntArray(n) { it + 1 })
        }
        val out = StringBuilder(s.length)
        var starts = IntArray(s.length)
        var ends = IntArray(s.length)
        var i = 0
        while (i < s.length) {
            val chunkStart = i
            i += Character.charCount(s.codePointAt(i))
            while (i < s.length && extendsChunk(s.codePointAt(i))) {
                i += Character.charCount(s.codePointAt(i))
            }
            val before = out.length
            out.append(foldChunk(s.substring(chunkStart, i)))
            if (out.length > starts.size) {
                starts = starts.copyOf(out.length * 2)
                ends = ends.copyOf(out.length * 2)
            }
            for (k in before until out.length) {
                starts[k] = chunkStart
                ends[k] = i
            }
        }
        return FoldedText(s, out.toString(), starts.copyOf(out.length), ends.copyOf(out.length))
    }

    private fun extendsChunk(cp: Int): Boolean =
        when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> cp in JAMO_FIRST..JAMO_LAST || cp == ZWJ
        }

    private fun foldChunk(chunk: String): String {
        val nfc = Normalizer.normalize(chunk, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        var i = 0
        while (i < nfc.length) {
            val cp = nfc.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == DOTTED_CAPITAL_I) {
                sb.append('i')
            } else {
                sb.append(String(Character.toChars(cp)).lowercase(Locale.ROOT))
            }
        }
        return sb.toString().replace('ς', 'σ')
    }

    private fun String.asciiLowercase(): String {
        val c = CharArray(length) {
            val ch = this[it]
            if (ch in 'A'..'Z') ch + 32 else ch
        }
        return String(c)
    }
}
