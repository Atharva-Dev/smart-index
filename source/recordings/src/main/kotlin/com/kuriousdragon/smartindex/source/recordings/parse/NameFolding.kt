package com.kuriousdragon.smartindex.source.recordings.parse

import java.text.Normalizer
import java.util.Locale

/** Folds names for comparison only (never for display). */
object NameFolding {
    private val SEPARATORS = Regex("""[\s_\\/:*?"<>|]+""")

    /** NFC → lowercase(Locale.ROOT) → each run of whitespace/_/filesystem-illegal chars → one space → trim; blank → null. */
    fun fold(s: String?): String? {
        if (s == null) return null
        val f = Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(SEPARATORS, " ").trim()
        return f.ifEmpty { null }
    }
}
