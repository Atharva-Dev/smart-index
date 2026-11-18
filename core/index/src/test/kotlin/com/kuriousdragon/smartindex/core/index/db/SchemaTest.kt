package com.kuriousdragon.smartindex.core.index.db

import android.content.Context
import androidx.room.Transactor
import androidx.room.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaTest {
    private lateinit var db: IndexDatabase

    @Before
    fun setUp() {
        db = IndexDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = db.close()

    private fun <T> write(block: suspend (Transactor) -> T): T = runBlocking { db.useWriterConnection { block(it) } }

    private suspend fun Transactor.exec(sql: String) = usePrepared(sql) { it.step() }

    private suspend fun Transactor.longs(sql: String): List<Long> =
        usePrepared(sql) { st -> buildList { while (st.step()) add(st.getLong(0)) } }

    private suspend fun Transactor.text(sql: String): String = usePrepared(sql) { st ->
        if (st.step()) st.getText(0) else ""
    }

    private suspend fun Transactor.names(sql: String): Set<String> =
        usePrepared(sql) { st -> buildSet { while (st.step()) add(st.getText(0)) } }

    private suspend fun Transactor.insertShort(
        id: Long,
        skey: String,
    ) = exec("INSERT INTO short_text(id, ts, type, skey) VALUES ($id, 1, 5, '$skey')")

    private suspend fun Transactor.match(term: String): List<Long> =
        longs("SELECT rowid FROM fts_short WHERE fts_short MATCH '\"$term\"'")

    @Test
    fun `sqlite is new enough and has FTS5`() {
        write { c ->
            val version = c.text("SELECT sqlite_version()").split('.').map { it.toInt() }
            val ok = version[0] > 3 || (version[0] == 3 && version[1] >= 43)
            assertThat(ok).isTrue()
            assertThat(c.names("SELECT compile_options FROM pragma_compile_options")).contains("ENABLE_FTS5")
        }
    }

    @Test
    fun `fts table and the four triggers exist`() {
        write { c ->
            assertThat(c.names("SELECT name FROM sqlite_master WHERE type='table'")).contains("fts_short")
            assertThat(c.names("SELECT name FROM sqlite_master WHERE type='trigger'"))
                .containsAtLeast("short_text_ai", "short_text_ad", "short_text_au", "item_ad")
        }
    }

    @Test
    fun `inserting a short_text row makes it findable`() {
        write { c ->
            c.insertShort(1, "hello world")
            assertThat(c.match("hello wor")).containsExactly(1L)
            assertThat(c.match("zzzzz")).isEmpty()
        }
    }

    @Test
    fun `updating skey replaces the indexed term`() {
        write { c ->
            c.insertShort(1, "alphabet")
            c.exec("UPDATE short_text SET skey = 'zebra crossing' WHERE id = 1")
            assertThat(c.match("alpha")).isEmpty()
            assertThat(c.match("zebra")).containsExactly(1L)
        }
    }

    @Test
    fun `deleting an item removes all derived rows`() {
        write { c ->
            c.exec(
                "INSERT INTO item(id, source, source_id, type, title, primary_ts, has_attachment, source_version) " +
                    "VALUES (1, 2, 10, 5, 't', 1, 0, 1)",
            )
            c.exec("INSERT INTO item_key(id, type, ts) VALUES (1, 5, 1)")
            c.exec("INSERT INTO item_date(item_id, kind, type, ts) VALUES (1, 4, 5, 1)")
            c.insertShort(1, "findable")
            c.exec(
                "INSERT INTO recording_link(call_item_id, recording_item_id, score, method) VALUES (1, 2, 1.0, 'm')",
            )
            c.exec(
                "INSERT INTO recording_link(call_item_id, recording_item_id, score, method) VALUES (3, 1, 1.0, 'm')",
            )
            assertThat(c.match("findable")).containsExactly(1L)

            c.exec("DELETE FROM item WHERE id = 1")

            assertThat(c.longs("SELECT COUNT(*) FROM item_key")).containsExactly(0L)
            assertThat(c.longs("SELECT COUNT(*) FROM item_date")).containsExactly(0L)
            assertThat(c.longs("SELECT COUNT(*) FROM short_text")).containsExactly(0L)
            assertThat(c.longs("SELECT COUNT(*) FROM recording_link")).containsExactly(0L)
            assertThat(c.match("findable")).isEmpty()
        }
    }
}
