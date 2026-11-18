package com.kuriousdragon.smartindex.feature.home.pipeline

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.ListEntry
import java.time.ZoneId

/** Wraps every item in a row and inserts a header where the group changes; no trailing header. */
internal fun PagingData<ResultItem>.withHeaders(grouping: Grouping, zone: ZoneId): PagingData<ListEntry> =
    map<ResultItem, ListEntry> { ListEntry.Row(it) }.insertSeparators { before, after ->
        val next = (after as? ListEntry.Row)?.item ?: return@insertSeparators null
        val group = grouping.groupOf(next.time, zone)
        val previous = (before as? ListEntry.Row)?.item?.let { grouping.groupOf(it.time, zone) }
        if (previous == group) null else ListEntry.Header(group)
    }
