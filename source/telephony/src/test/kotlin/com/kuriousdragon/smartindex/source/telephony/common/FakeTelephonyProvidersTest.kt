package com.kuriousdragon.smartindex.source.telephony.common

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addAddr
import com.kuriousdragon.smartindex.source.telephony.testing.addCall
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FakeTelephonyProvidersTest {
    private val fake = FakeTelephonyProviders()
    private val calls = Uri.parse("content://call_log/calls")

    private fun ids(spec: QuerySpec): List<Long> = fake.query(spec)!!.use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }

    @Test
    fun `a selection with bound arguments and the sort order are executed by SQLite`() {
        fake.addCall(id = 3, type = 1)
        fake.addCall(id = 1, type = 4)
        fake.addCall(id = 2, type = 2)
        fake.addCall(id = 5, type = 1)
        val spec = QuerySpec(calls, listOf("_id"), "type <> 4 AND _id > ?", listOf("2"), "_id DESC")
        assertThat(ids(spec)).containsExactly(5L, 3L).inOrder()
        assertThat(fake.queries).containsExactly(spec)
    }

    @Test
    fun `a projection with an unknown column fails like a real provider`() {
        assertThrows(Exception::class.java) { fake.query(QuerySpec(calls, listOf("nope"))) }
    }

    @Test
    fun `failOn with afterRows throws on row n plus one`() {
        repeat(5) { fake.addCall(id = it + 1L) }
        fake.failOn("content://call_log", IllegalStateException("boom"), afterRows = 3)
        val cursor = fake.query(QuerySpec(calls, listOf("_id"), sortOrder = "_id ASC"))!!
        assertThat(cursor.moveToNext()).isTrue()
        assertThat(cursor.moveToNext()).isTrue()
        assertThat(cursor.moveToNext()).isTrue()
        assertThrows(IllegalStateException::class.java) { cursor.moveToNext() }
    }

    @Test
    fun `failOn without afterRows throws from query and returnNullFor returns null`() {
        fake.failOn("content://call_log", SecurityException())
        assertThrows(SecurityException::class.java) { fake.query(QuerySpec(calls, listOf("_id"))) }
        fake.returnNullFor("content://sms")
        assertThat(fake.query(QuerySpec(Uri.parse("content://sms"), listOf("_id")))).isNull()
    }

    @Test
    fun `the per-message addr URI adds the message id to the selection`() {
        fake.addAddr(id = 1, messageId = 10, address = "+911", type = 137)
        fake.addAddr(id = 2, messageId = 11, address = "+912", type = 137)
        val spec = QuerySpec(Uri.parse("content://mms/10/addr"), listOf("_id"), "type = ?", listOf("137"))
        assertThat(ids(spec)).containsExactly(1L)
    }

    @Test
    fun `cursors are tracked so tests can assert they were closed`() {
        fake.addCall(id = 1)
        val cursor = fake.query(QuerySpec(calls, listOf("_id")))!!
        assertThat(fake.allCursorsClosed()).isFalse()
        cursor.close()
        assertThat(fake.allCursorsClosed()).isTrue()
    }

    @Test
    fun `openText returns at most maxBytes of the configured text`() {
        fake.textParts["content://mms/part/9"] = "abcdef"
        assertThat(fake.openText(Uri.parse("content://mms/part/9"), 3)).isEqualTo("abc")
        assertThat(fake.openText(Uri.parse("content://mms/part/8"), 3)).isNull()
    }
}
