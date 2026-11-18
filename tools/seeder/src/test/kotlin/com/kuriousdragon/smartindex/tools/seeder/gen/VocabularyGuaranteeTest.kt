package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VocabularyGuaranteeTest {
    /** Texts of the planted items that may contain a forbidden substring. */
    private val plantedTexts = setOf(
        "Booked Hotel Sea View for Friday",
        "see you at the hotel sea view lobby",
        "Rs 4,500 spent on card XX1234 at SEA VIEW RESORT on %s",
        "Sea_View.pdf",
        "SeaView.jpg",
        "Your booking reference is SIDX-4711",
        "notes_qz.txt",
        "qzbox/",
        "archive.qz",
        "Qzin Test",
        "code qz7",
        "kumquat jam recipe",
        "kumquat_notes.txt",
        "Kumquat Test",
    )

    private fun forbidden(s: String) = Vocabulary.FORBIDDEN.any { s.contains(it, ignoreCase = true) }

    private fun allowed(s: String) =
        plantedTexts.any { s.contains(it) || (it.contains("%s") && s.startsWith(it.substringBefore("%s"))) }

    private fun texts(plan: SeedPlan, step: Int): List<String> {
        val out = ArrayList<String>()
        fun <T> sample(items: List<T>) = items.filterIndexed { i, _ -> i % step == 0 }
        sample(plan.sms).forEach { out += it.body }
        sample(plan.contacts).forEach { out += it.displayName }
        sample(plan.mms).forEach { m ->
            out += m.texts
            m.subject?.let { out += it }
        }
        sample(plan.files).forEach { f ->
            out += f.relativePath + f.displayName
            (f.payload as? Payload.Doc)?.text?.let { out += it }
        }
        sample(plan.recordings).forEach { out += it.file.relativePath + it.file.displayName }
        return out
    }

    private fun assertOnlyPlanted(plan: SeedPlan, step: Int) {
        val offenders = texts(plan, step).filter(::forbidden)
        for (o in offenders) assertThat(allowed(o)).isTrue()
        if (step == 1) {
            // every planted item does show up
            assertThat(offenders.size).isAtLeast(plantedTexts.size - 1)
        }
    }

    @Test
    fun `forbidden substrings occur only in planted items in the whole small plan`() =
        assertOnlyPlanted(TestPlans.small, 1)

    @Test
    fun `forbidden substrings occur only in planted items in every 10th item of nfr002`() =
        assertOnlyPlanted(TestPlans.nfr002, 10)

    @Test
    fun `vocabulary lists and templates never contain a forbidden substring`() {
        val all = Vocabulary.ALL_TEMPLATES.map { it.text } + Vocabulary.FIRST_NAMES + Vocabulary.LAST_NAMES +
            Vocabulary.MERCHANTS + Vocabulary.PLACES + Vocabulary.ALPHA_SENDERS +
            Vocabulary.DOC_STEMS.values.flatten() +
            Vocabulary.OTHER_STEMS + Vocabulary.MMS_SUBJECTS + Vocabulary.MONTHS + Vocabulary.PROJECTS
        assertThat(all.filter(::forbidden)).isEmpty()
        // no bulk word that the planted searches rely on
        for (w in listOf("invoice", "sea", "view", "hotel", "booking")) {
            val hits = (Vocabulary.ALL_TEMPLATES.map { it.text } + Vocabulary.PLACES + Vocabulary.MERCHANTS)
                .filter { Regex("\\b$w\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            assertThat(hits).isEmpty()
        }
    }

    private fun isEnglish(s: String) = !hasDevanagari(s)

    @Test
    fun `the appears in about 40 percent of English sms and payment in about 8 percent of all sms`() {
        for (plan in listOf(TestPlans.small, TestPlans.nfr002)) {
            val english = plan.sms.map { it.body }.filter(::isEnglish)
            val the = english.count { it.contains("the", ignoreCase = true) }.toDouble() / english.size
            println("${plan.profile}: the=$the")
            assertThat(the).isAtLeast(0.35)
            assertThat(the).isAtMost(0.45)
            val payment = plan.sms.count { it.body.contains("payment", ignoreCase = true) }.toDouble() / plan.sms.size
            println("${plan.profile}: payment=$payment")
            assertThat(payment).isAtLeast(0.06)
            assertThat(payment).isAtMost(0.10)
        }
    }

    @Test
    fun `invoice appears in exactly three sms of the Meera conversation and in no other sms`() {
        val plan = TestPlans.small
        val meera = plan.contacts.single { it.displayName == "Meera Kulkarni" }
        val addr = Numbers.normalized(Numbers.lastTen(meera.numbers[0]))
        val conv = plan.sms.filter { it.address == addr }
        assertThat(conv).hasSize(PlantedBuilder.P12_COUNT)
        assertThat(conv.count { it.body.contains("invoice", ignoreCase = true) }).isEqualTo(3)
        assertThat(plan.sms.count { it.body.contains("invoice", ignoreCase = true) }).isEqualTo(3)
    }

    @Test
    fun `service sms of the bulk come from the fictional alphanumeric senders`() {
        val senders = TestPlans.small.sms.map { it.address }.filter { it.any(Char::isLetter) }.toSet()
        assertThat(senders).containsAtLeastElementsIn(listOf("VM-SIDXBK"))
        assertThat(Vocabulary.ALPHA_SENDERS).containsAtLeast("VM-SIDXBK", "AX-SIDXPAY", "JD-SIDXKART")
    }

    @Test
    fun `every call carries the wipe marker and every contact a source id`() {
        val plan = TestPlans.small
        assertThat(plan.calls.map { it.phoneAccountId }.toSet()).containsExactly("sidx-seeder")
        assertThat(plan.contacts.all { it.sourceId == "sidx-seeder:${it.key}" }).isTrue()
    }

    @Test
    fun `sms types follow the 60 37 2 1 split roughly`() {
        val bulk = TestPlans.nfr002.sms.filter { it.key < PLANTED_KEY_BASE }
        fun share(t: Int) = bulk.count { it.type == t }.toDouble() / bulk.size
        assertThat(share(1)).isWithin(0.01).of(0.60)
        assertThat(share(2)).isWithin(0.01).of(0.37)
        assertThat(share(5)).isWithin(0.005).of(0.02)
        assertThat(share(6)).isWithin(0.005).of(0.01)
    }

    @Test
    fun `thirty percent of calls use numbers that are in no contact`() {
        val plan = TestPlans.nfr002
        val known = plan.contacts.flatMap { c -> c.numbers.map { Numbers.lastTen(it) } }.toSet()
        val bulk = plan.calls.filter { it.key < PLANTED_KEY_BASE }
        val unknown = bulk.count { Numbers.lastTen(it.number!!) !in known }.toDouble() / bulk.size
        assertThat(unknown).isWithin(0.03).of(0.30)
    }
}
