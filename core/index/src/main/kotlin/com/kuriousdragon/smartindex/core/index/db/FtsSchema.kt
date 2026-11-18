package com.kuriousdragon.smartindex.core.index.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Raw DDL Room does not manage: the FTS5 trigram table and the derived-row triggers (LLD §4.2). */
object FtsSchema {
    val STATEMENTS: List<String> =
        listOf(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS fts_short USING fts5(
              skey, content='', contentless_delete=1, tokenize='trigram case_sensitive 1')
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS short_text_ai AFTER INSERT ON short_text BEGIN
              INSERT INTO fts_short(rowid, skey) VALUES (new.id, new.skey);
            END
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS short_text_ad AFTER DELETE ON short_text BEGIN
              DELETE FROM fts_short WHERE rowid = old.id;
            END
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS short_text_au AFTER UPDATE OF skey ON short_text BEGIN
              DELETE FROM fts_short WHERE rowid = old.id;
              INSERT INTO fts_short(rowid, skey) VALUES (new.id, new.skey);
            END
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS item_ad AFTER DELETE ON item BEGIN
              DELETE FROM item_key   WHERE id = old.id;
              DELETE FROM item_date  WHERE item_id = old.id;
              DELETE FROM short_text WHERE id = old.id;
              DELETE FROM recording_link WHERE call_item_id = old.id OR recording_item_id = old.id;
            END
            """.trimIndent(),
        )

    private fun run(connection: SQLiteConnection) = STATEMENTS.forEach { connection.execSQL(it) }

    /** Every statement is idempotent, so running on create, open and destructive migration is safe. */
    object Callback : RoomDatabase.Callback() {
        override fun onCreate(connection: SQLiteConnection) = run(connection)

        override fun onOpen(connection: SQLiteConnection) = run(connection)

        override fun onDestructiveMigration(connection: SQLiteConnection) = run(connection)
    }
}
