package com.kuriousdragon.smartindex.source.media.map

/** Fixed classification tables (LLD — Media & file indexer §4.2). Data, not logic: a new format is one line. */
internal object FileTypes {
    /** MIME types that make a file a DOCUMENT (compared lower-case, parameters after ';' dropped). */
    val DOCUMENT_MIMES: Set<String> = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "application/vnd.ms-word.document.macroenabled.12",
        "application/vnd.ms-word.template.macroenabled.12",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.ms-excel.sheet.macroenabled.12",
        "application/vnd.ms-excel.template.macroenabled.12",
        "application/vnd.ms-excel.sheet.binary.macroenabled.12",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "application/vnd.openxmlformats-officedocument.presentationml.template",
        "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "text/plain",
        "text/rtf",
        "application/rtf",
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
        "text/markdown",
    )

    /** Extensions (lower-case, no dot) that make a file a DOCUMENT even when its MIME type is generic. */
    val DOCUMENT_EXTS: Set<String> = setOf(
        "pdf",
        "doc", "docx", "dot", "dotx", "docm", "dotm",
        "xls", "xlsx", "xlt", "xltx", "xlsm", "xltm", "xlsb",
        "ppt", "pptx", "pps", "ppsx", "pot", "potx", "pptm", "ppsm",
        "odt", "ods", "odp",
        "txt", "rtf", "csv", "tsv", "md",
    )

    /** RELATIVE_PATH prefixes (case-insensitive, normalised to end with '/') of Samsung call recordings. */
    val RECORDING_PATH_PREFIXES: List<String> = listOf("Recordings/Call/", "Call/")

    /** RELATIVE_PATH prefixes (case-insensitive) of app-private storage, never indexed. */
    val APP_PRIVATE_PREFIXES: List<String> = listOf("Android/data/", "Android/obb/")
}
