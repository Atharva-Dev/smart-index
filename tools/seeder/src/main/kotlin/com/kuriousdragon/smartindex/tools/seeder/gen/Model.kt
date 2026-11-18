package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Plan model (LLD §4.2). Pure data; no Android types. Keys of planted/extra items start at [PLANTED_KEY_BASE]. */
const val PLANTED_KEY_BASE = 1_000_000

/** Marker phone account of every seeded call (wipe marker). */
const val PHONE_ACCOUNT_ID = "sidx-seeder"

/** Prefix of every seeded contact's SOURCE_ID. */
const val SOURCE_ID_PREFIX = "sidx-seeder:"

/** [numbers] as stored (mixed formats). */
data class SeedContact(val key: Int, val displayName: String, val numbers: List<String>) {
    val sourceId: String get() = "$SOURCE_ID_PREFIX$key"
}

/** [type] is `CallLog.Calls.TYPE` 1/2/3/5/6/7. */
data class SeedCall(
    val key: Int,
    val number: String?,
    val presentation: Int,
    val type: Int,
    val startMs: Long,
    val durationSec: Int,
) {
    val phoneAccountId: String get() = PHONE_ACCOUNT_ID
}

data class SeedSms(
    val key: Int,
    val address: String,
    val body: String,
    val type: Int,
    val dateMs: Long,
    val threadKey: String,
)

/** [addresses] [0] = counterpart; size >= 2 = group. [box] 1 = inbox, 2 = sent. */
data class SeedMms(
    val key: Int,
    val box: Int,
    val dateMs: Long,
    val addresses: List<String>,
    val subject: String?,
    val texts: List<String>,
    val imageParts: Int,
)

enum class MediaKind { PHOTO, VIDEO, DOCUMENT, OTHER, RECORDING }

sealed interface Payload {
    data class Jpeg(val template: Int) : Payload

    data class Mp4(val template: Int) : Payload

    data object Amr : Payload

    data object M4a : Payload

    data class Doc(val ext: String, val text: String?) : Payload
}

/** [relativePath] is a MediaStore RELATIVE_PATH with trailing slash ("DCIM/Camera/"). */
data class SeedFile(
    val key: Int,
    val kind: MediaKind,
    val relativePath: String,
    val displayName: String,
    val mime: String,
    val logicalSize: Long,
    val takenMs: Long?,
    val modifiedMs: Long,
    val durationMs: Long?,
    val payload: Payload,
)

/**
 * One planned recording file. [callKey] is the call it is expected to link to ([expectLinked]) or, for the N cases,
 * the nearby call it must NOT link to. [case] is "BULK", "E1".."E9", "PRIVATE", "N1".."N5".
 * The remaining fields describe what the name must parse to (null = nothing / unparseable name).
 */
data class SeedRecording(
    val file: SeedFile,
    val callKey: Int?,
    val expectLinked: Boolean,
    val case: String,
    val patternId: String? = null,
    val expectName: String? = null,
    val expectNumber: String? = null,
    val localStart: LocalDateTime? = null,
)

data class PlantedItem(val id: String, val where: String)

data class SeedPlan(
    val profile: Profile,
    val seed: Long,
    val anchor: LocalDateTime,
    val zone: ZoneId,
    val contacts: List<SeedContact>,
    val calls: List<SeedCall>,
    val sms: List<SeedSms>,
    val mms: List<SeedMms>,
    val files: List<SeedFile>,
    val recordings: List<SeedRecording>,
    val planted: List<PlantedItem>,
    val busyDay: LocalDate,
    val hotelDay: LocalDate,
)
