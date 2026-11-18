package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/** Bulk photos, videos, documents and other files (LLD §4.3, §4.5, §4.7). */
internal object FileGen {
    const val BUSY_PHOTOS = 250
    const val BUSY_VIDEOS = 30
    const val BUSY_DOCUMENTS = 100
    const val BUSY_OTHERS = 50
    private const val BUSY_PHOTO_KEY = 2_000_000
    private const val BUSY_VIDEO_KEY = 2_100_000
    private const val BUSY_DOCUMENT_KEY = 2_200_000
    private const val BUSY_OTHER_KEY = 2_300_000
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val STAMP_DASH = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun <T> weighted(rng: SeedRandom, items: List<Pair<T, Int>>): T {
        var r = rng.nextInt(items.sumOf { it.second })
        for ((item, w) in items) {
            if (r < w) return item
            r -= w
        }
        return items.last().first
    }

    /** Moves [t] by one second (backwards when forwards would leave the day) until [free] holds. */
    private fun uniqueTime(t0: LocalDateTime, free: (LocalDateTime) -> Boolean): LocalDateTime {
        var t = t0
        while (!free(t)) {
            val next = t.plusSeconds(1)
            t = if (next.toLocalDate() == t.toLocalDate()) next else t.minusSeconds(2)
        }
        return t
    }

    private fun time(ctx: GenContext, rng: SeedRandom, i: Int, bulk: Int): LocalDateTime =
        if (i < bulk) ctx.tm.random(rng) else ctx.tm.onDay(rng, ctx.tm.busyDay)

    fun photos(ctx: GenContext, base: Int, busy: Boolean): List<SeedFile> {
        val rng = ctx.rngPhotos
        val bulk = ctx.counts.photos
        val total = bulk + if (busy) BUSY_PHOTOS else 0
        val waCounter = HashMap<LocalDate, Int>()
        val out = ArrayList<SeedFile>(total)
        for (i in 0 until total) {
            val key = if (i < bulk) base + i else BUSY_PHOTO_KEY + (i - bulk)
            var t = time(ctx, rng, i, bulk)
            val roll = rng.nextInt(100)
            val dir: String
            val name: (LocalDateTime) -> String
            when {
                roll < 70 -> {
                    dir = "DCIM/Camera/"
                    name = { "${it.format(STAMP)}.jpg" }
                }

                roll < 85 -> {
                    dir = "Pictures/Screenshots/"
                    name = { "Screenshot_${it.format(STAMP_DASH)}_Gallery.jpg" }
                }

                else -> {
                    dir = "Pictures/WhatsApp/"
                    name = {
                        val n = waCounter.merge(it.toLocalDate(), 1, Int::plus)!!
                        "IMG-${it.format(DAY)}-WA%04d.jpg".format(n)
                    }
                }
            }
            val file: String
            if (dir == "Pictures/WhatsApp/") {
                file = name(t)
            } else {
                t = uniqueTime(t) { (dir + name(it)) !in ctx.usedPaths }
                file = name(t)
            }
            val ms = ctx.tm.ms(t)
            val f = SeedFile(
                key, MediaKind.PHOTO, dir, file, "image/jpeg", 1024L + rng.nextInt(1024), ms, ms, null,
                Payload.Jpeg(rng.nextInt(12)),
            )
            ctx.reserve(f)
            out += f
        }
        return out
    }

    fun videos(ctx: GenContext, base: Int, busy: Boolean): List<SeedFile> {
        val rng = ctx.rngVideos
        val bulk = ctx.counts.videos
        val total = bulk + if (busy) BUSY_VIDEOS else 0
        val out = ArrayList<SeedFile>(total)
        val durations = longArrayOf(5_000, 30_000, 90_000)
        for (i in 0 until total) {
            val key = if (i < bulk) base + i else BUSY_VIDEO_KEY + (i - bulk)
            val camera = rng.nextInt(100) < 85
            val dir = if (camera) "DCIM/Camera/" else "Movies/"
            val prefix = if (camera) "" else "VID_"
            val t = uniqueTime(time(ctx, rng, i, bulk)) { (dir + prefix + it.format(STAMP) + ".mp4") !in ctx.usedPaths }
            val template = rng.nextInt(3)
            val ms = ctx.tm.ms(t)
            val f = SeedFile(
                key, MediaKind.VIDEO, dir, "$prefix${t.format(STAMP)}.mp4", "video/mp4", 20_000L + rng.nextInt(40_001),
                ms, ms, durations[template], Payload.Mp4(template),
            )
            ctx.reserve(f)
            out += f
        }
        return out
    }

    private fun fillStem(stem: String, t: LocalDateTime, rng: SeedRandom): String =
        stem.replace("{yyyy}", t.year.toString())
            .replace("{mm}", "%02d".format(t.monthValue))
            .replace("{Mon}", Vocabulary.MONTHS[t.monthValue - 1])
            .replace("{n}", rng.nextInt(1, 19).toString())

    private fun unique(ctx: GenContext, dir: String, base: String, ext: String, dups: HashMap<String, Int>): String {
        val dot = if (ext.isEmpty()) "" else ".$ext"
        var name = base + dot
        while (!ctx.usedPaths.add(dir + name)) {
            val n = dups.merge(dir + base, 1, Int::plus)!! + 1
            name = "$base ($n)$dot"
        }
        return name
    }

    fun documents(ctx: GenContext, base: Int, busy: Boolean): List<SeedFile> {
        val rng = ctx.rngDocuments
        val bulk = ctx.counts.documents
        val total = bulk + if (busy) BUSY_DOCUMENTS else 0
        val planted = ctx.pFiles.filter { it.kind == MediaKind.DOCUMENT }
        val deva =
            flags(rng, total, devaTarget(total, planted.size, planted.count { hasDevanagari(it.displayName) }, total))
        val dups = HashMap<String, Int>()
        val out = ArrayList<SeedFile>(total)
        for (i in 0 until total) {
            val key = if (i < bulk) base + i else BUSY_DOCUMENT_KEY + (i - bulk)
            val t = time(ctx, rng, i, bulk)
            val ext = weighted(rng, Vocabulary.DOC_EXT_MIX)
            var dir = weighted(rng, Vocabulary.DOC_FOLDERS)
            if ("{project}" in
                dir
            ) {
                dir = dir.replace("{project}", Vocabulary.PROJECTS[rng.nextInt(Vocabulary.PROJECTS.size)])
            }
            val stems = Vocabulary.DOC_STEMS.getValue(ext)
            val stem = if (deva[i]) {
                Vocabulary.DEVA_DOC_STEMS[rng.nextInt(Vocabulary.DEVA_DOC_STEMS.size)]
            } else {
                stems[rng.nextInt(stems.size)]
            }
            val name = unique(ctx, dir, fillStem(stem, t, rng), ext, dups)
            val size = rng.lognormal(180_000.0, 1.2).roundToLong().coerceIn(1_000L, 20_000_000L)
            out += SeedFile(
                key, MediaKind.DOCUMENT, dir, name, Vocabulary.mimeOf(ext), size, null, ctx.tm.ms(t), null,
                Payload.Doc(ext, null),
            )
        }
        return out
    }

    fun others(ctx: GenContext, base: Int, busy: Boolean): List<SeedFile> {
        val rng = ctx.rngOthers
        val bulk = ctx.counts.others
        val total = bulk + if (busy) BUSY_OTHERS else 0
        val planted = ctx.pFiles.filter { it.kind == MediaKind.OTHER }
        val deva =
            flags(rng, total, devaTarget(total, planted.size, planted.count { hasDevanagari(it.displayName) }, total))
        val dups = HashMap<String, Int>()
        val out = ArrayList<SeedFile>(total)
        val mix = Vocabulary.OTHER_MIX.map { it to it.second }
        for (i in 0 until total) {
            val key = if (i < bulk) base + i else BUSY_OTHER_KEY + (i - bulk)
            val t = time(ctx, rng, i, bulk)
            val (ext, _, dir) = weighted(rng, mix)
            val n = rng.nextInt(1, 999)
            val stem = if (deva[i]) {
                "${Vocabulary.DEVA_OTHER_STEMS[rng.nextInt(Vocabulary.DEVA_OTHER_STEMS.size)]}_$n"
            } else if (ext == "m4a") {
                "New recording $n"
            } else {
                "${Vocabulary.OTHER_STEMS[rng.nextInt(Vocabulary.OTHER_STEMS.size)]}_$n"
            }
            val name = unique(ctx, dir, stem, ext, dups)
            val modMs = ctx.tm.ms(t)
            if (ext == "m4a") {
                val dur = 10_000L + rng.nextInt(300_000)
                out += SeedFile(key, MediaKind.OTHER, dir, name, "audio/mp4", dur * 2, null, modMs, dur, Payload.M4a)
            } else {
                val size = rng.lognormal(1_500_000.0, 1.5).roundToLong().coerceIn(1_000L, 200_000_000L)
                out +=
                    SeedFile(
                        key, MediaKind.OTHER, dir, name,
                        Vocabulary.mimeOf(
                            ext,
                        ),
                        size, null, modMs, null, Payload.Doc(ext, null),
                    )
            }
        }
        return out
    }
}
