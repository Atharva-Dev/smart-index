package com.kuriousdragon.smartindex.core.index.query

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.db.dao.TypeCount
import com.kuriousdragon.smartindex.core.index.db.direction
import com.kuriousdragon.smartindex.core.index.db.itemType
import com.kuriousdragon.smartindex.core.index.db.msgKind
import com.kuriousdragon.smartindex.core.index.db.source
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.ItemDetail
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.SourceKey
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Room implementation of [IndexQuery]. */
class RoomIndexQuery @Inject constructor(
    private val db: IndexDatabase,
    private val signal: IndexChangeSignal,
    private val zones: TimeZoneSource,
) : IndexQuery {
    override fun timeline(day: LocalDate, type: ItemType?): Flow<PagingData<ResultItem>> =
        pages(SearchPlan.Browse(DayBounds.of(day, zones.zone()), type))

    override fun search(q: SearchQuery): Flow<PagingData<ResultItem>> =
        when (val plan = SearchPlan.of(q)) {
            SearchPlan.Empty -> flowOf(PagingData.empty())
            else -> pages(plan)
        }

    private fun pages(plan: SearchPlan): Flow<PagingData<ResultItem>> =
        Pager(
            PagingConfig(
                pageSize = IndexConfig.PAGE_SIZE,
                initialLoadSize = IndexConfig.PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
        ) {
            IndexPagingSource(plan, db, signal)
        }.flow

    override suspend fun counts(day: LocalDate): Map<ItemType, Int> =
        PerfTrace.traced(PerfTrace.STORE_COUNTS) {
            countsOf(SearchPlan.Browse(DayBounds.of(day, zones.zone()), null))
        }

    override suspend fun counts(q: SearchQuery): Map<ItemType, Int> =
        PerfTrace.traced(PerfTrace.STORE_COUNTS) { countsOf(SearchPlan.of(q)) }

    private suspend fun countsOf(plan: SearchPlan): Map<ItemType, Int> {
        if (plan == SearchPlan.Empty) return zeroFilled(emptyList())
        return zeroFilled(db.searchDao().typeCounts(SearchSql.counts(plan)))
    }

    override suspend fun item(id: Long): ItemDetail? {
        val row = db.readDao().detail(id) ?: return null
        val e = row.item
        return ItemDetail(
            id = e.id,
            key = SourceKey(source(e.source), e.sourceId),
            type = itemType(e.type),
            title = e.title,
            primaryTs = e.primaryTs,
            dates = dates(e.dateTaken, e.dateAdded, e.dateModified, e.dateEvent),
            folder = e.folder,
            mime = e.mime,
            ext = e.ext,
            sizeBytes = e.sizeBytes,
            durationMs = e.durationMs,
            contentUri = e.contentUri,
            numberRaw = e.numberRaw,
            contactName = e.contactName,
            direction = e.direction?.let(::direction),
            msgKind = e.msgKind?.let(::msgKind),
            threadId = e.threadId,
            hasAttachment = e.hasAttachment,
            body = e.body,
            linkedRecordingId = row.linkedRecordingId,
            linkedCallId = row.linkedCallId,
        )
    }

    override fun stats(): Flow<IndexStats> {
        val dao = db.readDao()
        return combine(dao.typeCountsFlow(), dao.linkCountFlow()) { types, links ->
            val counts = zeroFilled(types)
            IndexStats(countsByType = counts, recordingsLinked = links, totalItems = counts.values.sum())
        }.distinctUntilChanged().conflate()
    }

    override suspend fun recordingCandidates(since: Long): RecordingInputs {
        val dao = db.readDao()
        val from = if (since <= 0) Long.MIN_VALUE else since - WINDOW_MARGIN_MS
        val calls = dao.calls(from)
        val recordings = dao.recordings(from)
        val links =
            if (since <= 0) {
                dao.allLinks()
            } else {
                val callIds = calls.map { it.id }
                val recIds = recordings.map { it.id }
                buildList {
                    // Chunks of 500 per side keep the bound-variable count well under SQLite's limit.
                    for (i in 0 until maxOf(callIds.size, recIds.size) step LINK_CHUNK) {
                        addAll(dao.linksFor(callIds.drop(i).take(LINK_CHUNK), recIds.drop(i).take(LINK_CHUNK)))
                    }
                }.distinctBy { it.callItemId }
            }
        return RecordingInputs(
            calls = calls.map {
                RecordingInputs.Call(
                    it.id,
                    it.primaryTs,
                    it.durationMs,
                    it.numberNorm,
                    it.numberRaw,
                    it.contactName,
                    it.direction?.let(::direction),
                )
            },
            recordings = recordings.map {
                RecordingInputs.Recording(
                    it.id,
                    it.title,
                    it.folder,
                    it.primaryTs,
                    dates(it.dateTaken, it.dateAdded, it.dateModified, it.dateEvent),
                    it.durationMs,
                    it.sizeBytes,
                )
            },
            links = links.map { RecordingLink(it.callItemId, it.recordingItemId, it.score.toFloat(), it.method) },
        )
    }

    private fun zeroFilled(rows: List<TypeCount>): Map<ItemType, Int> {
        val byType = rows.associate { itemType(it.type) to it.n }
        return ItemType.entries.associateWith { byType[it] ?: 0 }
    }

    private fun dates(taken: Long?, added: Long?, modified: Long?, event: Long?): Map<DateKind, Long> =
        buildMap {
            taken?.let { put(DateKind.TAKEN, it) }
            added?.let { put(DateKind.ADDED, it) }
            modified?.let { put(DateKind.MODIFIED, it) }
            event?.let { put(DateKind.EVENT, it) }
        }

    private companion object {
        const val PREFETCH_DISTANCE = 15
        const val WINDOW_MARGIN_MS = 86_400_000L
        const val LINK_CHUNK = 500
    }
}
