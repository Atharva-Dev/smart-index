package com.kuriousdragon.smartindex.feature.home.pipeline

import androidx.paging.PagingData
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.ListEntry
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Commit to a paged list with section headers. */
internal class ListPipeline(private val index: IndexQuery) {
    fun pages(c: Commit): Flow<PagingData<ListEntry>> = when (val r = c.request) {
        is ListRequest.Timeline -> index.timeline(r.day, r.type)
        is ListRequest.Search -> index.search(r.query)
        ListRequest.Hint, is ListRequest.Locked -> flowOf(PagingData.empty())
    }.map { it.withHeaders(c.grouping, c.env.zone) }
}
