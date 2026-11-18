package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Bump with any change of the generated output; part of the manifest (LLD §4.2). */
const val GENERATOR_VERSION = 1

// Per-kind stream salts (fixed constants, never change): the stream of one kind never depends on another kind.
internal const val SALT_CONTACTS = 0x5DEECE66D1A2B3C4L
internal const val SALT_CALLS = 0x2545F4914F6CDD1DL
internal const val SALT_SMS = 0x1B873593CC9E2D51L
internal const val SALT_MMS = 0x27D4EB2F165667C5L
internal const val SALT_PHOTOS = 0x7FEB352D846CA68BL
internal const val SALT_VIDEOS = 0x3C6EF372FE94F82BL
internal const val SALT_DOCUMENTS = 0x510E527FADE682D1L
internal const val SALT_OTHERS = 0x1F83D9ABFB41BD6BL
internal const val SALT_RECORDINGS = 0x5BE0CD19137E2179L
internal const val SALT_PLANTED = 0x6A09E667F3BCC908L

/** Shared state of one [Generator.plan] run: streams, time model, accumulators of planted items, key counters. */
internal class GenContext(val profile: Profile, val seed: Long, val tm: TimeModel) {
    val counts = profile.counts

    val rngContacts = SeedRandom(seed xor SALT_CONTACTS)
    val rngCalls = SeedRandom(seed xor SALT_CALLS)
    val rngSms = SeedRandom(seed xor SALT_SMS)
    val rngMms = SeedRandom(seed xor SALT_MMS)
    val rngPhotos = SeedRandom(seed xor SALT_PHOTOS)
    val rngVideos = SeedRandom(seed xor SALT_VIDEOS)
    val rngDocuments = SeedRandom(seed xor SALT_DOCUMENTS)
    val rngOthers = SeedRandom(seed xor SALT_OTHERS)
    val rngRecordings = SeedRandom(seed xor SALT_RECORDINGS)
    val rngPlanted = SeedRandom(seed xor SALT_PLANTED)

    // Planted / extra items (keys from PLANTED_KEY_BASE), filled by Planted + RecordingPlan.
    val pContacts = ArrayList<SeedContact>()
    val pCalls = ArrayList<SeedCall>()
    val pSms = ArrayList<SeedSms>()
    val pMms = ArrayList<SeedMms>()
    val pFiles = ArrayList<SeedFile>()
    val pRecordings = ArrayList<SeedRecording>()
    val items = ArrayList<PlantedItem>()

    private var nextContact = PLANTED_KEY_BASE
    private var nextCall = PLANTED_KEY_BASE
    private var nextSms = PLANTED_KEY_BASE
    private var nextMms = PLANTED_KEY_BASE
    private var nextFile = PLANTED_KEY_BASE

    fun contactKey() = nextContact++

    fun callKey() = nextCall++

    fun smsKey() = nextSms++

    fun mmsKey() = nextMms++

    fun fileKey() = nextFile++

    /** Last-ten-digit strings in use (bulk numbers never collide with them). */
    val usedNumbers = HashSet<String>()

    /** "relativePath + displayName" of every file so far. */
    val usedPaths = HashSet<String>()

    fun reserve(file: SeedFile) {
        usedPaths += file.relativePath + file.displayName
    }

    /** Milliseconds of [daysBack] before the anchor date at the given wall-clock time. */
    fun at(daysBack: Long, h: Int, m: Int, s: Int = 0, nanoMs: Int = 0): Long =
        tm.ms(LocalDateTime.of(tm.anchorDate.minusDays(daysBack), java.time.LocalTime.of(h, m, s, nanoMs * 1_000_000)))
}

internal fun hasDevanagari(s: String): Boolean = s.any { it in 'ऀ'..'ॿ' }

/** Number of Devanagari items for a kind of [total] items of which [planted] are planted ([plantedDeva] of them). */
internal fun devaTarget(total: Int, planted: Int, plantedDeva: Int, bulk: Int): Int =
    ((0.15 * (total + planted)).roundToInt() - plantedDeva).coerceIn(0, bulk)

/** Exactly [count] true flags among [n], positions shuffled by [rng]. */
internal fun flags(rng: SeedRandom, n: Int, count: Int): BooleanArray {
    val idx = MutableList(n) { it }
    rng.shuffle(idx)
    val out = BooleanArray(n)
    for (i in 0 until count.coerceIn(0, n)) out[idx[i]] = true
    return out
}

/** Per-SMS flags: Devanagari (exact [devaCount]), `payment` (8 % of [n]), `the` (40 % of the English ones). */
internal class SmsStrata(rng: SeedRandom, n: Int, devaCount: Int) {
    val deva: BooleanArray = flags(rng, n, devaCount)
    val payment = BooleanArray(n)
    val the = BooleanArray(n)

    init {
        val english = (0 until n).filter { !deva[it] }.toMutableList()
        val pay = english.toMutableList().also { rng.shuffle(it) }
        for (i in 0 until (0.08 * n).roundToInt().coerceAtMost(pay.size)) payment[pay[i]] = true
        val theOrder = english.toMutableList().also { rng.shuffle(it) }
        for (i in 0 until (0.40 * english.size).roundToInt()) the[theOrder[i]] = true
    }
}

/** Stateless-ish body maker: picks a template for the flags and fills its slots. */
internal fun smsBody(
    rng: SeedRandom,
    deva: Boolean,
    payment: Boolean,
    the: Boolean,
    personalOnly: Boolean,
    names: List<String>,
): Pair<String, Boolean> {
    val pool = Vocabulary.templates(deva, payment, the, personalOnly)
    val t = pool[rng.nextInt(pool.size)]
    return Vocabulary.fill(t, rng, names) to t.service
}

/** Phone-number helpers: Indian mobiles, mixed stored formats. */
internal object Numbers {
    fun lastTen(s: String): String = s.filter { it.isDigit() }.takeLast(10)

    fun normalized(digits10: String) = "+91$digits10"

    /** `+91 98765 43210` */
    fun spaced(digits10: String) = "+91 ${digits10.substring(0, 5)} ${digits10.substring(5)}"

    /** One of the four stored formats of §4.3, by [style] 0..3. */
    fun stored(digits10: String, style: Int): String = when (style) {
        0 -> spaced(digits10)
        1 -> "0$digits10"
        2 -> "${digits10.substring(0, 5)}-${digits10.substring(5)}"
        else -> "+91$digits10"
    }

    fun fresh(rng: SeedRandom, used: MutableSet<String>): String {
        while (true) {
            val first = "789"[rng.nextInt(3)]
            val sb = StringBuilder().append(first)
            repeat(9) { sb.append('0' + rng.nextInt(10)) }
            val d = sb.toString()
            if ("98765" in d || d in used) continue
            used += d
            return d
        }
    }
}
