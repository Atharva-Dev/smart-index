package com.kuriousdragon.smartindex.core.index.bench

import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Deterministic data for the scale benchmark (LLD §7.4); SIDX-15 can reuse it.
 *
 * Mix of [items] (NFR-002 scaled): 50 % SMS, 25 % media (photos and videos), 10 % calls, 10 % documents,
 * 5 % other files. 15 % of the words are Devanagari. The vocabulary has no "x", no "z" and neither "sea" nor
 * "view", so the planted items below are the only matches for "x", "qz" and "sea view".
 */
class BenchData(val items: Int, private val zone: ZoneId = ZONE) {
    /** Items planted on [PLANT_DAY]; every one must be found by its query. */
    val seaViewSms = 3
    val seaViewDocs = 2

    /** Contact names (number → name) for [numbers]; "Rahul" names are among them. */
    val contactNames: Map<String, String> by lazy {
        val r = Random(SEED + 1)
        numbers.take(CONTACTS).associateWith { NAMES[r.nextInt(NAMES.size)] }
    }

    val numbers: List<String> by lazy {
        val r = Random(SEED + 2)
        List(NUMBERS) { "+91 9${r.nextLong(100_000_000L, 999_999_999L)}" }
    }

    val plantDay: LocalDate = PLANT_DAY
    val rangeEnd: Long = END_MS
    val rangeStart7Days: Long = END_MS - 7 * DAY_MS

    /** All records in deterministic order, as batches of at most [batch]. */
    fun batches(batch: Int = 500): Sequence<List<ItemRecord>> = sequence {
        val r = Random(SEED)
        val out = ArrayList<ItemRecord>(batch)
        for (i in 0 until items) {
            out += generated(i, r)
            if (out.size == batch) {
                yield(out.toList())
                out.clear()
            }
        }
        out += planted()
        yield(out.toList())
    }

    private fun generated(i: Int, r: Random): ItemRecord {
        val id = i + 1L
        val ts = START_MS + r.nextLong(END_MS - START_MS)
        val slot = i % 100
        return when {
            slot < 50 -> sms(id, ts, words(r, 4, 16), numbers[r.nextInt(numbers.size)], r)
            slot < 75 -> media(id, ts, r)
            slot < 85 -> call(id, ts, numbers[r.nextInt(numbers.size)], r)
            slot < 95 -> file(id, ts, ItemType.DOCUMENT, "Documents", listOf("pdf", "doc", "rtf"), r)
            else -> file(id, ts, ItemType.OTHER_FILE, "Download", listOf("apk", "dat", "bin"), r)
        }
    }

    private fun planted(): List<ItemRecord> {
        val noon = PLANT_DAY.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val old = START_MS - DAY_MS
        var id = PLANT_ID
        val list = ArrayList<ItemRecord>()
        repeat(seaViewSms) { n ->
            list += smsRecord(id++, noon + n * 60_000L, "Booked Hotel Sea View for Friday", numbers[n])
        }
        repeat(seaViewDocs) { n ->
            list +=
                fileRecord(
                    id++,
                    noon + n * 60_000L,
                    ItemType.DOCUMENT,
                    "Hotel Sea View booking $n.pdf",
                    "pdf",
                    "Documents",
                )
        }
        list += smsRecord(id++, noon + 10 * 60_000L, "होटल की बुकिंग हो गई", numbers[0])
        // The oldest items of all, so the short-query scans (newest first) must run to the end of the index.
        list += fileRecord(id++, old, ItemType.DOCUMENT, "qz plan.pdf", "pdf", "Documents")
        list += fileRecord(id, old - 1, ItemType.DOCUMENT, "x ray.pdf", "pdf", "Documents")
        return list
    }

    private fun words(r: Random, min: Int, max: Int): String =
        (0 until r.nextInt(min, max + 1)).joinToString(" ") {
            if (r.nextInt(100) < DEVANAGARI_PERCENT) HINDI[r.nextInt(HINDI.size)] else ENGLISH[r.nextInt(ENGLISH.size)]
        }

    private fun sms(id: Long, ts: Long, body: String, number: String, r: Random) =
        smsRecord(id, ts, body, number, if (r.nextBoolean()) Direction.RECEIVED else Direction.SENT)

    private fun smsRecord(
        id: Long,
        ts: Long,
        body: String,
        number: String,
        direction: Direction = Direction.RECEIVED,
    ) = ItemRecord(
        key = SourceKey(Source.SMS, id),
        type = ItemType.MESSAGE,
        title = number,
        primaryTs = ts,
        dates = mapOf(DateKind.EVENT to ts),
        numberRaw = number,
        direction = direction,
        msgKind = MsgKind.SMS,
        threadId = id % 5_000,
        body = body,
        sourceVersion = ts,
    )

    private fun call(id: Long, ts: Long, number: String, r: Random) =
        ItemRecord(
            key = SourceKey(Source.CALL_LOG, id),
            type = ItemType.CALL,
            title = number,
            primaryTs = ts,
            dates = mapOf(DateKind.EVENT to ts),
            numberRaw = number,
            direction = Direction.entries[r.nextInt(5)],
            durationMs = r.nextLong(0, 3_600_000),
            sourceVersion = ts,
        )

    private fun media(id: Long, ts: Long, r: Random): ItemRecord {
        val video = r.nextInt(10) == 0
        val name = (if (video) "VID_" else "IMG_") + id + (if (video) ".mp4" else ".jpg")
        return ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = if (video) ItemType.VIDEO else ItemType.PHOTO,
            title = name,
            primaryTs = ts,
            dates = mapOf(DateKind.TAKEN to ts, DateKind.ADDED to ts + r.nextLong(0, 3_600_000)),
            folder = if (video) "Movies" else "DCIM/Camera",
            ext = if (video) "mp4" else "jpg",
            sizeBytes = r.nextLong(100_000, 8_000_000),
            sourceVersion = ts,
        )
    }

    private fun file(id: Long, ts: Long, type: ItemType, folder: String, exts: List<String>, r: Random): ItemRecord {
        val ext = exts[r.nextInt(exts.size)]
        return fileRecord(id, ts, type, words(r, 1, 3) + " " + id + "." + ext, ext, folder)
    }

    private fun fileRecord(id: Long, ts: Long, type: ItemType, title: String, ext: String, folder: String) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = type,
            title = title,
            primaryTs = ts,
            dates = mapOf(DateKind.ADDED to ts, DateKind.MODIFIED to ts + 1_000),
            folder = folder,
            ext = ext,
            sizeBytes = 50_000,
            sourceVersion = ts,
        )

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        const val SEED = 42
        const val DAY_MS = 86_400_000L
        const val END_MS = 1_780_000_000_000L // 2026-05-28
        const val START_MS = END_MS - 5 * 365 * DAY_MS
        val PLANT_DAY: LocalDate = LocalDate.of(2026, 3, 2)
        const val PLANT_ID = 10_000_000L
        private const val NUMBERS = 2_000
        private const val CONTACTS = 800
        private const val DEVANAGARI_PERCENT = 15

        private val NAMES = listOf(
            "Rahul Sharma", "Priya Singh", "Amit Patel", "Sneha Rao", "Vikram Mehta", "Anita Desai", "Karan Joshi",
            "Pooja Nair", "Rohan Gupta", "Neha Kapoor", "राहुल शर्मा", "Rahul Verma",
        )
        private val ENGLISH = listOf(
            "payment", "payment", "the", "the", "the", "and", "your", "account", "credited", "debited", "balance",
            "order", "delivered", "meeting", "tomorrow", "lunch", "dinner", "call", "me", "back", "please", "thanks",
            "happy", "birthday", "photo", "trip", "family", "invoice", "bill", "recharge", "offer", "bank", "card",
            "statement", "report", "project", "draft", "ticket", "flight", "train", "station", "hotel", "room",
            "booking", "confirmed", "rahul", "otp", "code", "valid", "minutes", "ready", "pickup", "home", "office",
            "weekend", "movie", "music", "doctor", "appointment", "reminder", "rent", "salary", "good", "morning",
        )
        private val HINDI = listOf(
            "होटल", "भुगतान", "धन्यवाद", "कृपया", "खाता", "बैंक", "संदेश", "परिवार", "यात्रा", "शुभकामनाएं", "टिकट",
            "दोपहर", "शाम", "कल", "मिलते", "हैं", "आपका", "ऑर्डर", "पहुंच", "गया",
        )
    }
}
