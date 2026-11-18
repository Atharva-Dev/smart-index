package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test

class ProfileCountsTest {
    private fun bulkCounts(plan: SeedPlan): Map<String, Int> {
        val b = { key: Int -> key < PLANTED_KEY_BASE }
        return mapOf(
            "contacts" to plan.contacts.count { b(it.key) },
            "calls" to plan.calls.count { b(it.key) },
            "sms" to plan.sms.count { b(it.key) },
            "mms" to plan.mms.count { b(it.key) },
            "photos" to plan.files.count { b(it.key) && it.kind == MediaKind.PHOTO },
            "videos" to plan.files.count { b(it.key) && it.kind == MediaKind.VIDEO },
            "documents" to plan.files.count { b(it.key) && it.kind == MediaKind.DOCUMENT },
            "others" to plan.files.count { b(it.key) && it.kind == MediaKind.OTHER },
            "recordings" to plan.recordings.count { b(it.file.key) },
        )
    }

    private fun check(plan: SeedPlan) {
        val c = plan.profile.counts
        assertThat(bulkCounts(plan)).containsExactly(
            "contacts", c.contacts, "calls", c.calls, "sms", c.sms, "mms", c.mms, "photos", c.photos,
            "videos", c.videos, "documents", c.documents, "others", c.others, "recordings", c.recordings,
        )
        val linked = plan.recordings.filter { it.file.key < PLANTED_KEY_BASE }
        assertThat(linked.count { it.expectLinked }).isEqualTo(c.recordingsLinked)
        // keys are unique per list
        assertThat(plan.contacts.map { it.key }.toSet()).hasSize(plan.contacts.size)
        assertThat(plan.calls.map { it.key }.toSet()).hasSize(plan.calls.size)
        assertThat(plan.sms.map { it.key }.toSet()).hasSize(plan.sms.size)
        assertThat((plan.files.map { it.key } + plan.recordings.map { it.file.key }).toSet())
            .hasSize(plan.files.size + plan.recordings.size)
    }

    @Test
    fun `small profile has the exact table counts plus planted items`() = check(TestPlans.small)

    @Test
    fun `edge profile has the exact table counts plus planted and edge items`() = check(TestPlans.edge)

    @Test(timeout = 120_000)
    fun `nfr002 profile has the exact table counts plus planted items`() = check(TestPlans.nfr002)

    @Test
    fun `devanagari share is 13 to 17 percent per kind`() {
        for (plan in listOf(TestPlans.small, TestPlans.nfr002)) {
            fun share(texts: List<String>) = texts.count(::hasDevanagari).toDouble() / texts.size
            val shares = mapOf(
                "contacts" to share(plan.contacts.map { it.displayName }),
                "sms" to share(plan.sms.map { it.body }),
                "documents" to share(plan.files.filter { it.kind == MediaKind.DOCUMENT }.map { it.displayName }),
                "others" to share(plan.files.filter { it.kind == MediaKind.OTHER }.map { it.displayName }),
            )
            for ((kind, s) in shares) {
                assertThat(s).isAtLeast(0.13)
                assertThat(s).isAtMost(0.17)
                println("${plan.profile} $kind devanagari=$s")
            }
        }
    }

    @Test
    fun `busy day has at least 850 items with the mix of the LLD`() {
        for (plan in listOf(TestPlans.small, TestPlans.nfr002)) {
            val zone = plan.zone
            fun onBusy(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == plan.busyDay
            val sms = plan.sms.count { onBusy(it.dateMs) }
            val calls = plan.calls.count { onBusy(it.startMs) }
            val photos = plan.files.count { it.kind == MediaKind.PHOTO && onBusy(it.takenMs!!) }
            val videos = plan.files.count { it.kind == MediaKind.VIDEO && onBusy(it.takenMs!!) }
            val docs = plan.files.count { it.kind == MediaKind.DOCUMENT && onBusy(it.modifiedMs) }
            val others = plan.files.count { it.kind == MediaKind.OTHER && onBusy(it.modifiedMs) }
            assertThat(sms).isAtLeast(300)
            assertThat(calls).isAtLeast(120)
            assertThat(photos).isAtLeast(250)
            assertThat(videos).isAtLeast(30)
            assertThat(docs).isAtLeast(100)
            assertThat(others).isAtLeast(50)
            assertThat(sms + calls + photos + videos + docs + others).isAtLeast(850)
            assertThat(plan.busyDay).isEqualTo(plan.anchor.toLocalDate().minusDays(1))
        }
    }

    @Test
    fun `hotel day items are present`() {
        val plan = TestPlans.small
        assertThat(plan.hotelDay).isEqualTo(plan.anchor.toLocalDate().minusDays(40))
        fun onHotel(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(plan.zone).toLocalDate() == plan.hotelDay
        assertThat(plan.files.count { it.kind == MediaKind.PHOTO && onHotel(it.takenMs!!) }).isAtLeast(5)
        assertThat(plan.sms.any { onHotel(it.dateMs) && it.address == "VM-SIDXBK" }).isTrue()
    }

    @Test
    fun `all items lie within the three years before the anchor`() {
        val plan = TestPlans.small
        val anchorMs = plan.anchor.atZone(plan.zone).toInstant().toEpochMilli()
        val earliest = plan.anchor.minusYears(3).minus(Duration.ofDays(2)).atZone(plan.zone).toInstant().toEpochMilli()
        for (s in plan.sms) assertThat(s.dateMs).isAtMost(anchorMs)
        for (s in plan.sms.filter { it.key < PLANTED_KEY_BASE }) assertThat(s.dateMs).isAtLeast(earliest)
        for (c in plan.calls) assertThat(c.startMs).isAtMost(anchorMs)
    }

    @Test
    fun `nfr002 plan builds within time and memory`() {
        val start = System.nanoTime()
        val plan = Generator(Profile.NFR002, 7, TestPlans.ANCHOR, TestPlans.ZONE).plan()
        val ms = (System.nanoTime() - start) / 1_000_000
        System.gc()
        val usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        println("nfr002 plan built in $ms ms, heap in use $usedMb MB, items=${plan.sms.size + plan.calls.size}")
        assertThat(ms).isLessThan(60_000)
        assertThat(usedMb).isLessThan(512)
    }
}
