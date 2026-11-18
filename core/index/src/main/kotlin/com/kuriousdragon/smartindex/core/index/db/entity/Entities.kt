package com.kuriousdragon.smartindex.core.index.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room entities for schema v1 (LLD §4.2). Enums are stored as stable Int codes (see Codes.kt). */

@Entity(
    tableName = "item",
    indices = [
        Index(value = ["source", "source_id"], unique = true, name = "idx_item_source"),
        Index(value = ["type", "primary_ts"], name = "idx_item_type_ts"),
        Index(value = ["number_norm"], name = "idx_item_number_norm"),
    ],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "source") val source: Int,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "folder") val folder: String?,
    @ColumnInfo(name = "mime") val mime: String?,
    @ColumnInfo(name = "ext") val ext: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "content_uri") val contentUri: String?,
    @ColumnInfo(name = "primary_ts") val primaryTs: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long?,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "date_event") val dateEvent: Long?,
    @ColumnInfo(name = "number_raw") val numberRaw: String?,
    @ColumnInfo(name = "number_norm") val numberNorm: String?,
    @ColumnInfo(name = "contact_name") val contactName: String?,
    @ColumnInfo(name = "direction") val direction: Int?,
    @ColumnInfo(name = "msg_kind") val msgKind: Int?,
    @ColumnInfo(name = "thread_id") val threadId: Long?,
    @ColumnInfo(name = "has_attachment") val hasAttachment: Boolean,
    @ColumnInfo(name = "body") val body: String?,
    @ColumnInfo(name = "source_version") val sourceVersion: Long,
)

@Entity(
    tableName = "item_key",
    indices = [
        Index(value = ["ts", "id"], name = "idx_item_key_ts"),
        Index(value = ["type", "ts", "id"], name = "idx_item_key_type_ts"),
    ],
)
data class ItemKeyEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "ts") val ts: Long,
)

@Entity(
    tableName = "item_date",
    primaryKeys = ["item_id", "kind"],
    indices = [
        Index(value = ["ts", "type", "item_id"], name = "idx_item_date_ts"),
        Index(value = ["type", "ts", "item_id"], name = "idx_item_date_type_ts"),
    ],
)
data class ItemDateEntity(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "kind") val kind: Int,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "ts") val ts: Long,
)

@Entity(
    tableName = "short_text",
    indices = [Index(value = ["ts", "id", "type", "skey"], name = "idx_short_text_scan")],
)
data class ShortTextEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "ts") val ts: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "skey") val skey: String,
)

@Entity(
    tableName = "recording_link",
    indices = [Index(value = ["recording_item_id"], unique = true, name = "idx_recording_link_rec")],
)
data class RecordingLinkEntity(
    @PrimaryKey @ColumnInfo(name = "call_item_id") val callItemId: Long,
    @ColumnInfo(name = "recording_item_id") val recordingItemId: Long,
    @ColumnInfo(name = "score") val score: Double,
    @ColumnInfo(name = "method") val method: String,
)

/** Further participants of a group message, by normalised number (schema v2, SIDX-108). */
@Entity(
    tableName = "item_participant",
    primaryKeys = ["item_id", "number_norm"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["number_norm"], name = "idx_item_participant_norm")],
)
data class ItemParticipantEntity(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "number_norm") val numberNorm: String,
)

@Entity(tableName = "contact_name")
data class ContactNameEntity(
    @PrimaryKey @ColumnInfo(name = "number_norm") val numberNorm: String,
    @ColumnInfo(name = "name") val name: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "source") val source: Int,
    @ColumnInfo(name = "cursor") val cursor: Long,
    @ColumnInfo(name = "store_version") val storeVersion: String?,
    @ColumnInfo(name = "last_full_reconcile") val lastFullReconcile: Long?,
    @ColumnInfo(name = "last_run") val lastRun: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
)
