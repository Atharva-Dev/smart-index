package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import org.junit.Test

class SearchPlanTest {
    private val range = DateRange(10, 20)

    @Test
    fun `blank text without range is Empty`() {
        assertThat(SearchPlan.of(SearchQuery("", null, null))).isEqualTo(SearchPlan.Empty)
        assertThat(SearchPlan.of(SearchQuery("   ", null, ItemType.PHOTO))).isEqualTo(SearchPlan.Empty)
        assertThat(SearchPlan.Empty.foldedQuery).isNull()
    }

    @Test
    fun `blank text with range is Browse and keeps the type`() {
        assertThat(SearchPlan.of(SearchQuery("", range, null))).isEqualTo(SearchPlan.Browse(range, null))
        assertThat(
            SearchPlan.of(SearchQuery(" ", range, ItemType.CALL)),
        ).isEqualTo(SearchPlan.Browse(range, ItemType.CALL))
        assertThat(SearchPlan.of(SearchQuery("", range, null)).foldedQuery).isNull()
    }

    @Test
    fun `one or two code points is Short and trimmed`() {
        assertThat(SearchPlan.of(SearchQuery("  r ", null, null))).isEqualTo(SearchPlan.Short("r", null, null))
        assertThat(SearchPlan.of(SearchQuery("RA", range, ItemType.MESSAGE)))
            .isEqualTo(SearchPlan.Short("ra", range, ItemType.MESSAGE))
        assertThat(SearchPlan.of(SearchQuery("हो", null, null))).isInstanceOf(SearchPlan.Short::class.java)
        assertThat(SearchPlan.of(SearchQuery("ra", null, null)).foldedQuery).isEqualTo("ra")
    }

    @Test
    fun `the Short and Fts boundary is the shared model constant`() {
        val max = IndexConfig.SHORT_QUERY_MAX_CODE_POINTS
        assertThat(max).isEqualTo(2)
        assertThat(SearchPlan.of(SearchQuery("a".repeat(max), null, null))).isInstanceOf(SearchPlan.Short::class.java)
        assertThat(SearchPlan.of(SearchQuery("a".repeat(max + 1), null, null))).isInstanceOf(SearchPlan.Fts::class.java)
        assertThat(SearchPlan.of(SearchQuery("\uD83D\uDE00\uD83D\uDE00", null, null)))
            .isInstanceOf(SearchPlan.Short::class.java)
    }

    @Test
    fun `three or more code points is Fts with an escaped phrase`() {
        val p = SearchPlan.of(SearchQuery("SEA VIEW", null, null))
        assertThat(p).isEqualTo(SearchPlan.Fts("sea view", "\"sea view\"", null, null))
        assertThat(p.foldedQuery).isEqualTo("sea view")
        val q = SearchPlan.of(SearchQuery("a\"b", range, ItemType.DOCUMENT)) as SearchPlan.Fts
        assertThat(q.phrase).isEqualTo("\"a\"\"b\"")
        assertThat(q.range).isEqualTo(range)
        assertThat(q.type).isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `an emoji surrogate pair counts as one code point`() {
        assertThat(SearchPlan.of(SearchQuery("😀😀", null, null))).isInstanceOf(SearchPlan.Short::class.java)
        assertThat(SearchPlan.of(SearchQuery("😀😀a", null, null))).isInstanceOf(SearchPlan.Fts::class.java)
    }
}
