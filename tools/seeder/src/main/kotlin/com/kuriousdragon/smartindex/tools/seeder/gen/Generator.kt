package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Deterministic plan of everything the seeder writes (LLD §4). Same (profile, seed, anchor, zone) gives the
 * same plan, byte for byte. [only] blanks the lists of kinds the run does not write; it never changes the data of
 * the kinds that stay (each kind draws from its own stream).
 */
class Generator(
    private val profile: Profile,
    private val seed: Long,
    private val anchor: LocalDateTime,
    private val zone: ZoneId,
    private val only: Set<String>? = null,
) {
    fun plan(): SeedPlan {
        val tm = TimeModel(anchor, zone)
        val ctx = GenContext(profile, seed, tm)
        Planted.build(ctx)
        val c = ctx.counts
        val busy = profile != Profile.EDGE

        // Key space of bulk files: photos, videos, documents, others, recordings in this fixed order.
        val photoBase = 0
        val videoBase = photoBase + c.photos
        val docBase = videoBase + c.videos
        val otherBase = docBase + c.documents
        val recBase = otherBase + c.others

        val book = ContactBook.build(ctx)
        val calls = CallGen.generate(ctx, book, busy)
        val sms = SmsGen.generate(ctx, book, busy)
        val mms = MmsGen.generate(ctx, book)
        val photos = FileGen.photos(ctx, photoBase, busy)
        val videos = FileGen.videos(ctx, videoBase, busy)
        val docs = FileGen.documents(ctx, docBase, busy)
        val others = FileGen.others(ctx, otherBase, busy)
        val allCalls = calls + ctx.pCalls
        val recordings = RecordingPlan.bulk(ctx, book.contacts, allCalls, recBase) + ctx.pRecordings

        fun keep(kind: String) = only == null || kind in only
        return SeedPlan(
            profile = profile,
            seed = seed,
            anchor = anchor,
            zone = zone,
            contacts = if (keep("contacts")) book.contacts + ctx.pContacts else emptyList(),
            calls = if (keep("calls")) allCalls else emptyList(),
            sms = if (keep("sms")) sms + ctx.pSms else emptyList(),
            mms = if (keep("mms")) mms + ctx.pMms else emptyList(),
            files = (if (keep("photos")) photos + ctx.pFiles.filter { it.kind == MediaKind.PHOTO } else emptyList()) +
                (if (keep("videos")) videos + ctx.pFiles.filter { it.kind == MediaKind.VIDEO } else emptyList()) +
                (if (keep("documents")) docs + ctx.pFiles.filter { it.kind == MediaKind.DOCUMENT } else emptyList()) +
                (if (keep("others")) others + ctx.pFiles.filter { it.kind == MediaKind.OTHER } else emptyList()),
            recordings = if (keep("recordings")) recordings else emptyList(),
            planted = ctx.items.toList(),
            busyDay = tm.busyDay,
            hotelDay = tm.hotelDay,
        )
    }
}

/** Bulk contacts plus the pool of numbers that are in no contact. */
internal class ContactBook(val contacts: List<SeedContact>, val strangers: List<String>) {
    private val numbersOf: List<List<String>> = contacts.map { c ->
        c.numbers.map { Numbers.normalized(Numbers.lastTen(it)) }
    }

    /** Contact index skewed towards the first ones (a few contacts are called/texted a lot). */
    fun pickContactIndex(rng: SeedRandom): Int {
        val u = rng.nextDouble()
        return (contacts.size * u * u).toInt().coerceAtMost(contacts.size - 1)
    }

    /** A counterpart number: 70 % a contact's, else a stranger's. */
    fun person(rng: SeedRandom): String {
        if (contacts.isNotEmpty() && rng.nextDouble() < 0.70) {
            val n = numbersOf[pickContactIndex(rng)]
            return n[rng.nextInt(n.size)]
        }
        return strangers[rng.nextInt(strangers.size)]
    }

    companion object {
        fun build(ctx: GenContext): ContactBook {
            val rng = ctx.rngContacts
            val n = ctx.counts.contacts
            val plantedDeva = ctx.pContacts.count { hasDevanagari(it.displayName) }
            val deva = flags(rng, n, devaTarget(n, ctx.pContacts.size, plantedDeva, n))
            val usedNames = HashSet<String>(ctx.pContacts.map { it.displayName })
            val out = ArrayList<SeedContact>(n)
            for (i in 0 until n) {
                var name = if (deva[i]) {
                    Vocabulary.DEVA_FIRST_NAMES[rng.nextInt(Vocabulary.DEVA_FIRST_NAMES.size)] + " " +
                        Vocabulary.DEVA_LAST_NAMES[rng.nextInt(Vocabulary.DEVA_LAST_NAMES.size)]
                } else {
                    Vocabulary.FIRST_NAMES[rng.nextInt(Vocabulary.FIRST_NAMES.size)] + " " +
                        Vocabulary.LAST_NAMES[rng.nextInt(Vocabulary.LAST_NAMES.size)]
                }
                var suffix = 2
                val base = name
                while (!usedNames.add(name)) name = "$base ${suffix++}"
                val count = if (rng.chance(0.2)) 2 else 1
                val numbers = (0 until count).map {
                    Numbers.stored(Numbers.fresh(rng, ctx.usedNumbers), rng.nextInt(4))
                }
                out += SeedContact(i, name, numbers)
            }
            val strangerCount = maxOf(30, n / 2)
            val strangers = (0 until strangerCount).map { Numbers.normalized(Numbers.fresh(rng, ctx.usedNumbers)) }
            return ContactBook(out, strangers)
        }
    }
}

internal object CallGen {
    fun generate(ctx: GenContext, book: ContactBook, busy: Boolean): List<SeedCall> {
        val rng = ctx.rngCalls
        val tm = ctx.tm
        val out = ArrayList<SeedCall>(ctx.counts.calls + if (busy) BUSY_CALLS else 0)
        fun one(key: Int, t: LocalDateTime): SeedCall {
            val u = rng.nextDouble()
            val type = when {
                u < 0.38 -> 1
                u < 0.78 -> 2
                u < 0.96 -> 3
                else -> 5
            }
            val raw = book.person(rng)
            val number = if (rng.chance(0.1)) "0" + raw.takeLast(10) else raw
            val dur = if (type == 1 || type == 2) rng.lognormal(70.0, 1.1).roundToInt().coerceIn(5, 1500) else 0
            return SeedCall(key, number, 1, type, tm.ms(t), dur)
        }
        for (i in 0 until ctx.counts.calls) {
            var t = tm.random(rng)
            while (t.toLocalDate() == tm.trickyDay) t = tm.random(rng)
            out += one(i, t)
        }
        if (busy) {
            var key = PLANTED_BUSY_CALLS_BASE
            repeat(BUSY_CALLS) { out += one(key++, tm.onDay(rng, tm.busyDay)) }
        }
        return out
    }

    const val BUSY_CALLS = 120
    const val PLANTED_BUSY_CALLS_BASE = 2_000_000
}

internal object SmsGen {
    fun generate(ctx: GenContext, book: ContactBook, busy: Boolean): List<SeedSms> {
        val rng = ctx.rngSms
        val tm = ctx.tm
        val bulk = ctx.counts.sms
        val extra = if (busy) BUSY_SMS else 0
        val total = bulk + extra
        val plantedDeva = ctx.pSms.count { hasDevanagari(it.body) }
        val strata = SmsStrata(rng, total, devaTarget(total, ctx.pSms.size, plantedDeva, total))
        val out = ArrayList<SeedSms>(total)
        for (i in 0 until total) {
            val t = if (i < bulk) tm.random(rng) else tm.onDay(rng, tm.busyDay)
            val key = if (i < bulk) i else PLANTED_BUSY_SMS_BASE + (i - bulk)
            val u = rng.nextDouble()
            val type = when {
                u < 0.60 -> 1
                u < 0.97 -> 2
                u < 0.99 -> 5
                else -> 6
            }
            val (body, service) = smsBody(
                rng,
                strata.deva[i],
                strata.payment[i],
                strata.the[i],
                type != 1,
                Vocabulary.FIRST_NAMES,
            )
            val address = if (service) {
                Vocabulary.ALPHA_SENDERS[
                    rng.nextInt(
                        Vocabulary.ALPHA_SENDERS.size,
                    ),
                ]
            } else {
                book.person(rng)
            }
            out += SeedSms(key, address, body, type, tm.ms(t), address)
        }
        return out
    }

    const val BUSY_SMS = 300
    const val PLANTED_BUSY_SMS_BASE = 2_000_000
}

internal object MmsGen {
    fun generate(ctx: GenContext, book: ContactBook): List<SeedMms> {
        val rng = ctx.rngMms
        val tm = ctx.tm
        val out = ArrayList<SeedMms>(ctx.counts.mms)
        for (i in 0 until ctx.counts.mms) {
            val t = tm.random(rng)
            val box = if (rng.chance(0.6)) 1 else 2
            val addresses = ArrayList<String>()
            addresses += book.person(rng)
            if (rng.chance(0.30)) {
                val more = rng.nextInt(1, 4)
                while (addresses.size < 1 + more) {
                    val a = book.person(rng)
                    if (a !in addresses) addresses += a
                }
            }
            val devaText = rng.chance(0.15)
            val subject = if (rng.chance(0.20)) {
                if (devaText) {
                    Vocabulary.DEVA_MMS_SUBJECTS[
                        rng.nextInt(
                            Vocabulary.DEVA_MMS_SUBJECTS.size,
                        ),
                    ]
                } else {
                    Vocabulary.MMS_SUBJECTS[rng.nextInt(Vocabulary.MMS_SUBJECTS.size)]
                }
            } else {
                null
            }
            val images = rng.nextInt(3)
            val texts = if (rng.chance(0.85) || images == 0) {
                listOf(smsBody(rng, devaText, false, rng.chance(0.4), true, Vocabulary.FIRST_NAMES).first)
            } else {
                emptyList()
            }
            out += SeedMms(i, box, tm.ms(t), addresses, subject, texts, images)
        }
        return out
    }
}
