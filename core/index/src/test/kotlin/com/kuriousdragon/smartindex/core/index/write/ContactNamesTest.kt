package com.kuriousdragon.smartindex.core.index.write

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ContactNamesTest : IndexDbTest() {
    private val norm = "+919876543210"

    private fun seed() =
        runBlocking {
            writer.upsert(
                listOf(
                    call(1, "+91 98765 43210"),
                    sms(1, "098765 43210"),
                    sms(2, "+91 11111 22222"),
                    photo(1),
                ),
            )
        }

    private fun names(vararg p: Pair<String, String?>) = runBlocking { writer.setContactNames(mapOf(*p)) }

    @Test
    fun `a name makes calls and messages findable under both number forms`() {
        seed()
        names(norm to "Rahul")
        assertThat(match("rahul")).containsExactly(idOf(Source.CALL_LOG, 1), idOf(Source.SMS, 1)).inOrder()
        assertThat(texts("SELECT contact_name FROM item WHERE source IN (2,3) AND number_norm = '$norm'"))
            .containsExactly("Rahul", "Rahul")
        assertThat(texts("SELECT contact_name FROM item WHERE id = ${idOf(Source.SMS, 2)}")).containsExactly(null)
        assertThat(texts("SELECT name FROM contact_name")).containsExactly("Rahul")
    }

    @Test
    fun `a changed name replaces the old term`() {
        seed()
        names(norm to "Rahul")
        names(norm to "Rohan")
        assertThat(match("rahul")).isEmpty()
        assertThat(match("rohan")).hasSize(2)
        assertThat(texts("SELECT name FROM contact_name")).containsExactly("Rohan")
    }

    @Test
    fun `a null value removes the name and search stops matching`() {
        seed()
        names(norm to "Rahul")
        names(norm to null)
        assertThat(match("rahul")).isEmpty()
        assertThat(count("contact_name")).isEqualTo(0)
        assertThat(texts("SELECT contact_name FROM item WHERE source IN (2,3) AND number_norm = '$norm'"))
            .containsExactly(null, null)
    }

    @Test
    fun `keys absent from the map are untouched`() {
        seed()
        names(norm to "Rahul", "+911111122222" to "Meera")
        names("+919999900000" to "Zed")
        assertThat(match("rahul")).hasSize(2)
        assertThat(match("meera")).hasSize(1)
        assertThat(count("contact_name")).isEqualTo(3)
        names(norm to null)
        assertThat(match("meera")).hasSize(1)
    }

    @Test
    fun `blank keys are ignored`() {
        seed()
        names("" to "Nobody", "   " to "Nobody")
        assertThat(count("contact_name")).isEqualTo(0)
        assertThat(match("nobody")).isEmpty()
    }

    @Test
    fun `unchanged names do not rewrite items`() {
        seed()
        names(norm to "Rahul")
        read { it.usePrepared("UPDATE short_text SET skey = 'sentinelvalue'") { st -> st.step() } }
        names(norm to "Rahul")
        assertThat(texts("SELECT skey FROM short_text WHERE id = ${idOf(Source.SMS, 1)}"))
            .containsExactly("sentinelvalue")
        names(norm to "Rahul", "+911111122222" to "Meera")
        assertThat(texts("SELECT skey FROM short_text WHERE id = ${idOf(Source.CALL_LOG, 1)}"))
            .containsExactly("sentinelvalue")
        assertThat(match("meera")).hasSize(1)
    }

    @Test
    fun `names set before the items exist are applied at upsert and later changes re-denormalise`() {
        names(norm to "Rahul")
        seed()
        assertThat(match("rahul")).hasSize(2)
        names(norm to "Rohan")
        assertThat(match("rohan")).hasSize(2)
    }

    @Test
    fun `media items never get a contact name`() {
        seed()
        names(norm to "Rahul")
        assertThat(texts("SELECT contact_name FROM item WHERE source = 1")).containsExactly(null)
    }
}
