package com.kuriousdragon.smartindex.tools.seeder.gen

/**
 * Item counts of a profile (LLD §4.1). They are the BULK counts of every profile, including `edge`; planted items
 * (§4.4), the busy-day extras (§4.5), the tricky recording cases (§4.6) and the edge cases (§4.8) come in addition.
 * [recordings] is the number of bulk recording files, [recordingsLinked] those that must link to a call.
 */
data class ProfileCounts(
    val contacts: Int,
    val calls: Int,
    val sms: Int,
    val mms: Int,
    val photos: Int,
    val videos: Int,
    val documents: Int,
    val others: Int,
    val recordings: Int,
    val recordingsLinked: Int,
)

/** Seed profiles (LLD §3.4, §4.1). */
enum class Profile(val alias: String?, val counts: ProfileCounts) {
    SMALL("smoke", ProfileCounts(50, 100, 200, 20, 60, 10, 40, 20, 15, 11)),
    NFR002("full", ProfileCounts(1_000, 20_000, 95_000, 5_000, 45_000, 5_000, 20_000, 10_000, 300, 270)),
    EDGE(null, ProfileCounts(30, 60, 80, 20, 20, 5, 30, 20, 25, 15)),
    ;

    companion object {
        /** Accepts the profile name or its alias, case-insensitive; null when unknown. */
        fun parse(value: String): Profile? {
            val v = value.trim().lowercase()
            return entries.firstOrNull { it.name.lowercase() == v || it.alias == v }
        }
    }
}
