package com.kuriousdragon.smartindex.source.telephony.common

import android.database.Cursor

/**
 * Tolerant typed reads over a [Cursor] positioned on a row; column indices are cached per cursor.
 * Provider rows are untrusted: a missing column, SQL NULL or an odd value type gives null, never an exception.
 */
class CursorReader(private val cursor: Cursor) {
    private val indices = HashMap<String, Int>()

    private fun index(column: String): Int = indices.getOrPut(column) { cursor.getColumnIndex(column) }

    /** Missing column, NULL, BLOB or non-numeric string → null; numeric string → parsed. */
    fun long(column: String): Long? {
        val i = index(column)
        if (i < 0) return null
        return when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i).takeIf { it.isFinite() }?.toLong()
            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)?.trim()?.toLongOrNull()
            else -> null
        }
    }

    /** Same rules as [long]; a value outside the `Int` range → null. */
    fun int(column: String): Int? = long(column)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** Missing column or NULL → null; numbers → their decimal text; BLOB → null. */
    fun string(column: String): String? {
        val i = index(column)
        if (i < 0) return null
        return when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i).toString()
            else -> null
        }
    }
}
