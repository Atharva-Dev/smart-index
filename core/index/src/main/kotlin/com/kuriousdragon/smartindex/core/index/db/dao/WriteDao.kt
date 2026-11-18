package com.kuriousdragon.smartindex.core.index.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.kuriousdragon.smartindex.core.index.db.code
import com.kuriousdragon.smartindex.core.index.db.entity.ItemDateEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemKeyEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemParticipantEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ShortTextEntity
import com.kuriousdragon.smartindex.core.index.db.entity.SyncStateEntity
import com.kuriousdragon.smartindex.core.index.write.Derivation
import com.kuriousdragon.smartindex.core.index.write.RecordMapper
import com.kuriousdragon.smartindex.core.index.write.RecordMapper.Companion.hasContactName
import com.kuriousdragon.smartindex.core.model.ItemRecord

/** Write path of the index (LLD §5.1). */
@Dao
abstract class WriteDao {
    @Query(
        "SELECT id, source_id AS sourceId, source_version AS sourceVersion FROM item " +
            "WHERE source = :source AND source_id IN (:sourceIds)",
    )
    abstract suspend fun existing(source: Int, sourceIds: List<Long>): List<ExistingRow>

    @Query("SELECT number_norm AS numberNorm, name FROM contact_name WHERE number_norm IN (:keys)")
    abstract suspend fun names(keys: List<String>): List<NameRow>

    @Query("DELETE FROM item_participant WHERE item_id = :id")
    abstract suspend fun deleteParticipants(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertParticipants(rows: List<ItemParticipantEntity>)

    @Query("DELETE FROM item_date WHERE item_id = :id")
    abstract suspend fun deleteDates(id: Long)

    @Insert
    abstract suspend fun insertItem(e: ItemEntity): Long

    @Update
    abstract suspend fun updateItem(e: ItemEntity)

    @Insert
    abstract suspend fun insertKey(e: ItemKeyEntity)

    @Update
    abstract suspend fun updateKey(e: ItemKeyEntity)

    @Insert
    abstract suspend fun insertDates(rows: List<ItemDateEntity>)

    @Insert
    abstract suspend fun insertShortText(e: ShortTextEntity)

    @Update
    abstract suspend fun updateShortText(e: ShortTextEntity)

    @Upsert
    abstract suspend fun upsertSyncState(s: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE source = :source")
    abstract suspend fun syncState(source: Int): SyncStateEntity?

    /** Writes one chunk (≤ 500 unique records) in a single transaction; returns how many records were written. */
    @Transaction
    open suspend fun writeChunk(chunk: List<ItemRecord>, mapper: RecordMapper, derivation: Derivation): Int {
        var written = 0
        val normCache = HashMap<String, String?>()
        for ((source, records) in chunk.groupBy { it.key.source }) {
            val stored = existing(source.code(), records.map { it.key.sourceId }).associateBy { it.sourceId }
            val work = records.filter { r -> stored[r.key.sourceId]?.sourceVersion != r.sourceVersion }
            if (work.isEmpty()) continue
            written += work.size
            val withNames = source.hasContactName()
            val others: Map<Long, List<String>> =
                if (withNames) {
                    work.associate { r ->
                        val primary = mapper.norm(r.numberRaw, normCache)
                        r.key.sourceId to
                            r.otherNumbers.mapNotNull { mapper.norm(it, normCache) }.distinct().filter { it != primary }
                    }
                } else {
                    emptyMap()
                }
            val nameByNorm: Map<String, String> =
                if (withNames) {
                    val norms =
                        (work.mapNotNull { mapper.norm(it.numberRaw, normCache) } + others.values.flatten()).distinct()
                    norms.chunked(NAME_CHUNK).flatMap { names(it) }.associate { it.numberNorm to it.name }
                } else {
                    emptyMap()
                }
            for (r in work) {
                val name = mapper.norm(r.numberRaw, normCache)?.let { nameByNorm[it] }
                val participants = others[r.key.sourceId].orEmpty()
                val extraNames = participants.mapNotNull { nameByNorm[it] }
                val old = stored[r.key.sourceId]
                if (old == null) {
                    val draft = mapper.toEntity(r, 0, name, normCache)
                    val id = insertItem(draft)
                    val d = derivation.derive(draft.copy(id = id), extraNames)
                    insertKey(d.key)
                    insertDates(d.dates)
                    insertShortText(d.shortText)
                    if (participants.isNotEmpty()) {
                        insertParticipants(
                            participants.map {
                                ItemParticipantEntity(id, it)
                            },
                        )
                    }
                } else {
                    val e = mapper.toEntity(r, old.id, name, normCache)
                    val d = derivation.derive(e, extraNames)
                    updateItem(e)
                    updateKey(d.key)
                    deleteDates(old.id)
                    insertDates(d.dates)
                    updateShortText(d.shortText)
                    deleteParticipants(old.id)
                    if (participants.isNotEmpty()) {
                        insertParticipants(participants.map { ItemParticipantEntity(old.id, it) })
                    }
                }
            }
        }
        return written
    }

    private companion object {
        const val NAME_CHUNK = 500
    }
}
