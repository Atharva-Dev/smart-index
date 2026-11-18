package com.kuriousdragon.smartindex.feature.home.model

import com.kuriousdragon.smartindex.core.model.ResultItem

/** What the LazyColumn renders. */
sealed interface ListEntry {
    /** Row "r<id>", Header "h:PART:<NAME>" or "h:DATE:<epochDay>". */
    val key: String

    data class Row(val item: ResultItem) : ListEntry {
        override val key: String get() = "r${item.id}"
    }

    data class Header(val group: ListGroup) : ListEntry {
        override val key: String
            get() = when (group) {
                is ListGroup.PartOfDayGroup -> "h:PART:${group.part.name}"
                is ListGroup.DateGroup -> "h:DATE:${group.date.toEpochDay()}"
            }
    }
}
