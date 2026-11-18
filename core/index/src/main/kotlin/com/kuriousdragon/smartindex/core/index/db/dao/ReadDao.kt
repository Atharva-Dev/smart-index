package com.kuriousdragon.smartindex.core.index.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.kuriousdragon.smartindex.core.index.db.entity.RecordingLinkEntity
import kotlinx.coroutines.flow.Flow

/** Hydration of list rows (LLD §5.7), item detail, stats and recording candidates (LLD §5.9). */
@Dao
interface ReadDao {
    /** The wide rows for at most one page of ids; a recording row also carries its linked call's name and number. */
    @Query(
        """
        SELECT i.id AS id, i.type AS type, i.title AS title, i.folder AS folder, i.size_bytes AS size_bytes,
               i.duration_ms AS duration_ms, i.ext AS ext, i.direction AS direction, i.msg_kind AS msg_kind,
               i.has_attachment AS has_attachment, i.contact_name AS contact_name, i.number_raw AS number_raw,
               i.content_uri AS content_uri, i.body AS body,
               lc.recording_item_id AS linked_recording_id,
               c.contact_name AS call_contact_name, c.number_raw AS call_number_raw
        FROM item i
        LEFT JOIN recording_link lc ON lc.call_item_id = i.id
        LEFT JOIN recording_link lr ON lr.recording_item_id = i.id
        LEFT JOIN item c ON c.id = lr.call_item_id
        WHERE i.id IN (:ids)
        """,
    )
    suspend fun rows(ids: List<Long>): List<ResultRow>

    @Query(
        """
        SELECT i.*, lc.recording_item_id AS linked_recording_id, lr.call_item_id AS linked_call_id
        FROM item i LEFT JOIN recording_link lc ON lc.call_item_id = i.id
                    LEFT JOIN recording_link lr ON lr.recording_item_id = i.id
        WHERE i.id = :id
        """,
    )
    suspend fun detail(id: Long): DetailRow?

    @Query("SELECT type, COUNT(*) AS n FROM item_key GROUP BY type")
    fun typeCountsFlow(): Flow<List<TypeCount>>

    @Query("SELECT COUNT(*) FROM recording_link")
    fun linkCountFlow(): Flow<Int>

    @Query(
        """
        SELECT id, primary_ts, duration_ms, number_norm, number_raw, contact_name, direction
        FROM item WHERE type = 5 AND primary_ts >= :from ORDER BY primary_ts
        """,
    )
    suspend fun calls(from: Long): List<CallRow>

    @Query(
        """
        SELECT id, title, folder, primary_ts, date_taken, date_added, date_modified, date_event, duration_ms, size_bytes
        FROM item WHERE type = 6 AND (primary_ts >= :from OR date_modified >= :from OR date_event >= :from)
        ORDER BY primary_ts
        """,
    )
    suspend fun recordings(from: Long): List<RecordingRow>

    @Query("SELECT * FROM recording_link")
    suspend fun allLinks(): List<RecordingLinkEntity>

    @Query("SELECT * FROM recording_link WHERE call_item_id IN (:callIds) OR recording_item_id IN (:recordingIds)")
    suspend fun linksFor(callIds: List<Long>, recordingIds: List<Long>): List<RecordingLinkEntity>
}
