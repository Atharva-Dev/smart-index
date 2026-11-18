package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.index.db.dao.KeyRow
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/** Runs the real A-E queries through [com.kuriousdragon.smartindex.core.index.db.dao.SearchDao] on a seeded DB. */
class SearchDaoTest : IndexDbTest() {
    private lateinit var seaSms: Pair<Long, Long>
    private var raSms = 0L
    private var raPhoto = 0L
    private var otherPhoto = 0L
    private var docId = 0L

    @Before
    fun seed() {
        runBlocking {
            writer.upsert(
                listOf(
                    sms(1, number = "+91 98765 43210", body = "Hotel Sea View"),
                    sms(2, number = "+91 98765 43210", body = "Rahul says hi"),
                    photo(3, title = "sea view.jpg", taken = 1_000, added = 2_000),
                    photo(4, title = "other.jpg", taken = 500_000, added = 500_001),
                    doc(5, title = "ra.pdf"),
                ),
            )
        }
        seaSms = idOf(Source.SMS, 1) to 10_001L
        raSms = idOf(Source.SMS, 2)
        raPhoto = idOf(Source.MEDIA_STORE, 3)
        otherPhoto = idOf(Source.MEDIA_STORE, 4)
        docId = idOf(Source.MEDIA_STORE, 5)
    }

    private fun keys(q: SearchQuery, limit: Int = 50, offset: Int = 0): List<KeyRow> =
        runBlocking { db.searchDao().keys(SearchSql.page(SearchPlan.of(q), limit, offset)) }

    private fun counts(q: SearchQuery): Map<Int, Int> =
        runBlocking { db.searchDao().typeCounts(SearchSql.counts(SearchPlan.of(q))) }.associate { it.type to it.n }

    private fun ids(q: SearchQuery) = keys(q).map { it.id }

    @Test
    fun `plan A finds sea view across a message and a file, newest first`() {
        val k = keys(SearchQuery("sea view", null, null))
        assertThat(k.map { it.id }).containsExactly(seaSms.first, raPhoto).inOrder()
        assertThat(k.first().ts).isEqualTo(seaSms.second)
        assertThat(ids(SearchQuery("sea view", null, ItemType.PHOTO))).containsExactly(raPhoto)
        assertThat(counts(SearchQuery("sea view", null, ItemType.PHOTO))).containsExactly(1, 1, 7, 1)
    }

    @Test
    fun `plan B restricts to the range and reports the latest date in it`() {
        val k = keys(SearchQuery("sea view", DateRange(0, 1_500), null))
        assertThat(k).containsExactly(KeyRow(raPhoto, 1_000))
        assertThat(keys(SearchQuery("sea view", DateRange(400_000, 600_000), null))).isEmpty()
    }

    @Test
    fun `plan C finds ra in every field once per item`() {
        // other.jpg and sea view.jpg match through the folder "DCIM/Camera", ra.pdf through its title, Rahul via the body.
        assertThat(ids(SearchQuery("ra", null, null))).containsExactly(otherPhoto, raSms, docId, raPhoto).inOrder()
        assertThat(ids(SearchQuery("ra", null, ItemType.MESSAGE))).containsExactly(raSms)
        assertThat(counts(SearchQuery("ra", null, ItemType.MESSAGE))).containsExactly(1, 2, 3, 1, 7, 1)
    }

    @Test
    fun `plan D combines a short query with a range`() {
        val k = keys(SearchQuery("ra", DateRange(5_000, 6_000), null))
        assertThat(k).containsExactly(KeyRow(docId, 6_000))
    }

    @Test
    fun `plan E lists a day oldest first, once per item`() {
        val k = keys(SearchQuery("", DateRange(0, 10_000), null))
        assertThat(k).containsExactly(KeyRow(raPhoto, 1_000), KeyRow(docId, 5_000)).inOrder()
        assertThat(counts(SearchQuery("", DateRange(0, 10_000), ItemType.PHOTO))).containsExactly(1, 1, 3, 1)
    }

    @Test
    fun `paging limit and offset are applied`() {
        assertThat(
            keys(SearchQuery("sea view", null, null), limit = 1, offset = 1).map {
                it.id
            },
        ).containsExactly(raPhoto)
    }
}
