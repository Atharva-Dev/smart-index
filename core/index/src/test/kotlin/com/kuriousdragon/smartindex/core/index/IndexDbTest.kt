package com.kuriousdragon.smartindex.core.index

import android.content.Context
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import androidx.room.Transactor
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.debug.StorageReporter
import com.kuriousdragon.smartindex.core.index.query.IndexChangeSignal
import com.kuriousdragon.smartindex.core.index.query.RoomIndexQuery
import com.kuriousdragon.smartindex.core.index.query.TimeZoneSource
import com.kuriousdragon.smartindex.core.index.write.RoomIndexWriter
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.E164Formatter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deterministic fake E.164 formatter (LLD §7): no libphonenumber dependency in tests. */
object TestE164 : E164Formatter {
    override fun formatE164(number: String, countryIso: String): String? {
        val t = number.trim()
        val digits = t.filter { it in '0'..'9' }
        return when {
            t.startsWith("+") -> "+$digits"
            digits.startsWith("0") -> "+91" + digits.drop(1)
            digits.length == 10 -> "+91$digits"
            else -> null
        }
    }
}

/** Wraps the real normaliser and counts calls per raw number. */
class CountingNormalizer(private val inner: PhoneNumberNormalizer) : PhoneNumberNormalizer {
    val normalizeCalls = HashMap<String?, Int>()

    override fun normalize(raw: String?): String? {
        normalizeCalls.merge(raw, 1, Int::plus)
        return inner.normalize(raw)
    }

    override fun digitsOnly(raw: String?): String? = inner.digitsOnly(raw)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class IndexDbTest {
    protected lateinit var db: IndexDatabase
    protected lateinit var numbers: CountingNormalizer
    protected lateinit var writer: RoomIndexWriter
    protected lateinit var query: RoomIndexQuery

    /** The zone the query side reads on every call; tests may reassign it. */
    protected var zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Before
    fun openDb() {
        db = IndexDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        numbers = CountingNormalizer(DefaultPhoneNumberNormalizer(country = { "IN" }, e164 = TestE164))
        writer = RoomIndexWriter(db, numbers, StorageReporter(db))
        query = RoomIndexQuery(db, IndexChangeSignal(db), TimeZoneSource { zone })
    }

    @After
    fun closeDb() = db.close()

    /** Every item of a paged flow (first refresh, then appends until the end). */
    protected fun <T : Any> Flow<PagingData<T>>.all(): List<T> = runBlocking {
        asSnapshot { appendScrollWhile { true } }
    }

    /** Epoch ms of local time on [day] in the current [zone]. */
    protected fun at(day: LocalDate, hour: Int = 12, minute: Int = 0, second: Int = 0, milli: Int = 0): Long =
        day.atTime(hour, minute, second, milli * 1_000_000).atZone(zone).toInstant().toEpochMilli()

    protected fun smsAt(id: Long, ts: Long, body: String = "Hotel Sea View", number: String? = "+91 98765 43210") =
        sms(id, number = number, body = body).copy(primaryTs = ts, dates = mapOf(DateKind.EVENT to ts))

    protected fun <T> read(block: suspend (Transactor) -> T): T = runBlocking { db.useReaderConnection { block(it) } }

    protected fun longs(sql: String): List<Long> =
        read { c -> c.usePrepared(sql) { st -> buildList { while (st.step()) add(st.getLong(0)) } } }

    protected fun count(table: String): Long = longs("SELECT COUNT(*) FROM $table").single()

    protected fun texts(sql: String): List<String?> =
        read { c ->
            c.usePrepared(sql) { st ->
                buildList { while (st.step()) add(if (st.isNull(0)) null else st.getText(0)) }
            }
        }

    protected fun match(term: String): List<Long> =
        longs("SELECT rowid FROM fts_short WHERE fts_short MATCH '\"${term.replace("'", "''")}\"' ORDER BY rowid")

    protected fun idOf(source: Source, sourceId: Long): Long =
        longs("SELECT id FROM item WHERE source = ${source.ordinal + 1} AND source_id = $sourceId").single()

    protected fun photo(
        id: Long,
        title: String = "IMG_$id.jpg",
        version: Long = 1,
        taken: Long? = 1_000,
        added: Long? = 2_000,
    ) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = ItemType.PHOTO,
            title = title,
            primaryTs = taken ?: added ?: 0,
            dates = listOfNotNull(taken?.let { DateKind.TAKEN to it }, added?.let { DateKind.ADDED to it }).toMap(),
            folder = "DCIM/Camera",
            ext = "jpg",
            sourceVersion = version,
        )

    protected fun doc(id: Long, title: String = "report_$id.pdf", version: Long = 1, body: String? = "secret body") =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id), type = ItemType.DOCUMENT, title = title, primaryTs = 5_000,
            dates = mapOf(DateKind.ADDED to 5_000L, DateKind.MODIFIED to 6_000L),
            folder = "Download", ext = "pdf", body = body, sourceVersion = version,
        )

    protected fun sms(
        id: Long,
        number: String? = "+91 98765 43210",
        body: String = "Hotel Sea View",
        version: Long = 1,
    ) =
        ItemRecord(
            key = SourceKey(Source.SMS, id), type = ItemType.MESSAGE, title = number ?: "Unknown",
            primaryTs =
                10_000 + id,
            dates = mapOf(DateKind.EVENT to 10_000L + id), numberRaw = number, direction = Direction.RECEIVED,
            msgKind = MsgKind.SMS, threadId = 7, body = body, sourceVersion = version,
        )

    protected fun call(id: Long, number: String? = "+91 98765 43210", version: Long = 1) =
        ItemRecord(
            key = SourceKey(Source.CALL_LOG, id), type = ItemType.CALL, title = number ?: "Unknown",
            primaryTs =
                20_000 + id,
            dates = mapOf(DateKind.EVENT to 20_000L + id), numberRaw = number, direction = Direction.INCOMING,
            durationMs = 30_000, sourceVersion = version,
        )

    protected fun recording(id: Long, version: Long = 1) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = ItemType.RECORDING,
            title = "Call recording $id.m4a",
            primaryTs = 30_000,
            dates = mapOf(DateKind.EVENT to 30_000L, DateKind.MODIFIED to 31_000L),
            ext = "m4a",
            sourceVersion = version,
        )
}
