package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeneratorDeterminismTest {
    @Test
    fun `same inputs give the same canonical hash`() {
        val a = SeedManifest.sha256(TestPlans.build(Profile.SMALL))
        val b = SeedManifest.sha256(TestPlans.build(Profile.SMALL))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `small profile seed 42 hash is frozen`() {
        assertThat(SeedManifest.sha256(TestPlans.small)).isEqualTo(SMALL_42_HASH)
    }

    @Test
    fun `seed 43 differs`() {
        val other = SeedManifest.sha256(TestPlans.build(Profile.SMALL, seed = 43))
        assertThat(other).isNotEqualTo(SeedManifest.sha256(TestPlans.small))
    }

    @Test
    fun `different anchor or zone changes the plan`() {
        val moved = Generator(Profile.SMALL, 42, TestPlans.ANCHOR.plusDays(1), TestPlans.ZONE).plan()
        assertThat(SeedManifest.sha256(moved)).isNotEqualTo(SeedManifest.sha256(TestPlans.small))
    }

    @Test
    fun `restricting the kinds never changes the data of the kinds that stay`() {
        val full = TestPlans.small
        val callsOnly = TestPlans.build(Profile.SMALL, only = setOf("calls"))
        assertThat(callsOnly.calls).isEqualTo(full.calls)
        assertThat(callsOnly.sms).isEmpty()
        val smsOnly = TestPlans.build(Profile.SMALL, only = setOf("sms"))
        assertThat(smsOnly.sms).isEqualTo(full.sms)
        assertThat(smsOnly.calls).isEmpty()
        val filesOnly = TestPlans.build(Profile.SMALL, only = setOf("photos", "documents"))
        assertThat(filesOnly.files).containsExactlyElementsIn(
            full.files.filter { it.kind == MediaKind.PHOTO || it.kind == MediaKind.DOCUMENT },
        )
    }

    @Test
    fun `streams are independent per kind`() {
        // Bulk SMS (keys below the planted range) only depend on the SMS stream, the contacts and the planted
        // counts: a different seed changes them, but the same seed gives the same bulk SMS in every profile slice.
        val a = TestPlans.build(Profile.SMALL, only = setOf("sms")).sms.filter { it.key < PLANTED_KEY_BASE }
        val b = TestPlans.small.sms.filter { it.key < PLANTED_KEY_BASE }
        assertThat(a).isEqualTo(b)
        val salts = listOf(
            SALT_CONTACTS, SALT_CALLS, SALT_SMS, SALT_MMS, SALT_PHOTOS, SALT_VIDEOS, SALT_DOCUMENTS, SALT_OTHERS,
            SALT_RECORDINGS, SALT_PLANTED,
        )
        assertThat(salts.toSet()).hasSize(salts.size)
        val firsts = salts.map { SeedRandom(42L xor it).nextLong() }
        assertThat(firsts.toSet()).hasSize(salts.size)
    }

    private companion object {
        const val SMALL_42_HASH = "33921c5881b21e2edee3123ae2776fed8fb33d6bcc906ffa5d6e31e527656b5a"
    }
}
