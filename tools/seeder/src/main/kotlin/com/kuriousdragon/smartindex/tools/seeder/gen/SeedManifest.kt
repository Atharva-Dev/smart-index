package com.kuriousdragon.smartindex.tools.seeder.gen

import java.security.MessageDigest

/**
 * The seed manifest of LLD §4.9 and the canonical plan dump used for determinism checks. The JSON is written by
 * hand (no org.json: this code runs on the plain JVM in tests) with a stable key order.
 * `written`, `failed`, `phasesMs`, `rescanned`, `mtimeFailed` and `smsRole` are filled by SeedRun after the run.
 */
object SeedManifest {
    val KINDS = listOf("CONTACT", "CALL", "SMS", "MMS", "PHOTO", "VIDEO", "DOCUMENT", "OTHER_FILE", "RECORDING")
    val PHASES = listOf("CONTACTS", "CALLS", "SMS", "MMS", "PHOTOS", "VIDEOS", "DOCUMENTS", "OTHERS", "RECORDINGS")

    /** Planned row count per manifest kind (includes planted items). */
    fun planned(plan: SeedPlan): Map<String, Int> = linkedMapOf(
        "CONTACT" to plan.contacts.size,
        "CALL" to plan.calls.size,
        "SMS" to plan.sms.size,
        "MMS" to plan.mms.size,
        "PHOTO" to plan.files.count { it.kind == MediaKind.PHOTO },
        "VIDEO" to plan.files.count { it.kind == MediaKind.VIDEO },
        "DOCUMENT" to plan.files.count { it.kind == MediaKind.DOCUMENT },
        "OTHER_FILE" to plan.files.count { it.kind == MediaKind.OTHER },
        "RECORDING" to plan.recordings.size,
    )

    /** Files the app excludes by design: `.nomedia` folders and hidden names (HIDDEN), epoch mtime (INVALID_DATE). */
    fun expectedExcluded(plan: SeedPlan): Map<String, Int> {
        val noMediaDirs = plan.files.filter { it.displayName == ".nomedia" }.map { it.relativePath }.toSet()
        fun hidden(f: SeedFile) = f.displayName.startsWith(".") || f.relativePath in noMediaDirs
        return linkedMapOf(
            "HIDDEN" to plan.files.count(::hidden),
            "INVALID_DATE" to plan.files.count { !hidden(it) && it.modifiedMs <= 0L },
        )
    }

    fun build(
        plan: SeedPlan,
        written: Map<String, Int> = emptyMap(),
        failed: Map<String, Int> = emptyMap(),
        phasesMs: Map<String, Long> = emptyMap(),
        rescanned: Int = 0,
        mtimeFailed: Int = 0,
        sparse: Boolean = true,
        smsRolePrevious: String? = null,
        smsRoleHeldMs: Long = 0,
    ): String {
        val callByKey = plan.calls.associateBy { it.key }
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"generatorVersion\": ").append(GENERATOR_VERSION).append(", ")
        sb.append("\"profile\": ").append(str(plan.profile.name.lowercase())).append(", ")
        sb.append("\"seed\": ").append(plan.seed).append(", ")
        sb.append("\"anchor\": ").append(str(plan.anchor.toString())).append(", ")
        sb.append("\"zone\": ").append(str(plan.zone.id)).append(", ")
        sb.append("\"seedDay\": ").append(str(plan.anchor.toLocalDate().toString())).append(", ")
        sb.append("\"busyDay\": ").append(str(plan.busyDay.toString())).append(", ")
        sb.append("\"hotelDay\": ").append(str(plan.hotelDay.toString())).append(", ")
        sb.append("\"planned\": ").append(map(planned(plan))).append(", ")
        sb.append("\"written\": ").append(map(KINDS.associateWith { written[it] ?: 0 })).append(", ")
        sb.append("\"failed\": ").append(map(KINDS.associateWith { failed[it] ?: 0 })).append(", ")
        sb.append("\"expectedExcluded\": ").append(map(expectedExcluded(plan))).append(", ")
        sb.append("\"rescanned\": ").append(rescanned).append(", ")
        sb.append("\"mtimeFailed\": ").append(mtimeFailed).append(", ")
        sb.append("\"sparse\": ").append(sparse).append(", ")
        sb.append("\"expectedLinks\": ").append(plan.recordings.count { it.expectLinked }).append(", ")
        sb.append("\"recordingCases\": [")
        plan.recordings.forEachIndexed { i, r ->
            if (i > 0) sb.append(", ")
            val call = r.callKey?.let { callByKey[it] }
            sb.append("{\"file\": ").append(str(r.file.displayName))
            sb.append(", \"case\": ").append(str(r.case))
            sb.append(", \"expectLinked\": ").append(r.expectLinked)
            sb.append(", \"callStartMs\": ").append(call?.startMs ?: 0L)
            sb.append(", \"callNumber\": ").append(str(call?.number.orEmpty())).append('}')
        }
        sb.append("], \"planted\": [")
        plan.planted.forEachIndexed { i, p ->
            if (i > 0) sb.append(", ")
            sb.append("{\"id\": ").append(str(p.id)).append(", \"where\": ").append(str(p.where)).append('}')
        }
        sb.append("], \"phasesMs\": ").append(map(PHASES.associateWith { phasesMs[it] ?: 0L })).append(", ")
        sb.append("\"smsRole\": {\"previous\": ").append(smsRolePrevious?.let(::str) ?: "null")
        sb.append(", \"heldMs\": ").append(smsRoleHeldMs).append("}}")
        return sb.toString()
    }

    private fun map(m: Map<String, Any>): String =
        m.entries.joinToString(", ", "{", "}") { "${str(it.key)}: ${it.value}" }

    fun str(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    // ---- canonical dump ------------------------------------------------------------------------------------------

    private fun row(vararg fields: Any?): String = fields.joinToString("|")

    /** Every field of the plan as text lines, in plan order. */
    fun canonicalLines(plan: SeedPlan): Sequence<String> = sequence {
        yield(
            row(
                "plan",
                plan.profile,
                plan.seed,
                plan.anchor,
                plan.zone.id,
                plan.busyDay,
                plan.hotelDay,
                "v$GENERATOR_VERSION",
            ),
        )
        for (c in plan.contacts) yield(row("C", c.key, c.displayName, c.numbers.joinToString(";")))
        for (c in plan.calls) yield(row("L", c.key, c.number, c.presentation, c.type, c.startMs, c.durationSec))
        for (s in plan.sms) yield(row("S", s.key, s.address, s.type, s.dateMs, s.threadKey, s.body))
        for (m in plan.mms) {
            val addresses = m.addresses.joinToString(";")
            yield(row("M", m.key, m.box, m.dateMs, addresses, m.subject, m.texts.joinToString("¦"), m.imageParts))
        }
        for (f in plan.files) yield(fileLine(f))
        for (r in plan.recordings) {
            yield(
                row(
                    "R", fileLine(r.file), r.callKey, r.expectLinked, r.case, r.patternId, r.expectName,
                    r.expectNumber, r.localStart,
                ),
            )
        }
        for (p in plan.planted) yield(row("P", p.id, p.where))
    }

    private fun fileLine(f: SeedFile) = row(
        "F", f.key, f.kind, f.relativePath, f.displayName, f.mime, f.logicalSize, f.takenMs, f.modifiedMs,
        f.durationMs, f.payload,
    )

    /** SHA-256 (lower-case hex) of [canonicalLines] joined by '\n'. */
    fun sha256(plan: SeedPlan): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (line in canonicalLines(plan)) {
            md.update(line.toByteArray(Charsets.UTF_8))
            md.update('\n'.code.toByte())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
