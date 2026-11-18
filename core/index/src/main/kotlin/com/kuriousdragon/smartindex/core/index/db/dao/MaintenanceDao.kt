package com.kuriousdragon.smartindex.core.index.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.kuriousdragon.smartindex.core.index.db.entity.ContactNameEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.index.db.entity.RecordingLinkEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ShortTextEntity
import com.kuriousdragon.smartindex.core.index.db.itemType
import com.kuriousdragon.smartindex.core.index.write.Derivation
import com.kuriousdragon.smartindex.core.model.ItemType

/** Reconcile, purge, recording links and contact names (LLD §5.2–5.4). */
@Dao
abstract class MaintenanceDao {
    // ---- reconcile / purge (§5.2)

    @Query("SELECT id, source_id AS sourceId FROM item WHERE source = :source")
    abstract suspend fun idsOf(source: Int): List<IdPair>

    @Query("DELETE FROM item WHERE id IN (:ids)")
    abstract suspend fun deleteItems(ids: List<Long>): Int

    @Query("DELETE FROM item WHERE id IN (SELECT id FROM item WHERE source = :source LIMIT 500)")
    abstract suspend fun purgeChunk(source: Int): Int

    @Query(
        "DELETE FROM item WHERE id IN " +
            "(SELECT id FROM item WHERE source = :source AND type IN (:types) LIMIT 500)",
    )
    abstract suspend fun purgeTypesChunk(source: Int, types: List<Int>): Int

    // ---- recording links (§5.3)

    @Query("DELETE FROM recording_link")
    abstract suspend fun deleteAllLinks()

    @Query("DELETE FROM recording_link WHERE call_item_id IN (:callIds)")
    abstract suspend fun deleteLinksByCall(callIds: List<Long>)

    @Query("DELETE FROM recording_link WHERE recording_item_id IN (:recordingIds)")
    abstract suspend fun deleteLinksByRecording(recordingIds: List<Long>)

    @Query("SELECT id, type FROM item WHERE id IN (:ids)")
    abstract suspend fun typesOf(ids: List<Long>): List<IdType>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertLinks(links: List<RecordingLinkEntity>)

    /**
     * Replaces links in one transaction: all of them ([all]) or those touching the given calls and
     * recordings. Links with a missing id or the wrong item type are skipped; returns how many.
     */
    @Transaction
    open suspend fun replaceLinks(links: List<RecordingLinkEntity>, all: Boolean): Int {
        if (all) {
            deleteAllLinks()
        } else {
            links.map { it.callItemId }.chunked(CHUNK).forEach { deleteLinksByCall(it) }
            links.map { it.recordingItemId }.chunked(CHUNK).forEach { deleteLinksByRecording(it) }
        }
        val types = HashMap<Long, Int>()
        links.flatMap { listOf(it.callItemId, it.recordingItemId) }.distinct().chunked(CHUNK)
            .forEach { ids -> typesOf(ids).forEach { types[it.id] = it.type } }
        val valid =
            links.filter {
                types[it.callItemId]?.let(::itemType) == ItemType.CALL &&
                    types[it.recordingItemId]?.let(::itemType) == ItemType.RECORDING
            }
        if (valid.isNotEmpty()) insertLinks(valid)
        return links.size - valid.size
    }

    // ---- contact names (§5.4)

    @Query("SELECT number_norm AS numberNorm, name FROM contact_name WHERE number_norm IN (:keys)")
    abstract suspend fun names(keys: List<String>): List<NameRow>

    @Query("SELECT * FROM item WHERE number_norm IN (:keys) AND source IN (2, 3, 4)")
    abstract suspend fun itemsByNumber(keys: List<String>): List<ItemEntity>

    @Query(
        "SELECT * FROM item WHERE id IN " +
            "(SELECT item_id FROM item_participant WHERE number_norm IN (:keys))",
    )
    abstract suspend fun itemsByParticipant(keys: List<String>): List<ItemEntity>

    @Query("SELECT item_id AS itemId, number_norm AS numberNorm FROM item_participant WHERE item_id IN (:itemIds)")
    abstract suspend fun participantsOf(itemIds: List<Long>): List<ParticipantRow>

    @Query("SELECT id, skey FROM short_text WHERE id IN (:ids)")
    abstract suspend fun skeysOf(ids: List<Long>): List<SkeyRow>

    @Query("DELETE FROM contact_name WHERE number_norm IN (:keys)")
    abstract suspend fun removeNames(keys: List<String>)

    @Upsert
    abstract suspend fun upsertNames(rows: List<ContactNameEntity>)

    @Update
    abstract suspend fun updateItems(rows: List<ItemEntity>)

    @Update
    abstract suspend fun updateShortTexts(rows: List<ShortTextEntity>)

    /** Applies already-filtered real changes (null value = remove) and re-denormalises affected items. */
    @Transaction
    open suspend fun applyContactNames(changes: Map<String, String?>, derivation: Derivation) {
        val keys = changes.keys.toList()
        val upserts = changes.mapNotNull { (k, v) -> v?.let { ContactNameEntity(k, it) } }
        if (upserts.isNotEmpty()) upsertNames(upserts)
        val removals = changes.filterValues { it == null }.keys.toList()
        if (removals.isNotEmpty()) removeNames(removals)
        // keys arrive in chunks of <= 500 (setContactNames), so every IN list below stays within the limit
        val affected = LinkedHashMap<Long, ItemEntity>()
        itemsByNumber(keys).forEach { affected[it.id] = it }
        itemsByParticipant(keys).forEach { affected.putIfAbsent(it.id, it) }
        if (affected.isEmpty()) return
        val byItem = HashMap<Long, MutableList<String>>()
        affected.keys.chunked(CHUNK).forEach { ids ->
            participantsOf(ids).forEach { byItem.getOrPut(it.itemId) { mutableListOf() }.add(it.numberNorm) }
        }
        val norms = (affected.values.mapNotNull { it.numberNorm } + byItem.values.flatten()).distinct()
        val current = HashMap<String, String>()
        norms.chunked(CHUNK).forEach { part -> names(part).forEach { current[it.numberNorm] = it.name } }
        val items = ArrayList<ItemEntity>()
        val texts = ArrayList<ShortTextEntity>()
        val stored = HashMap<Long, String>()
        affected.keys.chunked(CHUNK).forEach { ids -> skeysOf(ids).forEach { stored[it.id] = it.skey } }
        for (item in affected.values) {
            val updated = item.copy(contactName = item.numberNorm?.let { current[it] })
            val short = derivation.shortText(updated, byItem[item.id].orEmpty().mapNotNull { current[it] })
            if (updated.contactName != item.contactName || short.skey != stored[item.id]) {
                items.add(updated)
                texts.add(short)
            }
        }
        if (items.isEmpty()) return
        updateItems(items)
        updateShortTexts(texts)
    }

    private companion object {
        const val CHUNK = 500
    }
}
