package com.kuriousdragon.smartindex.core.index.text

/**
 * Folded text plus the offset map back to the original string (LLD §4.4).
 * `origStart[i]` / `origEnd[i]` are the original bounds of the chunk that produced folded char `i`.
 */
class FoldedText(
    val original: String,
    val folded: String,
    private val origStart: IntArray,
    private val origEnd: IntArray,
) {
    /** Original range for folded `[from, toExclusive)`; widens to whole chunks. */
    fun toOriginal(
        from: Int,
        toExclusive: Int,
    ): IntRange = origStart[from]..origEnd[toExclusive - 1] - 1
}
