package com.kuriousdragon.smartindex.core.index.text

/** Composes the folded search key stored in `short_text.skey` (LLD §4.5). */
object SkeyBuilder {
    fun build(
        title: String,
        folder: String?,
        ext: String?,
        numberRaw: String?,
        numberDigits: String?,
        contactName: String?,
        body: String?,
        extraNames: List<String> = emptyList(),
    ): String {
        val digits = numberDigits?.takeIf { it != numberRaw }
        val extras = extraNames.filter { it.isNotBlank() }.map(TextFolder::fold).distinct().sorted()
        val head = listOf(title, folder, ext, numberRaw, digits, contactName)
            .filter { !it.isNullOrBlank() }
            .map { TextFolder.fold(it!!) }
        val tail = listOf(body).filter { !it.isNullOrBlank() }.map { TextFolder.fold(it!!) }
        return (head + extras + tail).joinToString(TextFolder.FIELD_SEPARATOR.toString())
    }
}
