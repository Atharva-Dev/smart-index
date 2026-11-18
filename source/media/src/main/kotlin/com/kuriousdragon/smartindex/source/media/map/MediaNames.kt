package com.kuriousdragon.smartindex.source.media.map

/** Name, folder and extension derivation (LLD §4.4). */
internal object MediaNames {
    /** Title used when DISPLAY_NAME is null or blank. */
    const val UNNAMED: String = "Unnamed file"

    /** RELATIVE_PATH with '\' → '/', no leading '/', and exactly one trailing '/' ("" for root or null). */
    fun normalisedPath(relativePath: String?): String {
        val p = relativePath?.replace('\\', '/')?.trim()?.trim('/') ?: return ""
        return if (p.isEmpty()) "" else "$p/"
    }

    fun title(displayName: String?): String = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: UNNAMED

    /** Lower-case extension after the last '.', 1–10 chars without whitespace; null otherwise (".bashrc" → null). */
    fun extension(displayName: String?): String? {
        val name = displayName?.trim() ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        val ext = name.substring(dot + 1)
        if (ext.length > 10 || ext.any { it.isWhitespace() }) return null
        return ext.lowercase()
    }

    /**
     * Display folder (Design Spec §6.5): the last segment of RELATIVE_PATH ("DCIM/Camera/" → "Camera");
     * for a recording the whole path without the trailing '/' ("Recordings/Call/" → "Recordings/Call");
     * null for files in a volume's root.
     */
    fun folder(relativePath: String?, isRecording: Boolean): String? {
        val p = normalisedPath(relativePath).trimEnd('/')
        if (p.isEmpty()) return null
        return if (isRecording) p else p.substringAfterLast('/')
    }
}
