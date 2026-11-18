package com.kuriousdragon.smartindex.benchmark

/** One parameter set: query text, type tab test tag, optional date preset test tag (null = any date). */
data class Q(val text: String, val tabTag: String, val presetTag: String? = null) {
    fun toParams(): Array<Any?> = arrayOf(text, tabTag, presetTag ?: Tags.PRESET_ANY)
}

/** Query lists of LLD — Test & benchmark harness §3.6, verbatim. */
object Queries {
    val S1 = listOf(
        Q("ra", Tags.TAB_ALL),
        Q("qz", Tags.TAB_ALL),
        Q("x", Tags.TAB_ALL),
        Q("र", Tags.TAB_ALL),
        Q("ra", Tags.TAB_MESSAGE),
        Q("qz", Tags.TAB_MESSAGE),
        Q("ra", Tags.TAB_ALL, Tags.PRESET_LAST_30_DAYS),
        Q("qz", Tags.TAB_ALL, Tags.PRESET_LAST_30_DAYS),
    )

    val S2 = listOf(
        Q("the", Tags.TAB_ALL),
        Q("payment", Tags.TAB_ALL),
        Q("होटल", Tags.TAB_ALL),
        Q("payment", Tags.TAB_MESSAGE),
    )

    val S3 = listOf(
        Q("sea view", Tags.TAB_ALL),
        Q("kumquat", Tags.TAB_ALL),
        Q("98765", Tags.TAB_ALL),
        Q("ölaf", Tags.TAB_ALL),
    )

    val S6 = listOf(
        Q("hotel", Tags.TAB_ALL, Tags.PRESET_LAST_30_DAYS),
        Q("hotel", Tags.TAB_CALL),
        Q("", Tags.TAB_ALL, Tags.PRESET_LAST_7_DAYS),
        Q("payment", Tags.TAB_ALL, Tags.PRESET_LAST_7_DAYS),
    )

    fun params(list: List<Q>): List<Array<Any?>> = list.map { it.toParams() }
}
