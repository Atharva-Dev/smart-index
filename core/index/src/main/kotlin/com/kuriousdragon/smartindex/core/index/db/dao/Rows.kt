package com.kuriousdragon.smartindex.core.index.db.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity

/** Query result rows (more are added by the tickets that need them). */

data class KeyRow(val id: Long, val ts: Long)

data class TypeCount(val type: Int, val n: Int)

/** Result of `WriteDao.existing`. */
data class ExistingRow(val id: Long, val sourceId: Long, val sourceVersion: Long)

/** Result of `WriteDao.names`. */
data class NameRow(val numberNorm: String, val name: String)

/** Result of `MaintenanceDao.idsOf`. */
data class IdPair(val id: Long, val sourceId: Long)

/** Result of `MaintenanceDao.typesOf`. */
data class IdType(val id: Long, val type: Int)

/** Result of `ReadDao.rows`: the wide row behind one list entry (LLD §5.7). */
data class ResultRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "folder") val folder: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "ext") val ext: String?,
    @ColumnInfo(name = "direction") val direction: Int?,
    @ColumnInfo(name = "msg_kind") val msgKind: Int?,
    @ColumnInfo(name = "has_attachment") val hasAttachment: Boolean,
    @ColumnInfo(name = "contact_name") val contactName: String?,
    @ColumnInfo(name = "number_raw") val numberRaw: String?,
    @ColumnInfo(name = "content_uri") val contentUri: String?,
    @ColumnInfo(name = "body") val body: String?,
    @ColumnInfo(name = "linked_recording_id") val linkedRecordingId: Long?,
    @ColumnInfo(name = "call_contact_name") val callContactName: String?,
    @ColumnInfo(name = "call_number_raw") val callNumberRaw: String?,
)

/** Result of `ReadDao.detail`: the item plus the id of its linked recording / call (LLD §5.9). */
data class DetailRow(
    @Embedded val item: ItemEntity,
    @ColumnInfo(name = "linked_recording_id") val linkedRecordingId: Long?,
    @ColumnInfo(name = "linked_call_id") val linkedCallId: Long?,
)

/** Result of `ReadDao.calls`: a call candidate for the recording matcher. */
data class CallRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "primary_ts") val primaryTs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "number_norm") val numberNorm: String?,
    @ColumnInfo(name = "number_raw") val numberRaw: String?,
    @ColumnInfo(name = "contact_name") val contactName: String?,
    @ColumnInfo(name = "direction") val direction: Int?,
)

/** Result of `ReadDao.recordings`: a recording candidate for the recording matcher. */
data class RecordingRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "folder") val folder: String?,
    @ColumnInfo(name = "primary_ts") val primaryTs: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long?,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "date_event") val dateEvent: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
)

/** Result of `MaintenanceDao.participantsOf`. */
data class ParticipantRow(val itemId: Long, val numberNorm: String)

/** Result of `MaintenanceDao.skeysOf`. */
data class SkeyRow(val id: Long, val skey: String)
