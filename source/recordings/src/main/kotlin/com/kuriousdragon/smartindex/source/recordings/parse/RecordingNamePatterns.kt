package com.kuriousdragon.smartindex.source.recordings.parse

/**
 * Samsung call-recording file-name formats (LLD — Call-recording matcher §4.2). DATA, not code:
 * a new format = one row here + one line in testdata/recordings/sample-names.tsv.
 * Time in every format is device-local wall-clock time of recording start.
 */
object RecordingNamePatterns {

    /** Optional duplicate suffix " (1)" and the audio extension; shared by every row. */
    private const val SUFFIX = """(?<copy> ?\(\d{1,3}\))?\.(?<ext>m4a|amr|3gp|3ga|aac|mp3|wav|ogg|opus)"""
    private val OPTIONS = setOf(RegexOption.IGNORE_CASE)

    val TABLE: List<RecordingNamePattern> = listOf(
        RecordingNamePattern(
            id = "who_number_yyMMdd_HHmmss",
            regex = Regex(
                """(?<who>.+?)_(?<number>\+?\d[\d \-()]{5,20}\d)_(?<date>\d{6})_(?<time>\d{6})$SUFFIX""",
                OPTIONS,
            ),
            dateDigits = 6,
            example = "Call recording Rahul_+919876543210_241103_094215.m4a",
        ),
        RecordingNamePattern(
            id = "who_yyyyMMdd_HHmmss",
            regex = Regex("""(?<who>.*?)_(?<date>\d{8})_(?<time>\d{6})$SUFFIX""", OPTIONS),
            dateDigits = 8,
            example = "Call recording Rahul_20241103_094215.m4a",
        ),
        RecordingNamePattern(
            id = "who_yyMMdd_HHmmss",
            regex = Regex("""(?<who>.*?)_(?<date>\d{6})_(?<time>\d{6})$SUFFIX""", OPTIONS),
            dateDigits = 6,
            example = "Call recording Rahul_241103_094215.m4a",
        ),
    )

    /**
     * Localised "Call recording" prefixes, stripped (case-insensitive) from the start of `who` when followed by
     * whitespace, '_' or the end. Only the English one is backed by public reports; the others are best-effort
     * and are confirmed or corrected by the R1 field capture (§6.5). An unknown prefix is harmless: it stays in
     * the name and identity becomes PARTIAL instead of MATCH (§4.4).
     */
    val KNOWN_PREFIXES: List<String> = listOf(
        "Call recording",
        "Anrufaufnahme",
        "Grabación de llamada",
        "Enregistrement d'appel",
        "Registrazione chiamata",
        "Gravação de chamada",
        "कॉल रिकॉर्डिंग",
    )

    /** Folded (NameFolding.fold) labels that mean "no counterpart". */
    val PLACEHOLDERS: Set<String> = setOf(
        "unknown", "unknown number", "private", "private number", "no caller id", "anonymous",
        "conference", "conference call", "voicemail", "emergency number",
    )

    /** `who` that is only a number (≥ 3 digits are checked separately). */
    val NUMBER_ONLY = Regex("""\+?[\d \-()]+""")

    /** "Rahul (+91 98765 43210)" → group 1 = number. */
    val TRAILING_PAREN_NUMBER = Regex("""\s*\((\+?\d[\d \-]{5,}\d)\)$""")

    /** "Anruf +49 151 2345678" → group 1 = number. */
    val TRAILING_NUMBER = Regex("""\s(\+?\d[\d \-]{5,}\d)$""")
}
