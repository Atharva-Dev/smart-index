package com.kuriousdragon.smartindex.core.index.text

/** FTS5 phrase escaping (LLD §4.6); always bound as a parameter (`MATCH ?`). */
object FtsPhrase {
    fun of(foldedQuery: String): String = "\"" + foldedQuery.replace("\"", "\"\"") + "\""
}
