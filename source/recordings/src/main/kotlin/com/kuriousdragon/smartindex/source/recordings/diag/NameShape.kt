package com.kuriousdragon.smartindex.source.recordings.diag

object NameShape {
    private const val SAFE = "_-+().,'&#@[]"
    private const val MAX_LEN = 120
    private const val MAX_EXT = 5

    /**
     * Masks a file name so it reveals its structure but no content: every digit (any script) -> '9';
     * every run of other characters that are not whitespace or in [SAFE] (letters, marks, symbols, emoji) -> one 'a';
     * whitespace -> ' '; [SAFE] kept; a final extension of 1-5 ASCII letters/digits is kept lower-cased.
     * Input is cut to 120 characters before masking.
     * "Call recording Rahul_241103_094215.m4a" -> "a a a_999999_999999.m4a"
     * "Call recording +91 98765 43210_241103_101005.m4a" -> "a a +99 99999 99999_999999_999999.m4a"
     */
    fun mask(fileName: String): String {
        val cut = if (fileName.length > MAX_LEN) fileName.substring(0, MAX_LEN) else fileName
        val dot = cut.lastIndexOf('.')
        val extLen = cut.length - dot - 1
        val hasExt = dot >= 0 && extLen in 1..MAX_EXT && (dot + 1 until cut.length).all { cut[it].isAsciiAlnum() }
        val body = if (hasExt) cut.substring(0, dot) else cut
        val sb = StringBuilder()
        var inRun = false
        var i = 0
        while (i < body.length) {
            val cp = body.codePointAt(i)
            i += Character.charCount(cp)
            val ch = if (cp <= Char.MAX_VALUE.code) cp.toChar() else null
            when {
                Character.isDigit(cp) -> {
                    sb.append('9')
                    inRun = false
                }

                Character.isWhitespace(cp) -> {
                    sb.append(' ')
                    inRun = false
                }

                ch != null && SAFE.indexOf(ch) >= 0 -> {
                    sb.append(ch)
                    inRun = false
                }

                else -> {
                    if (!inRun) sb.append('a')
                    inRun = true
                }
            }
        }
        if (hasExt) sb.append('.').append(cut.substring(dot + 1).lowercase())
        return sb.toString()
    }

    private fun Char.isAsciiAlnum(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
