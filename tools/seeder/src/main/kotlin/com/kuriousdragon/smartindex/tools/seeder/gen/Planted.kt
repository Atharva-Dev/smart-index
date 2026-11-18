package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDateTime
import java.time.LocalTime

/** Test Plan §3 planted items P1–P17 (LLD §4.4), the §4.8 edge additions and the §4.6 tricky recordings. */
internal object Planted {
    fun build(ctx: GenContext) {
        val b = PlantedBuilder(ctx)
        b.p1ToP2()
        b.p3ToP6()
        b.p7ToP9()
        b.p10ToP11()
        b.p12()
        b.p13()
        b.p14()
        b.p15ToP17()
        RecordingPlan.tricky(b)
        if (ctx.profile == Profile.EDGE) b.edgeCases()
    }
}

/** Small DSL that appends planted items to the [GenContext] accumulators and records the [PlantedItem] list. */
internal class PlantedBuilder(val ctx: GenContext) {
    val tm = ctx.tm
    val rng = ctx.rngPlanted

    fun item(id: String, where: String) {
        ctx.items += PlantedItem(id, where)
    }

    fun contact(name: String, digits10: String, style: Int = 0): SeedContact {
        ctx.usedNumbers += digits10
        val c = SeedContact(ctx.contactKey(), name, listOf(Numbers.stored(digits10, style)))
        ctx.pContacts += c
        return c
    }

    fun call(number: String?, presentation: Int, type: Int, startMs: Long, durationSec: Int): SeedCall {
        val c = SeedCall(ctx.callKey(), number, presentation, type, startMs, durationSec)
        ctx.pCalls += c
        return c
    }

    fun sms(address: String, body: String, type: Int, dateMs: Long): SeedSms {
        val s = SeedSms(ctx.smsKey(), address, body, type, dateMs, address)
        ctx.pSms += s
        return s
    }

    fun mms(box: Int, dateMs: Long, addresses: List<String>, subject: String?, texts: List<String>, images: Int) {
        ctx.pMms += SeedMms(ctx.mmsKey(), box, dateMs, addresses, subject, texts, images)
    }

    fun file(
        kind: MediaKind,
        relativePath: String,
        name: String,
        modifiedMs: Long,
        size: Long,
        payload: Payload,
        takenMs: Long? = null,
        durationMs: Long? = null,
        mime: String = Vocabulary.mimeOf(name.substringAfterLast('.', "")),
    ): SeedFile {
        val f = SeedFile(ctx.fileKey(), kind, relativePath, name, mime, size, takenMs, modifiedMs, durationMs, payload)
        ctx.pFiles += f
        ctx.reserve(f)
        return f
    }

    fun doc(path: String, name: String, modifiedMs: Long, text: String? = null, size: Long = 150_000L): SeedFile =
        file(MediaKind.DOCUMENT, path, name, modifiedMs, size, Payload.Doc(name.substringAfterLast('.', ""), text))

    fun other(path: String, name: String, modifiedMs: Long, size: Long = 50_000L): SeedFile =
        file(MediaKind.OTHER, path, name, modifiedMs, size, Payload.Doc(name.substringAfterLast('.', ""), null))

    fun photo(path: String, name: String, takenMs: Long): SeedFile =
        file(MediaKind.PHOTO, path, name, takenMs, 1_500L, Payload.Jpeg(rng.nextInt(12)), takenMs = takenMs)

    // ---- P1–P17 -----------------------------------------------------------------------------------------------

    fun p1ToP2() {
        val rahul = contact("Rahul", "9876543210")
        item("P1", "contact Rahul ${rahul.numbers[0]}")
        sms(Numbers.normalized("9876543210"), "Booked Hotel Sea View for Friday", 1, ctx.at(20, 10, 15))
        item("P1", "sms from ${Numbers.normalized("9876543210")}: Booked Hotel Sea View for Friday")
        ctx.usedNumbers += "7000012345"
        sms("+917000012345", "see you at the hotel sea view lobby", 1, ctx.at(19, 18, 40))
        item("P2", "sms from +917000012345: see you at the hotel sea view lobby")
    }

    fun p3ToP6() {
        photo("Pictures/Home/", "preview.jpg", ctx.at(30, 9, 0))
        item("P3", "Pictures/Home/preview.jpg")
        doc("Download/", "Sea_View.pdf", ctx.at(31, 9, 0))
        photo("DCIM/Camera/", "SeaView.jpg", ctx.at(31, 9, 30))
        item("P4", "Download/Sea_View.pdf")
        item("P4", "DCIM/Camera/SeaView.jpg")
        doc("Download/", "Hotel booking.pdf", ctx.at(32, 9, 0), text = "Itinerary")
        item("P5", "Download/Hotel booking.pdf")
        doc("Download/", "scan_0042.pdf", ctx.at(33, 9, 0), text = "Your booking reference is SIDX-4711")
        item("P6", "Download/scan_0042.pdf")
    }

    fun p7ToP9() {
        val rahul = contact("राहुल शर्मा", "7000088888")
        sms(Numbers.normalized("7000088888"), "होटल पहुँच गया, कमरा 204", 1, ctx.at(21, 20, 5))
        item("P7", "sms from ${rahul.displayName}: होटल पहुँच गया, कमरा 204")
        val sneha = contact("स्नेहा पाटील", "7000099999")
        sms(Numbers.normalized("7000099999"), "उद्या सकाळी भेटू", 1, ctx.at(22, 21, 5))
        item("P8", "sms from ${sneha.displayName}: उद्या सकाळी भेटू")
        contact("Ölaf Test", "7000033333", 1)
        call("+917000033333", 1, 1, ctx.at(15, 10, 0), 60)
        call("+917000033333", 1, 2, ctx.at(14, 11, 0), 30)
        sms("+917000033333", "ok see you soon", 1, ctx.at(13, 12, 0))
        item("P9", "contact Ölaf Test with 2 calls and 1 sms")
    }

    fun p10ToP11() {
        // "qz" in exactly one field each
        doc("Documents/", "notes_qz.txt", ctx.at(40, 10, 0), size = 2_000L)
        item("P10", "Documents/notes_qz.txt")
        doc("Documents/qzbox/", "plain.txt", ctx.at(40, 10, 5), size = 2_000L)
        item("P10", "Documents/qzbox/plain.txt")
        other("Download/", "archive.qz", ctx.at(40, 10, 10))
        item("P10", "Download/archive.qz")
        contact("Qzin Test", "7000044444", 2)
        call("+917000044444", 1, 1, ctx.at(41, 10, 0), 45)
        item("P10", "contact Qzin Test (call only)")
        ctx.usedNumbers += "7000055555"
        sms("+917000055555", "code qz7", 1, ctx.at(41, 11, 0))
        item("P10", "sms from +917000055555: code qz7")
        // "ra" in exactly one field each
        photo("Pictures/Home/", "mira.jpg", ctx.at(42, 9, 0))
        item("P11", "Pictures/Home/mira.jpg")
        doc("Documents/Ravi files/", "plain.pdf", ctx.at(42, 9, 5))
        item("P11", "Documents/Ravi files/plain.pdf")
        other("Download/", "backup.rar", ctx.at(42, 9, 10))
        item("P11", "Download/backup.rar")
        contact("Ravi", "7000066666", 3)
        call("+917000066666", 1, 2, ctx.at(43, 10, 0), 25)
        item("P11", "contact Ravi (call)")
        ctx.usedNumbers += "7000077777"
        sms("+917000077777", "so brave", 1, ctx.at(43, 11, 0))
        item("P11", "sms from +917000077777: so brave")
    }

    /** 200 SMS with "Meera Kulkarni", exactly 3 containing "invoice". */
    fun p12() {
        val meera = contact("Meera Kulkarni", MEERA_DIGITS)
        val address = Numbers.normalized(MEERA_DIGITS)
        val n = P12_COUNT
        val strata = SmsStrata(rng, n, (0.15 * n).toInt())
        val invoiceAt = flags(rng, n, 3)
        val invoiceBodies =
            listOf("Please send the invoice for March", "Invoice attached, please check", "Did you pay the invoice")
        var inv = 0
        for (i in 0 until n) {
            val sent = rng.chance(0.5)
            val body = if (invoiceAt[i]) {
                invoiceBodies[inv++ % invoiceBodies.size]
            } else {
                smsBody(rng, strata.deva[i], strata.payment[i], strata.the[i], true, Vocabulary.FIRST_NAMES).first
            }
            val daysBack = rng.nextInt(2, 60).toLong()
            val t = tm.onDay(rng, tm.anchorDate.minusDays(daysBack))
            sms(address, body, if (sent) 2 else 1, tm.ms(t))
        }
        item("P12", "conversation of $n sms with ${meera.displayName}, 3 containing invoice")
    }

    fun p13() {
        val h = tm.hotelDay
        val times =
            listOf(
                LocalTime.of(18, 5, 12),
                LocalTime.of(18, 40, 3),
                LocalTime.of(19, 2, 50),
                LocalTime.of(19, 31, 9),
                LocalTime.of(19, 58, 41),
            )
        for (t in times) {
            val taken = tm.ms(LocalDateTime.of(h, t))
            val name = "%04d%02d%02d_%02d%02d%02d.jpg".format(
                h.year,
                h.monthValue,
                h.dayOfMonth,
                t.hour,
                t.minute,
                t.second,
            )
            photo("DCIM/Camera/", name, taken)
        }
        item("P13", "5 photos DCIM/Camera on $h 18:00-20:00")
        val date = "%02d-%02d-%02d".format(h.dayOfMonth, h.monthValue, h.year % 100)
        val ms = tm.ms(LocalDateTime.of(h, LocalTime.of(21, 14)))
        sms("VM-SIDXBK", "Rs 4,500 spent on card XX1234 at SEA VIEW RESORT on $date", 1, ms)
        item("P13", "sms from VM-SIDXBK on $h 21:14")
    }

    fun p14() {
        val d = tm.dayB
        val times = listOf(
            LocalTime.of(0, 0, 0, 0), LocalTime.of(4, 59, 59), LocalTime.of(5, 0, 0), LocalTime.of(11, 59, 59),
            LocalTime.of(12, 0, 0), LocalTime.of(16, 59, 59), LocalTime.of(17, 0, 0), LocalTime.of(21, 59, 59),
            LocalTime.of(22, 0, 0), LocalTime.of(23, 59, 59, 999_000_000),
        )
        ctx.usedNumbers += "7000000014"
        for ((i, t) in times.withIndex()) {
            sms("+917000000014", "Day B marker ${i + 1} at $t", 1, tm.ms(LocalDateTime.of(d, t)))
        }
        item("P14", "10 sms on $d at the part-of-day bounds")
    }

    fun p15ToP17() {
        doc("Documents/", "invoice_march.xlsx", ctx.at(50, 10, 0))
        item("P15", "Documents/invoice_march.xlsx")
        ctx.usedNumbers += "7000000016"
        sms("+917000000016", "kumquat jam recipe", 1, ctx.at(51, 10, 0))
        doc("Documents/", "kumquat_notes.txt", ctx.at(51, 10, 5), size = 2_000L)
        contact("Kumquat Test", "7000000017", 1)
        item("P16", "sms kumquat jam recipe")
        item("P16", "Documents/kumquat_notes.txt")
        item("P16", "contact Kumquat Test")
        sms("AX-SIDXPAY", "50% off \"today\" (only) *T&C_", 1, ctx.at(52, 10, 0))
        doc("Documents/", "a_b(1)*.txt", ctx.at(52, 10, 5), size = 1_000L)
        item("P17", "sms 50% off \"today\" (only) *T&C_")
        item("P17", "Documents/a_b(1)*.txt")
    }

    // ---- §4.8 ---------------------------------------------------------------------------------------------------

    fun edgeCases() {
        doc("Documents/", "a".repeat(251) + ".pdf", ctx.at(60, 10, 0))
        doc("Documents/", "😀 holiday 🎉.txt", ctx.at(60, 10, 1), size = 2_000L)
        doc("Documents/", "ملف_العمل.pdf", ctx.at(60, 10, 2))
        doc("Documents/", "empty.pdf", ctx.at(60, 10, 3), size = 0L)
        other("Download/", "README_noext", ctx.at(60, 10, 4))
        other("Pictures/NoMedia/", ".nomedia", ctx.at(60, 10, 5), 0L)
        photo("Pictures/NoMedia/", "nm.jpg", ctx.at(60, 10, 6))
        doc("Pictures/NoMedia/", "nm.pdf", ctx.at(60, 10, 7))
        doc("Documents/", ".hidden.pdf", ctx.at(60, 10, 8))
        doc("Documents/", "future.pdf", tm.ms(tm.anchor.plusDays(2)))
        doc("Documents/", "epoch.pdf", 0L)
        item("EDGE", "file-name and mtime edge files")

        ctx.usedNumbers += setOf("7000000101", "7000000102")
        sms("+917000000101", "", 1, ctx.at(61, 10, 0))
        sms("+917000000101", "abc ".repeat(2_600).take(10_050), 2, ctx.at(61, 10, 1))
        sms("+91 70000-00 102", "sender with spaces and dashes", 1, ctx.at(61, 10, 2))
        sms("AD-EDGEBK", "alphanumeric sender", 1, ctx.at(61, 10, 3))
        item("EDGE", "sms edge cases")

        call("+917000000101", 1, 1, ctx.at(62, 10, 0), 0)
        call("+917000000101", 1, 5, ctx.at(62, 10, 1), 0)
        call("+917000000101", 1, 6, ctx.at(62, 10, 2), 0)
        call("+917000000101", 1, 7, ctx.at(62, 10, 3), 30)
        call(null, 2, 1, ctx.at(62, 10, 4), 20)
        call(null, 3, 1, ctx.at(62, 10, 5), 20)
        call(null, 4, 1, ctx.at(62, 10, 6), 20)
        item("EDGE", "call types 5/6/7, duration 0, presentation 2/3/4")

        val a = "+917000000101"
        mms(1, ctx.at(63, 10, 0), listOf(a), null, emptyList(), 1)
        mms(1, ctx.at(63, 10, 1), listOf(a), "Subject only", emptyList(), 0)
        mms(1, ctx.at(63, 10, 2), listOf(a), "no subject", listOf("text"), 0)
        mms(
            2,
            ctx.at(63, 10, 3),
            listOf(a) + (1..5).map {
                "+9170000002%02d".format(it)
            },
            null,
            listOf("group of six"),
            0,
        )
        item("EDGE", "mms edge cases")
    }

    companion object {
        const val MEERA_DIGITS = "7000022222"
        const val P12_COUNT = 200
    }
}
