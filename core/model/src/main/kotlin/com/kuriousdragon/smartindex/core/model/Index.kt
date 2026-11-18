package com.kuriousdragon.smartindex.core.model

import androidx.collection.LongSet
import androidx.paging.PagingData
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Write side of the on-device index (HLD §6.2), implemented by `:core:index`.
 *
 * Callers: the source indexers (`:source:media`, `:source:telephony`), the recording matcher
 * (`:source:recordings`) and orchestration (`:sync`). The UI never writes.
 *
 * Rules for every function:
 * - Main-safe: the implementation switches to its own IO context.
 * - Each [Source] has exactly one writer (HLD §5.2). Never write another owner's source.
 * - Derived data (keys, dates, search text, FTS) is maintained by the store in the same
 *   transaction as the item; callers never see or write it.
 * - Failures (for example an `SQLiteException` for a full disk) are thrown after the failing
 *   transaction is rolled back, so the index is never half-written. `CancellationException`
 *   is always propagated.
 */
interface IndexWriter {

    /**
     * Inserts new items and updates existing ones, keyed by [ItemRecord.key]. Idempotent: the same
     * batch written twice leaves the index unchanged, and an item's id never changes on update.
     *
     * - Any size is accepted. The store commits in transactions of at most 500 records
     *   (the `IndexConfig` upsert batch); records of a committed transaction stay committed if a
     *   later one fails.
     * - [ItemRecord.sourceVersion] is an opaque equality token (for example a 64-bit hash): a record
     *   whose stored `sourceVersion` is **equal** is skipped. It is never compared with `<` / `>`.
     * - If the same key occurs more than once in [batch], the last record wins.
     * - [ItemRecord.body] is stored only for [ItemType.MESSAGE] and dropped for every other type.
     * - Numbers: the store computes `number_norm = PhoneNumberNormalizer.normalize(numberRaw)` and
     *   indexes both `numberRaw` and `PhoneNumberNormalizer.digitsOnly(numberRaw)` for search, using
     *   the shared normaliser from `PhoneNumbers.kt` (owned by the telephony LLD, SIDX-10).
     * - For [Source.CALL_LOG], [Source.SMS] and [Source.MMS] the contact name is copied from the
     *   name cache kept by [setContactNames], matched on `number_norm`.
     */
    suspend fun upsert(batch: List<ItemRecord>)

    /**
     * Deletes every item of [source] whose `sourceId` is not in [presentIds], together with its
     * derived rows and recording links. Call it only after a **complete** read of the source; a
     * partial or failed read must not reconcile. Returns the number of items deleted.
     */
    suspend fun reconcile(source: Source, presentIds: LongSet): Int

    /**
     * Deletes every item of [source] (only those whose type is in [types], when given), with derived
     * rows and recording links. Used when a permission is revoked (ADR-008). It does not touch
     * [SyncState]; the caller resets that. Returns the number of items deleted.
     */
    suspend fun purge(source: Source, types: Set<ItemType>? = null): Int

    /**
     * Incremental update from the matcher. In one transaction: removes every existing link whose call
     * or recording appears in [links], then inserts [links]. Links must be one-to-one (each call id
     * and each recording id at most once in [links]); otherwise `IllegalArgumentException` is thrown
     * and nothing is written. A link whose call is not a [ItemType.CALL] item, or whose recording is
     * not a [ItemType.RECORDING] item (or no longer exists), is skipped.
     */
    suspend fun replaceRecordingLinks(links: List<RecordingLink>)

    /** Full rematch: replaces **all** links with [links] in one transaction (same rules as above). */
    suspend fun replaceAllRecordingLinks(links: List<RecordingLink>)

    /**
     * Merges [names] into the number → contact-name cache and re-denormalises the name (and the
     * search text) of every call and message with that `number_norm`, so names are searchable
     * (FR-007). Keys are `PhoneNumberNormalizer.normalize` values and are used as given.
     * - A non-null value inserts or updates that number's name.
     * - A `null` value deletes that number and clears the name on its items.
     * - Numbers absent from [names] are untouched (merge semantics; the telephony indexer sends
     *   removals as `null`).
     * Only items whose name actually changes are rewritten.
     */
    suspend fun setContactNames(names: Map<String, String?>)

    /** The saved state of [source], or `null` if it has never been saved. */
    suspend fun syncState(source: Source): SyncState?

    /** Inserts or replaces the state of [SyncState.source]. */
    suspend fun saveSyncState(s: SyncState)
}

/**
 * Read side of the index (HLD §6.3), implemented by `:core:index`. Used by the Home & Search UI,
 * the Index status screen and the recording matcher. All functions are main-safe.
 *
 * Query rules (binding, HLD §6.3 and ADR-016): the query text is trimmed, then folded exactly like
 * the indexed text (NFC + `lowercase(Locale.ROOT)`, see LLD SIDX-7 §4.4). A folded query of 3 or
 * more code points uses the FTS5 trigram index; 1–2 code points scan the same search text with
 * `instr()`. Both paths search the same fields: title / file name, folder, extension, number (as
 * stored and digits-only), contact name and message text. Matching is a case-insensitive contiguous
 * substring; every character (including `"`, `*`, `%`, `_`, `(`) is literal.
 *
 * Day and range boundaries use the device time zone at call time (`ZoneId.systemDefault()`), so a
 * time-zone change needs no re-index. Paged flows emit fresh data when the index changes
 * (throttled to about once per second).
 */
interface IndexQuery {

    /**
     * Items "on" [day] in the device time zone, oldest → newest (Design Spec D2), each item once even
     * when several of its dates fall on that day. [type] `null` = all types.
     * [ResultItem.time] = the item's earliest date on that day. Messages carry the start of their
     * text in [ResultItem.snippet]; [ResultItem.matches] is empty. Page size 50.
     */
    fun timeline(day: LocalDate, type: ItemType?): Flow<PagingData<ResultItem>>

    /**
     * Search (FR-008/009/010).
     * - Blank text and no range: an empty result.
     * - Blank text with [SearchQuery.range]: the range's items, oldest → newest, like the timeline.
     * - Non-blank text: matching items, newest first; with a range, only items with at least one
     *   date inside it (inclusive), each item once.
     * [SearchQuery.type] `null` = all types. Page size 50.
     */
    fun search(q: SearchQuery): Flow<PagingData<ResultItem>>

    /** Per-tab badge counts for [day] (Home). Always contains every [ItemType] (0 when none). */
    suspend fun counts(day: LocalDate): Map<ItemType, Int>

    /**
     * Per-tab badge counts for [q] (Search). [SearchQuery.type] is ignored, because badges show every
     * tab. Always contains every [ItemType]. Cancellable: cancelling the caller drops the result.
     */
    suspend fun counts(q: SearchQuery): Map<ItemType, Int>

    /** Full detail of one item for sheets, open and play; `null` if it no longer exists. */
    suspend fun item(id: Long): ItemDetail?

    /** Live counts for the Index status screen; emits on every committed write (conflated). */
    fun stats(): Flow<IndexStats>

    /**
     * Inputs for the recording matcher (SIDX-11). [since] is an epoch-ms lower bound on event time;
     * `0` (or less) returns everything. The store widens the window by 24 h, so a recording and
     * its call are returned together.
     */
    suspend fun recordingCandidates(since: Long): RecordingInputs
}

/** A highlightable field of a [ResultItem]; each maps to the property holding the original text. */
enum class Field {
    /** [ResultItem.title] */
    TITLE,

    /** [ResultItem.folder] */
    FOLDER,

    /** [ResultItem.ext] */
    EXT,

    /** [ResultItem.number] */
    NUMBER,

    /** [ResultItem.contactName] */
    CONTACT_NAME,

    /** [ResultItem.snippet] (message text window; offsets are into the snippet string) */
    SNIPPET,
}

/**
 * One row of a timeline or search list.
 *
 * @property id the store's item id (stable across updates; use it for [IndexQuery.item]).
 * @property title the indexer's title (file name, or what the telephony indexer set). The UI picks
 *   contact name / number / title per Design Spec §6.5.
 * @property time the date that placed the row: timeline and blank-text search = earliest date in
 *   the day/range; text search without range = the item's primary time; text search with range =
 *   the latest date inside the range.
 * @property contactName contact name; for a [ItemType.RECORDING] it is the linked call's name.
 * @property number the number as stored (raw); for a [ItemType.RECORDING] the linked call's number.
 * @property snippet only for [ItemType.MESSAGE]: a window of the message text around the first
 *   match (with a leading "…" when cut), or its start when the match is elsewhere or there is no
 *   query; `null` when the message has no text. Never set for other types.
 * @property matches every occurrence of the folded query in each displayed field, as inclusive
 *   UTF-16 ranges into the ORIGINAL field text (FR-018). Empty when there is no query.
 * @property linkedRecordingId for a [ItemType.CALL] with a matched recording: the recording's item
 *   id (shows ▶, FR-012); otherwise `null`.
 */
data class ResultItem(
    val id: Long,
    val type: ItemType,
    val title: String,
    val time: Long,
    val folder: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val ext: String?,
    val direction: Direction?,
    val msgKind: MsgKind?,
    val hasAttachment: Boolean,
    val contactName: String?,
    val number: String?,
    val contentUri: String?,
    val snippet: String?,
    val matches: Map<Field, List<IntRange>>,
    val linkedRecordingId: Long?,
)

/**
 * Everything the store knows about one item.
 *
 * @property dates every date the indexer supplied, by kind (not only the ones the item is "on").
 * @property body message text for [ItemType.MESSAGE]; always `null` for other types.
 * @property linkedRecordingId for a call: its matched recording's item id.
 * @property linkedCallId for a recording: its matched call's item id.
 */
data class ItemDetail(
    val id: Long,
    val key: SourceKey,
    val type: ItemType,
    val title: String,
    val primaryTs: Long,
    val dates: Map<DateKind, Long>,
    val folder: String?,
    val mime: String?,
    val ext: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val contentUri: String?,
    val numberRaw: String?,
    val contactName: String?,
    val direction: Direction?,
    val msgKind: MsgKind?,
    val threadId: Long?,
    val hasAttachment: Boolean,
    val body: String?,
    val linkedRecordingId: Long?,
    val linkedCallId: Long?,
)

/**
 * Index status numbers (FR-021).
 *
 * @property countsByType items per type; contains every [ItemType] (0 when none).
 * @property recordingsLinked number of recording ↔ call links ("{n} linked to calls").
 * @property totalItems sum of [countsByType].
 */
data class IndexStats(
    val countsByType: Map<ItemType, Int>,
    val recordingsLinked: Int,
    val totalItems: Int,
)

/**
 * What the recording matcher (SIDX-11) needs, from [IndexQuery.recordingCandidates].
 *
 * @property calls call-log items in the window, oldest first.
 * @property recordings call-recording items in the window, oldest first.
 * @property links existing links that involve any of [calls] or [recordings].
 */
data class RecordingInputs(
    val calls: List<Call>,
    val recordings: List<Recording>,
    val links: List<RecordingLink>,
) {
    /**
     * A call candidate.
     *
     * @property numberNorm `PhoneNumberNormalizer.normalize(numberRaw)` as stored; compare it with
     *   `normalize(<number parsed from the file name>)` from the same injected normaliser.
     */
    data class Call(
        val id: Long,
        val primaryTs: Long,
        val durationMs: Long?,
        val numberNorm: String?,
        val numberRaw: String?,
        val contactName: String?,
        val direction: Direction?,
    )

    /**
     * A recording candidate.
     *
     * @property title the file name, e.g. `Rahul_241103_094215.m4a`.
     * @property folder the folder as indexed, e.g. `Recordings/Call`.
     * @property dates every date the media indexer supplied (ADDED, MODIFIED, and EVENT if parsed).
     */
    data class Recording(
        val id: Long,
        val title: String,
        val folder: String?,
        val primaryTs: Long,
        val dates: Map<DateKind, Long>,
        val durationMs: Long?,
        val sizeBytes: Long?,
    )
}

/**
 * One recording ↔ call link (one-to-one).
 *
 * @property score matcher confidence in 0..1.
 * @property method short tag of the rule that matched, e.g. `"name+ts"`, `"number+ts"`, `"time"`.
 */
data class RecordingLink(
    val callItemId: Long,
    val recordingItemId: Long,
    val score: Float,
    val method: String,
)

/**
 * Per-source resume state, written by orchestration (SIDX-12).
 *
 * @property cursor MediaStore generation, max provider `_id`, or max date, per source.
 * @property storeVersion `MediaStore.getVersion()` at the last run (MEDIA_STORE only).
 * @property lastFullReconcile epoch ms of the last complete reconcile.
 * @property lastRun epoch ms of the last run.
 * @property lastError short error code of the last failed run (never content), or `null`.
 */
data class SyncState(
    val source: Source,
    val cursor: Long,
    val storeVersion: String?,
    val lastFullReconcile: Long?,
    val lastRun: Long?,
    val lastError: String?,
)
