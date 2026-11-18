package com.kuriousdragon.smartindex.tools.seeder

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.tools.seeder.gen.Profile
import java.time.LocalDateTime
import org.junit.Test

class SeedCommandTest {
    private val now = LocalDateTime.of(2026, 11, 4, 18, 8, 59, 123)

    private fun parse(vararg extras: Pair<String, Any?>) = SeedCommand.parse(extras.toMap()) { now }

    @Test
    fun `defaults apply when no extras are given`() {
        val c = parse().getOrThrow()
        assertThat(c.cmd).isEqualTo(SeedCmd.SEED)
        assertThat(c.profile).isEqualTo(Profile.SMALL)
        assertThat(c.seed).isEqualTo(42L)
        assertThat(c.anchor).isEqualTo(LocalDateTime.of(2026, 11, 4, 18, 8))
        assertThat(c.only).containsExactlyElementsIn(SeedKind.entries)
        assertThat(c.skipSms).isFalse()
        assertThat(c.sparse).isTrue()
        assertThat(c.freshType).isNull()
        assertThat(c.freshToken).isNull()
    }

    @Test
    fun `every cmd value is accepted`() {
        val expected = mapOf(
            "seed" to SeedCmd.SEED,
            "wipe" to SeedCmd.WIPE,
            "status" to SeedCmd.STATUS,
            "restore-sms" to SeedCmd.RESTORE_SMS,
        )
        expected.forEach { (k, v) -> assertThat(parse("cmd" to k).getOrThrow().cmd).isEqualTo(v) }
        val fresh = parse("cmd" to "fresh", "type" to "DOCUMENT", "token" to "F01").getOrThrow()
        assertThat(fresh.cmd).isEqualTo(SeedCmd.FRESH)
    }

    @Test
    fun `profile names and aliases map to the enum`() {
        assertThat(parse("profile" to "small").getOrThrow().profile).isEqualTo(Profile.SMALL)
        assertThat(parse("profile" to "smoke").getOrThrow().profile).isEqualTo(Profile.SMALL)
        assertThat(parse("profile" to "nfr002").getOrThrow().profile).isEqualTo(Profile.NFR002)
        assertThat(parse("profile" to "full").getOrThrow().profile).isEqualTo(Profile.NFR002)
        assertThat(parse("profile" to "edge").getOrThrow().profile).isEqualTo(Profile.EDGE)
    }

    @Test
    fun `seed accepts long int and string`() {
        assertThat(parse("seed" to 7L).getOrThrow().seed).isEqualTo(7L)
        assertThat(parse("seed" to 8).getOrThrow().seed).isEqualTo(8L)
        assertThat(parse("seed" to "9").getOrThrow().seed).isEqualTo(9L)
    }

    @Test
    fun `anchor is an ISO local date-time`() {
        val c = parse("anchor" to "2026-11-01T12:00").getOrThrow()
        assertThat(c.anchor).isEqualTo(LocalDateTime.of(2026, 11, 1, 12, 0))
    }

    @Test
    fun `only is a comma list of kinds`() {
        val c = parse("only" to "calls, sms").getOrThrow()
        assertThat(c.only).containsExactly(SeedKind.CALLS, SeedKind.SMS)
        assertThat(
            parse("only" to "contacts,calls,sms,mms,photos,videos,documents,others,recordings").getOrThrow().only,
        )
            .containsExactlyElementsIn(SeedKind.entries)
    }

    @Test
    fun `booleans accept boolean and string values`() {
        val c = parse("skipSms" to true, "sparse" to "false").getOrThrow()
        assertThat(c.skipSms).isTrue()
        assertThat(c.sparse).isFalse()
    }

    @Test
    fun `fresh accepts every type and a valid token`() {
        FreshType.entries.forEach { t ->
            val c = parse("cmd" to "fresh", "type" to t.name, "token" to "AB12").getOrThrow()
            assertThat(c.freshType).isEqualTo(t)
            assertThat(c.freshToken).isEqualTo("AB12")
        }
    }

    @Test
    fun `bad profile gives a bad-arg message`() {
        val e = parse("profile" to "huge").exceptionOrNull()
        assertThat(e).isInstanceOf(BadArgException::class.java)
        assertThat(e).hasMessageThat().startsWith("bad-arg profile")
    }

    @Test
    fun `bad type gives a bad-arg message`() {
        assertThat(parse("cmd" to "fresh", "type" to "PIZZA", "token" to "F01").exceptionOrNull())
            .hasMessageThat().startsWith("bad-arg type")
    }

    @Test
    fun `bad token gives a bad-arg message`() {
        listOf("a", "lower", "X", "TOOLONGTOKEN123", "AB-C").forEach { token ->
            assertThat(parse("cmd" to "fresh", "type" to "SMS", "token" to token).exceptionOrNull())
                .hasMessageThat().startsWith("bad-arg token")
        }
    }

    @Test
    fun `fresh requires type and token`() {
        assertThat(parse("cmd" to "fresh").exceptionOrNull()).hasMessageThat().startsWith("bad-arg type")
        assertThat(
            parse("cmd" to "fresh", "type" to "SMS").exceptionOrNull(),
        ).hasMessageThat().startsWith("bad-arg token")
    }

    @Test
    fun `bad cmd, seed, anchor, only and boolean are rejected`() {
        assertThat(parse("cmd" to "explode").exceptionOrNull()).hasMessageThat().startsWith("bad-arg cmd")
        assertThat(parse("seed" to "abc").exceptionOrNull()).hasMessageThat().startsWith("bad-arg seed")
        assertThat(parse("anchor" to "yesterday").exceptionOrNull()).hasMessageThat().startsWith("bad-arg anchor")
        assertThat(parse("only" to "calls,fax").exceptionOrNull()).hasMessageThat().startsWith("bad-arg only")
        assertThat(parse("skipSms" to "maybe").exceptionOrNull()).hasMessageThat().startsWith("bad-arg skipSms")
    }

    @Test
    fun `SMS role is needed only for SMS or MMS seeding`() {
        assertThat(parse().getOrThrow().needsSmsRole).isTrue()
        assertThat(parse("skipSms" to true).getOrThrow().needsSmsRole).isFalse()
        assertThat(parse("only" to "calls").getOrThrow().needsSmsRole).isFalse()
        assertThat(parse("cmd" to "status").getOrThrow().needsSmsRole).isFalse()
    }
}
