package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class PlantedItemsTest {
    private val plan = TestPlans.small
    private val zone = plan.zone

    private fun file(path: String, name: String) =
        plan.files.singleOrNull { it.relativePath == path && it.displayName == name }

    private fun contact(name: String) = plan.contacts.single { it.displayName == name }

    private fun addr(c: SeedContact) = Numbers.normalized(Numbers.lastTen(c.numbers[0]))

    private fun smsFrom(c: SeedContact) = plan.sms.filter { it.address == addr(c) }

    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    @Test
    fun `P1 contact Rahul with its sms`() {
        val rahul = contact("Rahul")
        assertThat(rahul.numbers).containsExactly("+91 98765 43210")
        assertThat(smsFrom(rahul).map { it.body }).containsExactly("Booked Hotel Sea View for Friday")
    }

    @Test
    fun `P2 sea view sms from an unknown number`() {
        val s = plan.sms.single { it.body == "see you at the hotel sea view lobby" }
        assertThat(
            plan.contacts.flatMap {
                it.numbers
            }.map(Numbers::lastTen),
        ).doesNotContain(Numbers.lastTen(s.address))
    }

    @Test
    fun `P3 to P6 files`() {
        assertThat(file("Pictures/Home/", "preview.jpg")!!.kind).isEqualTo(MediaKind.PHOTO)
        assertThat(file("Download/", "Sea_View.pdf")).isNotNull()
        assertThat(file("DCIM/Camera/", "SeaView.jpg")).isNotNull()
        val hotel = file("Download/", "Hotel booking.pdf")!!
        assertThat((hotel.payload as Payload.Doc).text).isEqualTo("Itinerary")
        val scan = file("Download/", "scan_0042.pdf")!!
        assertThat((scan.payload as Payload.Doc).text).isEqualTo("Your booking reference is SIDX-4711")
    }

    @Test
    fun `P7 and P8 devanagari sms from devanagari contacts`() {
        assertThat(smsFrom(contact("राहुल शर्मा")).map { it.body }).containsExactly("होटल पहुँच गया, कमरा 204")
        assertThat(smsFrom(contact("स्नेहा पाटील")).map { it.body }).containsExactly("उद्या सकाळी भेटू")
    }

    @Test
    fun `P9 Olaf has two calls and one sms`() {
        val olaf = contact("Ölaf Test")
        val ten = Numbers.lastTen(olaf.numbers[0])
        assertThat(plan.calls.count { it.number?.let(Numbers::lastTen) == ten }).isEqualTo(2)
        assertThat(smsFrom(olaf)).hasSize(1)
    }

    @Test
    fun `P10 qz appears in exactly one field each`() {
        assertThat(file("Documents/", "notes_qz.txt")).isNotNull()
        assertThat(file("Documents/qzbox/", "plain.txt")).isNotNull()
        assertThat(file("Download/", "archive.qz")).isNotNull()
        val q = contact("Qzin Test")
        val ten = Numbers.lastTen(q.numbers[0])
        assertThat(plan.calls.count { it.number?.let(Numbers::lastTen) == ten }).isEqualTo(1)
        assertThat(smsFrom(q)).isEmpty()
        assertThat(plan.sms.count { it.body.contains("qz", ignoreCase = true) }).isEqualTo(1)
        assertThat(plan.sms.single { it.body == "code qz7" }).isNotNull()
        assertThat(
            plan.files.count {
                it.displayName.contains("qz", true) || it.relativePath.contains("qz", true)
            },
        ).isEqualTo(3)
    }

    @Test
    fun `P11 ra appears in exactly one field each`() {
        assertThat(file("Pictures/Home/", "mira.jpg")).isNotNull()
        assertThat(file("Documents/Ravi files/", "plain.pdf")).isNotNull()
        assertThat(file("Download/", "backup.rar")).isNotNull()
        val ravi = contact("Ravi")
        val ten = Numbers.lastTen(ravi.numbers[0])
        assertThat(plan.calls.count { it.number?.let(Numbers::lastTen) == ten }).isEqualTo(1)
        assertThat(plan.sms.single { it.body == "so brave" }).isNotNull()
    }

    @Test
    fun `P12 conversation of 200 sms with exactly 3 invoice`() {
        val conv = smsFrom(contact("Meera Kulkarni"))
        assertThat(conv).hasSize(200)
        assertThat(conv.count { it.body.contains("invoice", ignoreCase = true) }).isEqualTo(3)
    }

    @Test
    fun `P13 hotel day photos and the card sms`() {
        val h = plan.hotelDay
        assertThat(h).isEqualTo(plan.anchor.toLocalDate().minusDays(40))
        val photos = plan.files.filter {
            it.kind == MediaKind.PHOTO && it.relativePath == "DCIM/Camera/" && local(it.takenMs!!).toLocalDate() == h &&
                local(it.takenMs).hour in 18..19
        }
        assertThat(photos.size).isAtLeast(5)
        val sms = plan.sms.single { it.address == "VM-SIDXBK" && it.body.contains("SEA VIEW RESORT") }
        assertThat(local(sms.dateMs)).isEqualTo(h.atTime(21, 14))
        val date = "%02d-%02d-%02d".format(h.dayOfMonth, h.monthValue, h.year % 100)
        assertThat(sms.body).isEqualTo("Rs 4,500 spent on card XX1234 at SEA VIEW RESORT on $date")
    }

    @Test
    fun `P14 day B has one sms at each part-of-day bound`() {
        val d: LocalDate = plan.anchor.toLocalDate().minusDays(3)
        val onB = plan.sms.filter { local(it.dateMs).toLocalDate() == d && it.body.startsWith("Day B marker") }
        val times = onB.map { local(it.dateMs).toLocalTime() }.toSet()
        assertThat(times).containsExactly(
            LocalTime.of(0, 0, 0, 0), LocalTime.of(4, 59, 59), LocalTime.of(5, 0, 0), LocalTime.of(11, 59, 59),
            LocalTime.of(12, 0, 0), LocalTime.of(16, 59, 59), LocalTime.of(17, 0, 0), LocalTime.of(21, 59, 59),
            LocalTime.of(22, 0, 0), LocalTime.of(23, 59, 59, 999_000_000),
        )
    }

    @Test
    fun `P15 to P17 files and sms`() {
        assertThat(file("Documents/", "invoice_march.xlsx")).isNotNull()
        assertThat(plan.sms.count { it.body.contains("kumquat") }).isEqualTo(1)
        assertThat(plan.files.count { it.displayName.contains("kumquat") }).isEqualTo(1)
        assertThat(plan.contacts.count { it.displayName.contains("Kumquat") }).isEqualTo(1)
        assertThat(plan.sms.single { it.body == "50% off \"today\" (only) *T&C_" }).isNotNull()
        assertThat(plan.files.count { it.displayName == "a_b(1)*.txt" }).isEqualTo(1)
    }

    @Test
    fun `planted list names every P1 to P17`() {
        val ids = plan.planted.map { it.id }.toSet()
        assertThat(ids).containsAtLeastElementsIn((1..17).map { "P$it" })
        assertThat(plan.planted.single { it.id == "P6" }.where).isEqualTo("Download/scan_0042.pdf")
    }

    @Test
    fun `planted items are present in every profile`() {
        for (p in listOf(TestPlans.edge, TestPlans.nfr002)) {
            assertThat(p.planted.map { it.id }.toSet()).containsAtLeastElementsIn((1..17).map { "P$it" })
        }
    }

    @Test
    fun `edge profile has the edge additions`() {
        val e = TestPlans.edge
        assertThat(e.files.any { it.displayName.length == 255 }).isTrue()
        assertThat(e.files.any { it.logicalSize == 0L && it.displayName.endsWith(".pdf") }).isTrue()
        assertThat(e.files.any { it.displayName == ".nomedia" }).isTrue()
        assertThat(e.files.any { it.displayName == ".hidden.pdf" }).isTrue()
        assertThat(e.files.any { it.modifiedMs == 0L }).isTrue()
        val anchorMs = e.anchor.atZone(e.zone).toInstant().toEpochMilli()
        assertThat(e.files.any { it.modifiedMs > anchorMs + 40 * 3_600_000L }).isTrue()
        assertThat(e.sms.any { it.body.isEmpty() }).isTrue()
        assertThat(e.sms.any { it.body.length == 10_050 }).isTrue()
        assertThat(e.calls.map { it.type }.toSet()).containsAtLeast(5, 6, 7)
        assertThat(e.calls.map { it.presentation }.toSet()).containsAtLeast(2, 3, 4)
        assertThat(e.calls.any { it.type == 1 && it.durationSec == 0 }).isTrue()
        assertThat(e.mms.any { it.addresses.size == 6 }).isTrue()
        assertThat(e.mms.any { it.subject == "no subject" }).isTrue()
        assertThat(e.mms.any { it.texts.isEmpty() && it.imageParts > 0 }).isTrue()
        assertThat(e.mms.any { it.texts.isEmpty() && it.imageParts == 0 && it.subject != null }).isTrue()
        assertThat(TestPlans.small.files.none { it.displayName == ".nomedia" }).isTrue()
    }

    @Test
    fun `manifest follows the LLD layout with a stable key order`() {
        val json = SeedManifest.build(plan)
        val order = listOf(
            "generatorVersion", "profile", "seed", "anchor", "zone", "seedDay", "busyDay", "hotelDay", "planned",
            "written", "failed", "expectedExcluded", "rescanned", "mtimeFailed", "sparse", "expectedLinks",
            "recordingCases", "planted", "phasesMs", "smsRole",
        ).map { json.indexOf("\"$it\"") }
        assertThat(order.all { it >= 0 }).isTrue()
        assertThat(order).isInOrder()
        assertThat(json).contains("\"profile\": \"small\"")
        assertThat(json).contains("\"anchor\": \"2026-11-01T12:00\"")
        assertThat(json).contains("\"zone\": \"Asia/Kolkata\"")
        assertThat(json).contains("\"busyDay\": \"2026-10-31\"")
        assertThat(json).contains("\"hotelDay\": \"2026-09-22\"")
        assertThat(json).contains("{\"id\": \"P6\", \"where\": \"Download/scan_0042.pdf\"}")
        assertThat(json).contains("\"expectedLinks\": ${plan.recordings.count { it.expectLinked }}")
        assertThat(json.count { it == '{' }).isEqualTo(json.count { it == '}' })
        assertThat(json.count { it == '[' }).isEqualTo(json.count { it == ']' })
        assertThat(SeedManifest.build(plan)).isEqualTo(json)
        assertThat(SeedManifest.str("a\"b\\c\n")).isEqualTo("\"a\\\"b\\\\c\\n\"")
    }
}
