package com.kuriousdragon.smartindex.core.index.write

import androidx.collection.longSetOf
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** SIDX-108 / SIDX-116: a group message is searchable by the saved name of every participant. */
class GroupMessageSearchTest : IndexDbTest() {
    private val a = "+91 98765 43210"
    private val b = "+91 11111 22222"
    private val c = "+91 77777 88888"
    private val normA = "+919876543210"
    private val normB = "+911111122222"
    private val normC = "+917777788888"

    private fun mms(id: Long, vararg others: String, primary: String = a, version: Long = 1): ItemRecord =
        sms(id, number = primary, version = version)
            .copy(key = SourceKey(Source.MMS, id), otherNumbers = others.toList())

    private fun write(vararg r: ItemRecord) = runBlocking { writer.upsert(r.toList()) }

    private fun names(vararg p: Pair<String, String?>) = runBlocking { writer.setContactNames(mapOf(*p)) }

    private fun found(text: String): List<Long> = query.search(SearchQuery(text, null, null)).all().map { it.id }

    private fun participants(): List<String?> =
        texts("SELECT number_norm FROM item_participant ORDER BY number_norm")

    private fun seedNames() = names(normA to "Rahul", normB to "Meera", normC to "Sanjay")

    @Test
    fun `every participant name finds the message on both plans`() {
        seedNames()
        write(mms(1, b, c))
        val id = idOf(Source.MMS, 1)
        for (term in listOf("rahul", "meera", "sanjay", "me", "sa", "rah")) {
            assertThat(found(term)).containsExactly(id)
        }
        assertThat(found("zed")).isEmpty()
        assertThat(found("zq")).isEmpty()
    }

    @Test
    fun `display name stays the primary contact`() {
        seedNames()
        write(mms(1, b, c))
        assertThat(texts("SELECT contact_name FROM item")).containsExactly("Rahul")
    }

    @Test
    fun `a name saved after the upsert makes the message findable and removing it undoes that`() {
        write(mms(1, b, c))
        val id = idOf(Source.MMS, 1)
        assertThat(found("meera")).isEmpty()
        names(normB to "Meera")
        assertThat(found("meera")).containsExactly(id)
        assertThat(found("me")).containsExactly(id)
        names(normB to null)
        assertThat(found("meera")).isEmpty()
        assertThat(found("me")).isEmpty()
    }

    @Test
    fun `a renamed participant replaces the old term`() {
        seedNames()
        write(mms(1, b))
        names(normB to "Meena")
        assertThat(found("meera")).isEmpty()
        assertThat(found("meena")).hasSize(1)
    }

    @Test
    fun `a primary-only record behaves as before`() {
        seedNames()
        write(mms(1))
        assertThat(found("rahul")).hasSize(1)
        assertThat(found("meera")).isEmpty()
        assertThat(count("item_participant")).isEqualTo(0)
    }

    @Test
    fun `re-upserting with another participant list replaces the participant rows`() {
        seedNames()
        write(mms(1, b, c))
        assertThat(participants()).containsExactly(normB, normC).inOrder()
        write(mms(1, c, version = 2))
        assertThat(participants()).containsExactly(normC)
        assertThat(found("meera")).isEmpty()
        assertThat(found("sanjay")).hasSize(1)
    }

    @Test
    fun `duplicates, the primary number and blank numbers are ignored`() {
        write(mms(1, b, "+91 11111 22222", "011111 22222", a, "", "  "))
        assertThat(participants()).containsExactly(normB)
    }

    @Test
    fun `deleting an item removes its participant rows`() {
        write(mms(1, b, c), mms(2, c))
        assertThat(count("item_participant")).isEqualTo(3)
        runBlocking { writer.reconcile(Source.MMS, longSetOf(2L)) }
        assertThat(count("item_participant")).isEqualTo(1)
        runBlocking { writer.purge(Source.MMS) }
        assertThat(count("item_participant")).isEqualTo(0)
    }

    @Test
    fun `only the participant names that changed rewrite the key`() {
        seedNames()
        write(mms(1, b))
        read { it.usePrepared("UPDATE short_text SET skey = 'sentinelvalue'") { st -> st.step() } }
        names(normC to "Sanjay")
        assertThat(texts("SELECT skey FROM short_text")).containsExactly("sentinelvalue")
        names(normB to "Meena")
        assertThat(found("meena")).hasSize(1)
    }

    @Test
    fun `participants are not stored for sources without contact names`() {
        write(photo(1).copy(otherNumbers = listOf(b)))
        assertThat(count("item_participant")).isEqualTo(0)
    }
}
