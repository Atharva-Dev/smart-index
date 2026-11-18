package com.kuriousdragon.smartindex.core.model

// Shared model (HLD §6.1). Owner: "LLD — App shell, build & privacy hardening" (SIDX-6).
// The declarations, names, order and defaults are the HLD §6.1 contract and must not change (additions only).
// `Group` (also listed in HLD §6.1) lives in Permissions.kt next to `GroupState` and `PermissionService`.

/** The kind of an indexed item; the result tabs of FR-010 minus "All". */
enum class ItemType { PHOTO, VIDEO, DOCUMENT, OTHER_FILE, CALL, RECORDING, MESSAGE }

/** The Android provider an item was read from; each source has exactly one writer (HLD §5.2). */
enum class Source { MEDIA_STORE, CALL_LOG, SMS, MMS }

/** Call direction (INCOMING … BLOCKED) or message direction (SENT, RECEIVED). */
enum class Direction { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, SENT, RECEIVED }

/** Which date of an item a timestamp is (HLD §5.1 `item_date.kind`). */
enum class DateKind { TAKEN, ADDED, MODIFIED, EVENT }

/** Whether a [ItemType.MESSAGE] item came from the SMS or the MMS provider. */
enum class MsgKind { SMS, MMS }

/** Identity of an item in its source: unique per ([source], [sourceId]). */
data class SourceKey(val source: Source, val sourceId: Long)

/**
 * What an indexer hands to the store (`IndexWriter.upsert`).
 *
 * @property primaryTs the item's main time in epoch ms (UTC); used for ordering text-search results.
 * @property dates every date the item is "on" (photos: TAKEN, fallback ADDED; files: ADDED and MODIFIED;
 *   calls/messages: EVENT).
 * @property body SMS/MMS text only; null for every file type (SIDX-5).
 * @property sourceVersion opaque change token (MediaStore generation or provider date); equal = unchanged.
 * @property otherNumbers raw numbers of the further participants of a group message, excluding [numberRaw]. The
 *   store normalises, de-duplicates and stores them; they are used for contact-name search only and are
 *   never displayed.
 */
data class ItemRecord(
    val key: SourceKey,
    val type: ItemType,
    val title: String,
    val primaryTs: Long,
    val dates: Map<DateKind, Long>,
    val folder: String? = null,
    val mime: String? = null,
    val ext: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val contentUri: String? = null,
    val numberRaw: String? = null,
    val direction: Direction? = null,
    val msgKind: MsgKind? = null,
    val threadId: Long? = null,
    val hasAttachment: Boolean = false,
    val body: String? = null,
    val sourceVersion: Long,
    val otherNumbers: List<String> = emptyList(),
)

/** Inclusive epoch-ms range, built from LocalDate(s) in the device time zone by the UI (FR-009). */
data class DateRange(val startMs: Long, val endMsInclusive: Long)

/** One search request: [text] is trimmed by the store; null [range] = any date; null [type] = all types. */
data class SearchQuery(val text: String, val range: DateRange?, val type: ItemType?)
