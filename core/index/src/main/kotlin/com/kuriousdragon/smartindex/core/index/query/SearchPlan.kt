package com.kuriousdragon.smartindex.core.index.query

import com.kuriousdragon.smartindex.core.index.text.FtsPhrase
import com.kuriousdragon.smartindex.core.index.text.TextFolder
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery

/** How a [SearchQuery] is executed (LLD §5.5): Empty, Browse (plan E), Short (C/D) or Fts (A/B). */
sealed interface SearchPlan {
    val type: ItemType?

    data object Empty : SearchPlan {
        override val type: ItemType? = null
    }

    data class Browse(val range: DateRange, override val type: ItemType?) : SearchPlan

    data class Short(val folded: String, val range: DateRange?, override val type: ItemType?) : SearchPlan

    data class Fts(val folded: String, val phrase: String, val range: DateRange?, override val type: ItemType?) :
        SearchPlan

    companion object {
        /** Queries of at most this many code points use the substring scan instead of FTS. */
        private const val SHORT_MAX = IndexConfig.SHORT_QUERY_MAX_CODE_POINTS

        fun of(q: SearchQuery): SearchPlan {
            val t = TextFolder.fold(q.text.trim())
            val n = TextFolder.codePointLength(t)
            return when {
                n == 0 && q.range == null -> Empty
                n == 0 -> Browse(q.range!!, q.type)
                n <= SHORT_MAX -> Short(t, q.range, q.type)
                else -> Fts(t, FtsPhrase.of(t), q.range, q.type)
            }
        }
    }
}

/** The folded query text for Short and Fts plans; null otherwise. */
val SearchPlan.foldedQuery: String?
    get() =
        when (this) {
            is SearchPlan.Short -> folded
            is SearchPlan.Fts -> folded
            else -> null
        }
