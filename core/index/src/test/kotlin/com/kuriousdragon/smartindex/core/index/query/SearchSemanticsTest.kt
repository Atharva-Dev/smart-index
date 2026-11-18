package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.index.db.dao.KeyRow
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** FR-008 end to end through `RoomIndexQuery.search` (LLD §7.3, QA Scope §3.1). */
class SearchSemanticsTest : IndexDbTest() {
    private fun write(vararg r: ItemRecord) = runBlocking { writer.upsert(r.toList()) }

    private fun find(text: String, type: ItemType? = null): List<ResultItem> =
        query.search(SearchQuery(text, null, type)).all()

    private fun titles(text: String) = find(text).map { it.title }

    private fun file(id: Long, title: String, folder: String? = "Download", type: ItemType = ItemType.DOCUMENT) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = type,
            title = title,
            primaryTs = 1_000 + id,
            dates = mapOf(DateKind.ADDED to 1_000L + id),
            folder = folder,
            ext = title.substringAfterLast('.', ""),
            sourceVersion = 1,
        )

    @Test
    fun `sea view finds a message with the snippet range on Sea View`() {
        write(sms(1, body = "Hotel Sea View"))
        val hit = find("sea view").single()
        assertThat(hit.snippet).isEqualTo("Hotel Sea View")
        assertThat(hit.matches[Field.SNIPPET]).containsExactly(6..13)
        assertThat(hit.type).isEqualTo(ItemType.MESSAGE)
    }

    @Test
    fun `case and surrounding spaces do not matter`() {
        write(sms(1, body = "Hotel Sea View"))
        val expected = find("sea view").map { it.id }
        assertThat(expected).hasSize(1)
        assertThat(find("SEA VIEW").map { it.id }).isEqualTo(expected)
        assertThat(find("  sea view  ").map { it.id }).isEqualTo(expected)
    }

    @Test
    fun `view matches inside preview`() {
        write(file(1, "preview.jpg", type = ItemType.PHOTO))
        val hit = find("view").single()
        assertThat(hit.matches[Field.TITLE]).containsExactly(3..6)
    }

    @Test
    fun `sea view does not match joined or underscored names`() {
        write(file(1, "Sea_View.pdf"), file(2, "SeaView.jpg", type = ItemType.PHOTO))
        assertThat(find("sea view")).isEmpty()
    }

    @Test
    fun `seaview and sea_view do not match the spaced text`() {
        write(sms(1, body = "Hotel Sea View"))
        assertThat(find("seaview")).isEmpty()
        assertThat(find("sea_view")).isEmpty()
    }

    @Test
    fun `booking finds a document named Hotel booking pdf`() {
        write(file(1, "Hotel booking.pdf"))
        assertThat(titles("booking")).containsExactly("Hotel booking.pdf")
    }

    @Test
    fun `booking reference does not find a document whose only occurrence was in its body`() {
        write(doc(1, title = "report.pdf", body = "booking reference ABC123"))
        assertThat(find("booking reference")).isEmpty()
        assertThat(find("abc123")).isEmpty()
        assertThat(titles("report")).containsExactly("report.pdf")
    }

    @Test
    fun `a contact name finds all of that contacts calls and messages once names are set`() {
        write(call(1), call(2), sms(1, body = "hello"), sms(2, number = "+91 11111 22222", body = "other"))
        runBlocking { writer.setContactNames(mapOf("+919876543210" to "Rahul")) }
        val hits = find("rahul")
        assertThat(hits.map { it.type }).containsExactly(ItemType.CALL, ItemType.CALL, ItemType.MESSAGE)
        assertThat(hits.all { it.contactName == "Rahul" }).isTrue()
        assertThat(hits.all { it.matches[Field.CONTACT_NAME] == listOf(0..4) }).isTrue()
    }

    @Test
    fun `digit queries find a stored number and highlight it`() {
        write(call(1, number = "+91 98765 43210"))
        assertThat(find("98765").single().matches[Field.NUMBER]).containsExactly(4..8)
        val full = find("9876543210").single()
        assertThat(full.matches[Field.NUMBER]).containsExactly(4..14)
        assertThat(full.number).isEqualTo("+91 98765 43210")
    }

    @Test
    fun `Devanagari text matches with the correct range`() {
        write(sms(1, body = "आपका होटल बुक हो गया"))
        val hit = find("होटल").single()
        assertThat(hit.snippet).isEqualTo("आपका होटल बुक हो गया")
        val start = "आपका होटल बुक हो गया".indexOf("होटल")
        assertThat(hit.matches[Field.SNIPPET]).containsExactly(start until start + "होटल".length)
    }

    @Test
    fun `folding makes umlaut and decomposed forms match`() {
        write(file(1, "Ölaf.txt"), file(2, "Zoë.txt"))
        assertThat(titles("ölaf")).containsExactly("Ölaf.txt")
        assertThat(titles("ÖLAF")).containsExactly("Ölaf.txt")
        assertThat(titles("zoë")).containsExactly("Zoë.txt")
    }

    @Test
    fun `ra finds one item per field it appears in`() {
        write(
            file(1, "ra.pdf", folder = "Misc"),
            file(2, "plain.pdf", folder = "Radio"),
            file(3, "plain2.pdf", folder = "Misc").copy(ext = "ra"),
            sms(1, number = "+91 11111 22222", body = "say ra now"),
            call(1, number = "+91 98765 43210"),
        )
        runBlocking { writer.setContactNames(mapOf("+919876543210" to "Rahul")) }
        val hits = find("ra")
        assertThat(hits).hasSize(5)
        assertThat(hits.map { it.id }.toSet()).hasSize(5)
        val message = hits.single { it.type == ItemType.MESSAGE }
        assertThat(message.matches[Field.SNIPPET]).containsExactly(4..5)
    }

    @Test
    fun `a single character query works`() {
        write(file(1, "x-ray.jpg", type = ItemType.PHOTO), file(2, "abc.jpg", type = ItemType.PHOTO))
        assertThat(titles("x")).containsExactly("x-ray.jpg")
    }

    @Test
    fun `special characters are literal and never throw`() {
        write(
            sms(1, body = "say \"hi\" to *me*"),
            sms(2, body = "100% sure_thing (maybe)"),
            sms(3, body = "a\"b NEAR( AND OR"),
            sms(4, body = "plain text"),
        )
        val ids = (1L..4L).map { idOf(Source.SMS, it) }
        fun only(text: String, vararg expected: Int) {
            assertThat(find(text).map { it.id }).containsExactlyElementsIn(expected.map { ids[it] })
        }
        only("\"", 0, 2)
        only("*", 0)
        only("%", 1)
        only("_", 1)
        only("(", 1, 2)
        only("a\"b", 2)
        only("NEAR(", 2)
        only("AND", 2)
        only("\"hi\"", 0)
        only("*me*", 0)
        only("sure_thing", 1)
        only("100%", 1)
    }

    @Test
    fun `the type filter narrows results`() {
        write(file(1, "sea view.jpg", type = ItemType.PHOTO), sms(1, body = "Hotel Sea View"))
        assertThat(find("sea view")).hasSize(2)
        assertThat(find("sea view", ItemType.PHOTO).map { it.type }).containsExactly(ItemType.PHOTO)
        assertThat(find("sea view", ItemType.CALL)).isEmpty()
    }

    @Test
    fun `a blank query without a range is empty`() {
        write(sms(1))
        assertThat(find("")).isEmpty()
        assertThat(find("   ")).isEmpty()
    }

    @Test
    fun `FTS and instr agree on 2000 seeded items and 200 random substrings`() {
        val rnd = Random(7)
        val alphabet = "abcdeab ghé_-.ह"
        val words = List(300) { String(CharArray(rnd.nextInt(3, 9)) { alphabet[rnd.nextInt(alphabet.length)] }) }
        val records =
            (1L..2_000L).map { id ->
                val title = (1..rnd.nextInt(1, 4)).joinToString(" ") { words[rnd.nextInt(words.size)] }
                if (id % 2 == 0L) {
                    sms(id, body = title, number = null)
                } else {
                    file(id, "$title.pdf", folder = words[rnd.nextInt(words.size)])
                }
            }
        write(*records.toTypedArray())
        val dao = db.searchDao()
        repeat(200) {
            val src = words[rnd.nextInt(words.size)]
            val len = rnd.nextInt(3, minOf(src.length, 6) + 1)
            val start = rnd.nextInt(0, src.length - len + 1)
            val q = src.substring(start, start + len).trim()
            if (q.codePointCount(0, q.length) < 3) return@repeat
            val folded = com.kuriousdragon.smartindex.core.index.text.TextFolder.fold(q)
            val viaFts = runBlocking { dao.keys(SearchSql.page(SearchPlan.of(SearchQuery(q, null, null)), 5_000, 0)) }
            val viaInstr =
                runBlocking {
                    dao.keys(SearchSql.page(SearchPlan.Short(folded, null, null), 5_000, 0))
                }
            assertThat(viaFts.map(KeyRow::id).toSet()).isEqualTo(viaInstr.map(KeyRow::id).toSet())
        }
    }
}
