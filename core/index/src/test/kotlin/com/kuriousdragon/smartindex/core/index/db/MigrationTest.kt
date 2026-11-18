package com.kuriousdragon.smartindex.core.index.db

import android.content.Context
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Schema v1 → v2 (SIDX-108): keeps every item row and adds an empty `item_participant`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun schemaFile(version: Int) =
        File("schemas/com.kuriousdragon.smartindex.core.index.db.IndexDatabase/$version.json")

    /** Creates a database file exactly as Room v1 did, from the exported 1.json. */
    private fun createV1(path: String) {
        val db = JSONObject(schemaFile(1).readText()).getJSONObject("database")
        val conn: SQLiteConnection = BundledSQLiteDriver().open(path)
        try {
            val entities = db.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                conn.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val idx = e.optJSONArray("indices") ?: continue
                for (j in 0 until idx.length()) {
                    conn.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = db.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) conn.execSQL(setup.getString(i))
            for (s in FtsSchema.STATEMENTS) conn.execSQL(s)
            conn.execSQL(
                "INSERT INTO item(id, source, source_id, type, title, primary_ts, has_attachment, source_version) " +
                    "VALUES (1, 3, 10, 7, 'one', 5, 0, 1), (2, 4, 11, 7, 'two', 6, 0, 1)",
            )
            conn.execSQL("PRAGMA user_version = 1")
        } finally {
            conn.close()
        }
    }

    private fun IndexDatabase.long(sql: String): Long =
        runBlocking {
            useReaderConnection { c ->
                c.usePrepared(sql) { st ->
                    st.step()
                    st.getLong(0)
                }
            }
        }

    @Test
    fun `v1 to v2 keeps items and adds an empty participant table`() {
        val path = File(tmp.root, "index.db").absolutePath
        createV1(path)
        val db = IndexDatabase.build(ApplicationProvider.getApplicationContext<Context>(), path)
        try {
            assertThat(db.long("SELECT COUNT(*) FROM item")).isEqualTo(2)
            assertThat(db.long("SELECT COUNT(*) FROM item_participant")).isEqualTo(0)
            assertThat(
                db.long("SELECT COUNT(*) FROM sqlite_master WHERE name = 'idx_item_participant_norm'"),
            ).isEqualTo(1)
            assertThat(db.long("PRAGMA user_version")).isEqualTo(2)
            // the cascade works on the migrated table
            runBlocking {
                db.useWriterConnection { c ->
                    c.usePrepared("INSERT INTO item_participant(item_id, number_norm) VALUES (1, '+911')") { it.step() }
                    c.usePrepared("DELETE FROM item WHERE id = 1") { it.step() }
                }
            }
            assertThat(db.long("SELECT COUNT(*) FROM item_participant")).isEqualTo(0)
        } finally {
            db.close()
        }
    }
}
