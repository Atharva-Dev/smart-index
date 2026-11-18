package com.kuriousdragon.smartindex.source.telephony.common

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CursorReaderTest {
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE t (_id INTEGER, num INTEGER, txt TEXT, real REAL, blob BLOB)")
    }

    @After
    fun tearDown() = db.close()

    private fun <T> read(sql: String, block: (CursorReader) -> T): T =
        db.rawQuery(sql, null).use { c ->
            check(c.moveToFirst())
            block(CursorReader(c))
        }

    @Test
    fun `a missing column reads as null`() {
        db.execSQL("INSERT INTO t (_id, num) VALUES (1, 5)")
        read("SELECT * FROM t") { r ->
            assertThat(r.long("nope")).isNull()
            assertThat(r.int("nope")).isNull()
            assertThat(r.string("nope")).isNull()
        }
    }

    @Test
    fun `SQL NULL reads as null`() {
        db.execSQL("INSERT INTO t (_id) VALUES (1)")
        read("SELECT * FROM t") { r ->
            assertThat(r.long("num")).isNull()
            assertThat(r.int("num")).isNull()
            assertThat(r.string("txt")).isNull()
        }
    }

    @Test
    fun `a numeric string in a long column is parsed`() {
        db.execSQL("INSERT INTO t (_id, txt) VALUES (1, ' 1700000000000 ')")
        read("SELECT * FROM t") { r ->
            assertThat(r.long("txt")).isEqualTo(1_700_000_000_000L)
            assertThat(r.int("txt")).isNull() // out of Int range
        }
    }

    @Test
    fun `a non-numeric string reads as null for numbers and is kept as a string`() {
        db.execSQL("INSERT INTO t (_id, txt) VALUES (1, 'abc')")
        read("SELECT * FROM t") { r ->
            assertThat(r.long("txt")).isNull()
            assertThat(r.int("txt")).isNull()
            assertThat(r.string("txt")).isEqualTo("abc")
        }
    }

    @Test
    fun `a long read as a string gives its decimal text`() {
        db.execSQL("INSERT INTO t (_id, num) VALUES (1, 42)")
        read("SELECT * FROM t") { r ->
            assertThat(r.string("num")).isEqualTo("42")
            assertThat(r.long("num")).isEqualTo(42L)
            assertThat(r.int("num")).isEqualTo(42)
        }
    }

    @Test
    fun `a float is truncated for numbers and a blob reads as null`() {
        db.execSQL("INSERT INTO t (_id, real, blob) VALUES (1, 7.9, x'0102')")
        read("SELECT * FROM t") { r ->
            assertThat(r.long("real")).isEqualTo(7L)
            assertThat(r.long("blob")).isNull()
            assertThat(r.string("blob")).isNull()
        }
    }
}
