package com.kuriousdragon.smartindex.source.telephony.testing

import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyProviders

/**
 * In-memory telephony providers: one SQLite database whose tables use the real provider column names.
 * [query] routes by URI and runs the REAL selection, args and sort order, so query strings are tested.
 */
class FakeTelephonyProviders : TelephonyProviders {
    /** The backing database; tests may create more tables and route them with [route]. */
    val db: SQLiteDatabase = SQLiteDatabase.create(null)

    /** Every query received, in order. */
    val queries: MutableList<QuerySpec> = java.util.Collections.synchronizedList(mutableListOf())

    /** `openText` results by `uri.toString()`. */
    val textParts: MutableMap<String, String> = mutableMapOf()

    private val routes = linkedMapOf<String, String>()
    private val cursors = mutableListOf<Cursor>()
    private val faults = mutableListOf<Fault>()
    private val nulls = mutableListOf<String>()

    private class Fault(val prefix: String, val error: Throwable, val afterRows: Int?)

    init {
        db.execSQL(
            "CREATE TABLE calls (_id INTEGER PRIMARY KEY, number TEXT, date INTEGER, duration INTEGER, type INTEGER, presentation INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE sms (_id INTEGER PRIMARY KEY, thread_id INTEGER, address TEXT, date INTEGER, type INTEGER, body TEXT)",
        )
        db.execSQL(
            "CREATE TABLE pdu (_id INTEGER PRIMARY KEY, thread_id INTEGER, date INTEGER, msg_box INTEGER, sub TEXT, sub_cs INTEGER, m_type INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE part (_id INTEGER PRIMARY KEY, mid INTEGER, seq INTEGER, ct TEXT, text TEXT, _data TEXT, name TEXT, chset INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE addr (_id INTEGER PRIMARY KEY, msg_id INTEGER, address TEXT, type INTEGER, charset INTEGER)",
        )
        db.execSQL("CREATE TABLE threads (_id INTEGER PRIMARY KEY, recipient_ids TEXT)")
        db.execSQL("CREATE TABLE canonical_addresses (_id INTEGER PRIMARY KEY, address TEXT)")
        db.execSQL(
            "CREATE TABLE phones (_id INTEGER PRIMARY KEY AUTOINCREMENT, ${Phone.CONTACT_ID} INTEGER, " +
                "${Phone.DISPLAY_NAME} TEXT, ${Phone.NUMBER} TEXT, ${Phone.NORMALIZED_NUMBER} TEXT, " +
                "${Phone.IS_SUPER_PRIMARY} INTEGER DEFAULT 0, starred INTEGER DEFAULT 0)",
        )
        route("call_log/calls", "calls")
        route("sms", "sms")
        route("mms", "pdu")
        route("mms/part", "part")
        route("mms-sms/conversations", "threads")
        route("mms-sms/canonical-addresses", "canonical_addresses")
        route("com.android.contacts/data/phones", "phones")
    }

    /** Routes `content://<key>` (authority + path, no query string) to [table]. */
    fun route(key: String, table: String) {
        routes[key] = table
    }

    fun insert(table: String, values: ContentValues) {
        check(db.insertOrThrow(table, null, values) != -1L)
    }

    /** Makes queries whose `uri.toString()` starts with [uriPrefix] throw [error] (see [afterRows]). */
    fun failOn(uriPrefix: String, error: Throwable, afterRows: Int? = null) {
        faults += Fault(uriPrefix, error, afterRows)
    }

    /** Makes queries whose `uri.toString()` starts with [uriPrefix] return null. */
    fun returnNullFor(uriPrefix: String) {
        nulls += uriPrefix
    }

    /** True when every cursor handed out so far has been closed. */
    fun allCursorsClosed(): Boolean = cursors.all { it.isClosed }

    override fun query(spec: QuerySpec): Cursor? {
        queries += spec
        val uriText = spec.uri.toString()
        if (nulls.any { uriText.startsWith(it) }) return null
        val fault = faults.firstOrNull { uriText.startsWith(it.prefix) }
        if (fault != null && fault.afterRows == null) throw fault.error
        val (table, extra) = resolve(spec.uri)
        val selection = listOfNotNull(extra, spec.selection?.let { "($it)" }).joinToString(" AND ").ifEmpty { null }
        val raw = db.query(
            table,
            spec.projection.toTypedArray(),
            selection,
            spec.args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            spec.sortOrder,
        )
        val cursor = if (fault?.afterRows != null) ThrowingCursor(raw, fault.afterRows, fault.error) else raw
        cursors += cursor
        return cursor
    }

    override fun openText(uri: Uri, maxBytes: Int): String? =
        textParts[uri.toString()]?.toByteArray(Charsets.UTF_8)?.let {
            String(it, 0, minOf(it.size, maxBytes), Charsets.UTF_8)
        }

    private fun resolve(uri: Uri): Pair<String, String?> {
        val key = "${uri.authority.orEmpty()}${uri.path.orEmpty()}"
        routes[key]?.let { return it to null }
        ADDR.matchEntire(key)?.let { return "addr" to "msg_id = ${it.groupValues[1]}" }
        error("FakeTelephonyProviders: no route for $key")
    }

    /** Throws [error] on the call to `moveToNext` that would read row `afterRows + 1`. */
    private class ThrowingCursor(
        c: Cursor,
        private val afterRows: Int,
        private val error: Throwable,
    ) : CursorWrapper(c) {
        private var moves = 0

        override fun moveToNext(): Boolean {
            if (moves++ >= afterRows) throw error
            return super.moveToNext()
        }
    }

    private companion object {
        val ADDR = Regex("""mms/(\d+)/addr""")
    }
}
