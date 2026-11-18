package com.kuriousdragon.smartindex.tools.seeder.gen

import java.text.Normalizer
import java.time.format.DateTimeFormatter

/** Recording ↔ call pairs, the bulk name mix and the tricky cases (LLD §4.6; matcher LLD §3.4, §4.5, §5.8). */
internal object RecordingPlan {
    const val PATTERN_YYMMDD = "who_yyMMdd_HHmmss"
    const val PATTERN_YYYYMMDD = "who_yyyyMMdd_HHmmss"
    const val PATTERN_NUMBER = "who_number_yyMMdd_HHmmss"
    const val CALL_DIR = "Recordings/Call/"
    const val LEGACY_DIR = "Call/"
    const val VOICE_DIR = "Recordings/Voice Recorder/"
    private val F6 = DateTimeFormatter.ofPattern("yyMMdd_HHmmss")
    private val F8 = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    // ---- tricky cases ---------------------------------------------------------------------------------------

    fun tricky(b: PlantedBuilder) {
        val ctx = b.ctx
        val tm = b.tm
        val day = tm.trickyDay
        fun t(h: Int, m: Int, s: Int): Long = tm.ms(day.atTime(h, m, s))
        b.contact("Priya Test", "7000011111", 0)
        val rahulNo = Numbers.normalized("9876543210")
        val meeraNo = Numbers.normalized(PlantedBuilder.MEERA_DIGITS)
        val priyaNo = Numbers.normalized("7000011111")

        fun rec(
            case: String,
            callKey: Int?,
            linked: Boolean,
            dir: String,
            name: String,
            startMs: Long,
            durMs: Long,
            pattern: String?,
            expName: String?,
            expNumber: String?,
        ) {
            val file = recFile(ctx.fileKey(), dir, name, startMs + durMs, durMs, name.endsWith(".amr"))
            ctx.reserve(file)
            ctx.pRecordings += SeedRecording(
                file,
                callKey,
                linked,
                case,
                pattern,
                expName?.let(::nfc),
                expNumber,
                if (pattern == null) null else tm.local(startMs),
            )
        }

        fun nameOf(prefix: String, startMs: Long, suffix: String = "") =
            "$prefix${tm.local(startMs).format(F6)}$suffix.m4a"

        // E1 contact
        val e1 = b.call(rahulNo, 1, 1, t(9, 0, 0), 185)
        var s = e1.startMs + 17_000
        rec("E1", e1.key, true, CALL_DIR, nameOf("Call recording Rahul_", s), s, 184_000, PATTERN_YYMMDD, "Rahul", null)
        // E2 unknown number
        val e2 = b.call("+919876500001", 1, 1, t(10, 0, 0), 42)
        s = e2.startMs + 9_000
        rec(
            "E2", e2.key, true, CALL_DIR, nameOf("Call recording +91 98765 00001_", s), s, 41_500,
            PATTERN_YYMMDD, null, "+91 98765 00001",
        )
        // E4 back-to-back same contact
        val e4a = b.call(rahulNo, 1, 2, t(11, 0, 0), 40)
        val e4b = b.call(rahulNo, 1, 2, t(11, 1, 10), 120)
        s = e4a.startMs + 12_000
        rec("E4", e4a.key, true, CALL_DIR, nameOf("Call recording Rahul_", s), s, 40_000, PATTERN_YYMMDD, "Rahul", null)
        s = e4b.startMs + 8_000
        rec(
            "E4", e4b.key, true, CALL_DIR,
            nameOf(
                "Call recording Rahul_",
                s,
            ),
            s, 119_000, PATTERN_YYMMDD, "Rahul", null,
        )
        // E5 two calls < 1 min
        val e5a = b.call(meeraNo, 1, 1, t(12, 0, 0), 20)
        val e5b = b.call(meeraNo, 1, 1, t(12, 0, 50), 22)
        s = e5a.startMs + 5_000
        rec(
            "E5", e5a.key, true, CALL_DIR,
            nameOf(
                "Call recording Meera Kulkarni_",
                s,
            ),
            s, 20_000, PATTERN_YYMMDD, "Meera Kulkarni", null,
        )
        s = e5b.startMs + 5_000
        rec(
            "E5", e5b.key, true, CALL_DIR,
            nameOf(
                "Call recording Meera Kulkarni_",
                s,
            ),
            s, 22_000, PATTERN_YYMMDD, "Meera Kulkarni", null,
        )
        // E6 unparseable name: time + mtime only (recording starts 12 s after the call, lasts 180 s)
        val e6 = b.call(rahulNo, 1, 1, t(13, 0, 0), 181)
        val e6File = recFile(ctx.fileKey(), CALL_DIR, "Voice 001.m4a", e6.startMs + 12_000 + 180_000, 180_000, false)
        ctx.reserve(e6File)
        ctx.pRecordings += SeedRecording(e6File, e6.key, true, "E6")
        // E8 missed then answered
        b.call(meeraNo, 1, 3, t(14, 0, 0), 0)
        val e8 = b.call(meeraNo, 1, 1, t(14, 0, 40), 60)
        s = e8.startMs + 8_000
        rec(
            "E8", e8.key, true, CALL_DIR,
            nameOf(
                "Call recording Meera Kulkarni_",
                s,
            ),
            s, 60_000, PATTERN_YYMMDD, "Meera Kulkarni", null,
        )
        // E9 duplicate copy
        val e9 = b.call(priyaNo, 1, 1, t(15, 0, 0), 90)
        s = e9.startMs + 10_000
        rec(
            "E9", e9.key, true, CALL_DIR,
            nameOf(
                "Call recording Priya Test_",
                s,
            ),
            s, 89_000, PATTERN_YYMMDD, "Priya Test", null,
        )
        rec(
            "E9copy", e9.key, false, CALL_DIR,
            nameOf(
                "Call recording Priya Test_",
                s,
                " (1)",
            ),
            s, 89_000, PATTERN_YYMMDD, "Priya Test", null,
        )
        // private number
        val pv = b.call(null, 2, 1, t(16, 0, 0), 75)
        s = pv.startMs + 6_000
        rec(
            "PRIVATE", pv.key, true, CALL_DIR,
            nameOf(
                "Call recording Private number_",
                s,
            ),
            s, 74_000, PATTERN_YYMMDD, null, null,
        )
        // N1 missed call with recording
        val n1 = b.call(priyaNo, 1, 3, t(17, 0, 0), 0)
        s = n1.startMs + 5_000
        rec(
            "N1", n1.key, false, CALL_DIR,
            nameOf(
                "Call recording Priya Test_",
                s,
            ),
            s, 30_000, PATTERN_YYMMDD, "Priya Test", null,
        )
        // N2 window: -11 s and +91 s
        val n2 = b.call(meeraNo, 1, 1, t(18, 0, 0), 120)
        s = n2.startMs - 11_000
        rec(
            "N2", n2.key, false, CALL_DIR,
            nameOf(
                "Call recording Meera Kulkarni_",
                s,
            ),
            s, 120_000, PATTERN_YYMMDD, "Meera Kulkarni", null,
        )
        s = n2.startMs + 91_000
        rec(
            "N2", n2.key, false, CALL_DIR,
            nameOf(
                "Call recording Meera Kulkarni_",
                s,
            ),
            s, 120_000, PATTERN_YYMMDD, "Meera Kulkarni", null,
        )
        // N3 duration: 127 s against a 120 s call
        val n3 = b.call(rahulNo, 1, 1, t(19, 0, 0), 120)
        s = n3.startMs + 10_000
        rec(
            "N3", n3.key, false, CALL_DIR,
            nameOf(
                "Call recording Rahul_",
                s,
            ),
            s, 127_000, PATTERN_YYMMDD, "Rahul", null,
        )
        // N4 contradicting contact
        val n4 = b.call(priyaNo, 1, 1, t(20, 0, 0), 100)
        s = n4.startMs + 10_000
        rec(
            "N4", n4.key, false, CALL_DIR,
            nameOf(
                "Call recording Rahul_",
                s,
            ),
            s, 100_000, PATTERN_YYMMDD, "Rahul", null,
        )
        // N5 no call within +-10 min
        s = t(21, 0, 0)
        rec("N5", null, false, CALL_DIR, nameOf("Call recording Rahul_", s), s, 60_000, PATTERN_YYMMDD, "Rahul", null)
        // N6 not a call recording: a voice memo in the Voice Recorder folder (OTHER_FILE)
        val memoStart = t(22, 0, 0)
        val memo = recFile(ctx.fileKey(), VOICE_DIR, nameOf("memo_", memoStart), memoStart + 45_000, 45_000, false)
        val memoFile = memo.copy(kind = MediaKind.OTHER)
        ctx.pFiles += memoFile
        ctx.reserve(memoFile)
        ctx.items += PlantedItem("N6", memoFile.relativePath + memoFile.displayName)
        ctx.items += PlantedItem("E-N", "tricky recording cases E1 E2 E4 E5 E6 E8 E9 PRIVATE N1 N2 N3 N4 N5 on $day")
    }

    private fun recFile(key: Int, dir: String, name: String, modifiedMs: Long, durMs: Long, amr: Boolean): SeedFile =
        SeedFile(
            key = key,
            kind = MediaKind.RECORDING,
            relativePath = dir,
            displayName = name,
            mime = if (amr) "audio/amr" else "audio/mp4",
            logicalSize = if (amr) durMs * 8 / 5 else durMs * 2,
            takenMs = null,
            modifiedMs = modifiedMs,
            durationMs = durMs,
            payload = if (amr) Payload.Amr else Payload.M4a,
        )

    // ---- bulk ---------------------------------------------------------------------------------------------------

    /**
     * Bulk recordings (counts of the profile). Calls come from [calls] (bulk calls only are chosen); a recorded call
     * has no other call in the matcher window of its recording, so a linked pair is unambiguous.
     */
    fun bulk(ctx: GenContext, contacts: List<SeedContact>, calls: List<SeedCall>, keyBase: Int): List<SeedRecording> {
        val rng = ctx.rngRecordings
        val tm = ctx.tm
        val byLast10 = HashMap<String, SeedContact>()
        for (c in contacts) for (n in c.numbers) byLast10[Numbers.lastTen(n)] = c
        val sorted = calls.sortedBy { it.startMs }
        val starts = LongArray(sorted.size) { sorted[it].startMs }

        fun countIn(from: Long, to: Long): Int {
            var lo = 0
            var hi = starts.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (starts[mid] < from) lo = mid + 1 else hi = mid
            }
            var n = 0
            var i = lo
            while (i < starts.size && starts[i] <= to) {
                n++
                i++
            }
            return n
        }

        // Calls whose matcher window contains a recording anchor a: start in [a - 90 s, a + 10 s].
        fun inWindow(a: Long) = countIn(a - 90_000, a + 10_000)

        val candidates = calls.filter {
            it.key < PLANTED_KEY_BASE && (it.type == 1 || it.type == 2) && it.durationSec >= 10 && it.number != null
        }
        val known = candidates.filter { byLast10.containsKey(Numbers.lastTen(it.number!!)) }
        val unknown = candidates.filter { !byLast10.containsKey(Numbers.lastTen(it.number!!)) }
        val deva = known.filter { hasDevanagari(byLast10.getValue(Numbers.lastTen(it.number!!)).displayName) }
        val used = HashSet<Int>()

        fun pick(pool: List<SeedCall>): SeedCall? {
            if (pool.isEmpty()) return null
            repeat(60) {
                val c = pool[rng.nextInt(pool.size)]
                if (c.key !in used) return c
            }
            return null
        }

        val out = ArrayList<SeedRecording>()
        val c = ctx.counts
        for (j in 0 until c.recordings) {
            val linked = j < c.recordingsLinked
            val variant = if (linked) "BULK" else listOf("N2", "N3", "N5")[(j - c.recordingsLinked) % 3]
            var done = false
            var attempts = 0
            while (!done && attempts++ < 400) {
                val u = rng.nextInt(100)
                var form = when {
                    u < 65 -> 1
                    u < 80 -> 2
                    u < 85 -> 3
                    u < 90 -> 4
                    u < 95 -> 5
                    else -> 6
                }
                if (variant == "N5") {
                    form = 1
                    val who = contacts[rng.nextInt(contacts.size)]
                    val a = tm.ms(tm.random(rng)) / 1000 * 1000
                    if (countIn(a - 600_000, a + 600_000) != 0) continue
                    val dur = 20_000L + rng.nextInt(280_000)
                    out += make(ctx, keyBase + j, form, who, null, a, dur, "N5", null, false)
                    done = true
                    continue
                }
                val pool = when (form) {
                    2 -> unknown
                    5 -> candidates
                    6 -> deva
                    else -> known
                }
                var call = pick(pool)
                if (call == null) {
                    form = 1
                    call = pick(known) ?: continue
                }
                val who = byLast10[Numbers.lastTen(call.number!!)]
                if (who == null && form != 2 && form != 5) continue
                val callMs = call.durationSec * 1000L
                val start: Long
                val dur: Long
                when (variant) {
                    "N2" -> {
                        val d = if (rng.chance(0.5)) {
                            -(11_000L + 1000L * rng.nextInt(20))
                        } else {
                            91_000L +
                                1000L * rng.nextInt(30)
                        }
                        start = call.startMs + d
                        dur = callMs - rng.nextInt(2001)
                        if (inWindow(start) != 0) continue
                    }

                    "N3" -> {
                        start = call.startMs + 1000L * (2 + rng.nextInt(24))
                        val tol = maxOf(5_000L, (callMs * 0.05).toLong())
                        dur = callMs + tol + 2_000L + rng.nextInt(15_000)
                        if (inWindow(start) != 1) continue
                    }

                    else -> {
                        start = call.startMs + 1000L * (2 + rng.nextInt(24))
                        dur = callMs - rng.nextInt(2001)
                        if (inWindow(start) != 1) continue
                    }
                }
                used += call.key
                out += make(ctx, keyBase + j, form, who, call, start, dur, variant, call.number, linked)
                done = true
            }
        }
        return out
    }

    private fun make(
        ctx: GenContext,
        key: Int,
        form: Int,
        who: SeedContact?,
        call: SeedCall?,
        startMs: Long,
        durMs: Long,
        case: String,
        number: String?,
        linked: Boolean,
    ): SeedRecording {
        val tm = ctx.tm
        val local = tm.local(startMs)
        val f6 = local.format(F6)
        val f8 = local.format(F8)
        val name = who?.let { nfc(it.displayName) }
        val ten = number?.let { Numbers.lastTen(it) } ?: who?.let { Numbers.lastTen(it.numbers[0]) }.orEmpty()
        val digits12 = "91$ten"
        val dir: String
        val fileName: String
        val pattern: String
        var expName: String? = null
        var expNumber: String? = null
        when (form) {
            2 -> {
                dir = CALL_DIR
                expNumber = Numbers.spaced(ten)
                fileName = "Call recording ${expNumber}_$f6.m4a"
                pattern = PATTERN_YYMMDD
            }

            3 -> {
                dir = CALL_DIR
                expName = name
                expNumber = digits12
                fileName = "${name}_${digits12}_$f6.m4a"
                pattern = PATTERN_NUMBER
            }

            4 -> {
                dir = CALL_DIR
                expName = name
                fileName = "Call recording ${name}_$f8.m4a"
                pattern = PATTERN_YYYYMMDD
            }

            5 -> {
                dir = LEGACY_DIR
                expNumber = digits12
                fileName = "${digits12}_$f6.amr"
                pattern = PATTERN_YYMMDD
            }

            6 -> {
                dir = CALL_DIR
                expName = name
                fileName = "कॉल रिकॉर्डिंग ${name}_$f6.m4a"
                pattern = PATTERN_YYMMDD
            }

            else -> {
                dir = CALL_DIR
                expName = name
                fileName = "Call recording ${name}_$f6.m4a"
                pattern = PATTERN_YYMMDD
            }
        }
        val file = recFile(key, dir, fileName, startMs + durMs, durMs, form == 5)
        ctx.reserve(file)
        return SeedRecording(file, call?.key, linked, case, pattern, expName, expNumber, local)
    }
}
