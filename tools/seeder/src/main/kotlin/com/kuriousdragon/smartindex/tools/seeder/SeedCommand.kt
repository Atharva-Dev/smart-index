package com.kuriousdragon.smartindex.tools.seeder

import com.kuriousdragon.smartindex.tools.seeder.gen.Profile
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Items a seed run can write; `only` selects a subset (LLD §3.4). */
enum class SeedKind(val key: String) {
    CONTACTS("contacts"),
    CALLS("calls"),
    SMS("sms"),
    MMS("mms"),
    PHOTOS("photos"),
    VIDEOS("videos"),
    DOCUMENTS("documents"),
    OTHERS("others"),
    RECORDINGS("recordings"),
}

/** Item types of the `fresh` command (NFR-005). */
enum class FreshType { SMS, MMS, CALL, PHOTO, VIDEO, DOCUMENT, OTHER, RECORDING }

enum class SeedCmd(val key: String) {
    SEED("seed"),
    WIPE("wipe"),
    STATUS("status"),
    FRESH("fresh"),
    RESTORE_SMS("restore-sms"),
}

/** Thrown inside [SeedCommand.parse]; the message is the log line `error bad-arg <name>`. */
class BadArgException(val argName: String, detail: String) : IllegalArgumentException("bad-arg $argName: $detail")

data class SeedCommand(
    val cmd: SeedCmd,
    val profile: Profile,
    val seed: Long,
    val anchor: LocalDateTime,
    val only: Set<SeedKind>,
    val skipSms: Boolean,
    val sparse: Boolean,
    val freshType: FreshType?,
    val freshToken: String?,
) {
    /** True when the plan contains SMS or MMS rows, i.e. the default-SMS role is needed (LLD §3.4 role step 1). */
    val needsSmsRole: Boolean
        get() = when (cmd) {
            SeedCmd.SEED -> !skipSms && (SeedKind.SMS in only || SeedKind.MMS in only)
            SeedCmd.FRESH -> freshType == FreshType.SMS || freshType == FreshType.MMS
            else -> false
        }

    companion object {
        const val DEFAULT_SEED = 42L
        private val TOKEN = Regex("[A-Z0-9]{2,12}")

        /**
         * Parses the adb intent extras of LLD §3.4. Unknown values give a failure whose message starts with
         * `bad-arg <name>` ([BadArgException]). [now] supplies the default anchor (rounded down to the minute).
         */
        fun parse(
            extras: Map<String, Any?>,
            now: () -> LocalDateTime = { LocalDateTime.now() },
        ): Result<SeedCommand> = runCatching {
            val cmd = enumArg(extras, "cmd", "seed") { v -> SeedCmd.entries.firstOrNull { it.key == v } }
            val profile = enumArg(extras, "profile", "small") { Profile.parse(it) }
            val seed = when (val raw = extras["seed"]) {
                null -> DEFAULT_SEED
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull() ?: throw BadArgException("seed", "not a number: $raw")
                else -> throw BadArgException("seed", "not a number: $raw")
            }
            val anchor = when (val raw = extras["anchor"]) {
                null -> now().truncatedTo(ChronoUnit.MINUTES)

                is String -> runCatching { LocalDateTime.parse(raw.trim()) }
                    .getOrElse { throw BadArgException("anchor", "not an ISO local date-time: $raw") }

                else -> throw BadArgException("anchor", "not a string: $raw")
            }
            val only = parseOnly(extras["only"])
            val skipSms = boolArg(extras, "skipSms", false)
            val sparse = boolArg(extras, "sparse", true)
            val freshType = (extras["type"] as? String)?.let { v ->
                FreshType.entries.firstOrNull { it.name == v.trim().uppercase() }
                    ?: throw BadArgException("type", "unknown type: $v")
            }
            val freshToken = (extras["token"] as? String)?.let { v ->
                if (!TOKEN.matches(v)) throw BadArgException("token", "must match [A-Z0-9]{2,12}: $v")
                v
            }
            if (cmd == SeedCmd.FRESH) {
                if (freshType == null) throw BadArgException("type", "required for fresh")
                if (freshToken == null) throw BadArgException("token", "required for fresh")
            }
            SeedCommand(cmd, profile, seed, anchor, only, skipSms, sparse, freshType, freshToken)
        }

        private fun <T> enumArg(extras: Map<String, Any?>, name: String, default: String, find: (String) -> T?): T {
            val raw = extras[name] ?: default
            val v = (raw as? String)?.trim()?.lowercase() ?: throw BadArgException(name, "not a string: $raw")
            return find(v) ?: throw BadArgException(name, "unknown value: $raw")
        }

        private fun boolArg(extras: Map<String, Any?>, name: String, default: Boolean): Boolean =
            when (val raw = extras[name]) {
                null -> default

                is Boolean -> raw

                is String -> when (raw.trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throw BadArgException(name, "not a boolean: $raw")
                }

                else -> throw BadArgException(name, "not a boolean: $raw")
            }

        private fun parseOnly(raw: Any?): Set<SeedKind> {
            if (raw == null) return SeedKind.entries.toSet()
            val text = raw as? String ?: throw BadArgException("only", "not a string: $raw")
            val kinds = text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { k ->
                SeedKind.entries.firstOrNull { it.key == k } ?: throw BadArgException("only", "unknown kind: $k")
            }
            if (kinds.isEmpty()) throw BadArgException("only", "empty list")
            return kinds.toSet()
        }
    }
}
