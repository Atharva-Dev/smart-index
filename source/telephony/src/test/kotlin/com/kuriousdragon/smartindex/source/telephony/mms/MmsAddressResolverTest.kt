package com.kuriousdragon.smartindex.source.telephony.mms

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addAddr
import com.kuriousdragon.smartindex.source.telephony.testing.addCanonical
import com.kuriousdragon.smartindex.source.telephony.testing.addThread
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsAddressResolverTest {
    private val providers = FakeTelephonyProviders()
    private val stats = RunStatsBuilder(Source.MMS, Mode.FULL)

    private fun resolver(budget: Int = 5_000) = MmsAddressResolver(providers, budget)

    private fun row(id: Long, thread: Long? = 1, box: Int = 1) = MmsRow(id, thread, 1_700_000_000L, box, null, null)

    private fun MmsAddressResolver.one(row: MmsRow) = resolve(listOf(row), stats).getValue(row.id)

    private val addrQueries get() = providers.queries.filter { it.uri.toString().endsWith("/addr") }

    private fun group() {
        providers.addCanonical(1, "+919876500001")
        providers.addCanonical(2, "+919876500002")
        providers.addCanonical(3, "+919876500003")
        providers.addThread(5, "1 2 3")
    }

    @Test
    fun `a one-to-one thread gives that participant for received and sent without addr queries`() {
        providers.addCanonical(1, "+919876500001")
        providers.addThread(5, "1")
        val r = resolver()
        assertThat(r.one(row(1, thread = 5, box = 1))).isEqualTo(MmsParticipants("+919876500001", emptyList()))
        assertThat(r.one(row(2, thread = 5, box = 2))).isEqualTo(MmsParticipants("+919876500001", emptyList()))
        assertThat(addrQueries).isEmpty()
    }

    @Test
    fun `a group thread sent gives the first participant and n minus 1 others without addr queries`() {
        group()
        assertThat(
            resolver().one(row(1, thread = 5, box = 2)),
        ).isEqualTo(MmsParticipants("+919876500001", listOf("+919876500002", "+919876500003")))
        assertThat(addrQueries).isEmpty()
    }

    @Test
    fun `a group thread received uses the per-id FROM and n minus 1 others when the sender is a participant`() {
        group()
        providers.addAddr(1, 1, "insert-address-token", 151)
        providers.addAddr(2, 1, "+919876500002", 137)
        providers.addAddr(3, 1, "+919876500003", 151)
        val p = resolver().one(row(1, thread = 5, box = 1))

        assertThat(p).isEqualTo(MmsParticipants("+919876500002", listOf("+919876500001", "+919876500003")))
        assertThat(addrQueries).hasSize(1)
        val spec = addrQueries.single()
        assertThat(spec.selection).isEqualTo("type IN (137,151)")
        assertThat(spec.projection).containsExactly("address", "type").inOrder()
        assertThat(spec.sortOrder).isEqualTo("_id ASC")
    }

    @Test
    fun `a sender who is not a thread participant gives others equal to n`() {
        group()
        providers.addAddr(1, 1, "+14155550123", 137)
        assertThat(
            resolver().one(row(1, thread = 5, box = 1)),
        ).isEqualTo(MmsParticipants("+14155550123", listOf("+919876500001", "+919876500002", "+919876500003")))
    }

    @Test
    fun `duplicates, blanks and the counterpart collapse out of others`() {
        providers.addCanonical(1, "+919876500001")
        providers.addCanonical(2, "+91 98765 00002")
        providers.addCanonical(3, "09876500002")
        providers.addCanonical(4, "+919876500001")
        providers.addThread(6, "1 2 3 4")
        assertThat(resolver().one(row(1, thread = 6, box = 2)))
            .isEqualTo(MmsParticipants("+919876500001", listOf("+91 98765 00002")))
    }

    @Test
    fun `a received message from a participant leaves that participant out of others`() {
        group()
        providers.addAddr(1, 1, "+91 98765 00003", 137)
        val p = resolver().one(row(1, thread = 5, box = 1))
        assertThat(p.counterpart).isEqualTo("+91 98765 00003")
        assertThat(p.others).containsExactly("+919876500001", "+919876500002").inOrder()
        assertThat(p.othersCount).isEqualTo(2)
    }

    @Test
    fun `a single participant has no others`() {
        providers.addCanonical(1, "+919876500001")
        providers.addThread(5, "1")
        assertThat(resolver().one(row(1, thread = 5, box = 2)).others).isEmpty()
    }

    @Test
    fun `an unknown thread falls back to the per-id FROM for received and first TO for sent`() {
        providers.addAddr(1, 1, "+919876500009", 137)
        providers.addAddr(2, 2, "+919876500001", 151)
        providers.addAddr(3, 2, "+919876500002", 151)
        providers.addAddr(4, 2, "+919876500003", 151)
        val r = resolver()

        assertThat(r.one(row(1, thread = 99, box = 1))).isEqualTo(MmsParticipants("+919876500009", emptyList()))
        assertThat(r.one(row(2, thread = 99, box = 2)))
            .isEqualTo(MmsParticipants("+919876500001", listOf("+919876500002", "+919876500003")))
        assertThat(r.one(row(3, thread = null, box = 1))).isEqualTo(MmsParticipants(null, emptyList()))
    }

    @Test
    fun `the insert-address-token is never a counterpart`() {
        providers.addAddr(1, 1, "insert-address-token", 137)
        providers.addAddr(2, 2, "insert-address-token", 151)
        val r = resolver()
        assertThat(r.one(row(1, thread = 99, box = 1))).isEqualTo(MmsParticipants(null, emptyList()))
        assertThat(r.one(row(2, thread = 99, box = 2))).isEqualTo(MmsParticipants(null, emptyList()))
    }

    @Test
    fun `an exhausted budget gives no counterpart and notes addr_budget`() {
        for (id in 1L..4L) providers.addAddr(id, id, "+91987650000$id", 137)
        val result = resolver(budget = 2).resolve((1L..4L).map { row(it, thread = 99) }, stats)

        assertThat(result.getValue(1).counterpart).isEqualTo("+919876500001")
        assertThat(result.getValue(2).counterpart).isEqualTo("+919876500002")
        assertThat(result.getValue(3)).isEqualTo(MmsParticipants(null, emptyList()))
        assertThat(result.getValue(4)).isEqualTo(MmsParticipants(null, emptyList()))
        assertThat(stats.notes["addr_budget"]).isEqualTo(2)
        assertThat(addrQueries).hasSize(2)
    }

    @Test
    fun `a failing threads query switches to per-id lookups and notes threads_unavailable`() {
        group()
        providers.failOn("content://mms-sms/conversations", IllegalStateException("boom"))
        providers.addAddr(1, 1, "+919876500002", 137)
        val p = resolver().one(row(1, thread = 5, box = 1))

        assertThat(p).isEqualTo(MmsParticipants("+919876500002", emptyList()))
        assertThat(stats.notes["threads_unavailable"]).isEqualTo(1)
    }

    @Test
    fun `a null canonical-addresses cursor also switches to per-id lookups`() {
        group()
        providers.returnNullFor("content://mms-sms/canonical-addresses")
        providers.addAddr(1, 1, "+919876500002", 137)
        assertThat(resolver().one(row(1, thread = 5, box = 1)).othersCount).isEqualTo(0)
        assertThat(stats.notes["threads_unavailable"]).isEqualTo(1)
    }

    @Test
    fun `a SecurityException from the threads query is rethrown`() {
        providers.failOn("content://mms-sms", SecurityException("no"))
        assertThrows(SecurityException::class.java) { resolver().one(row(1)) }
    }

    @Test
    fun `a SecurityException from an addr query is rethrown`() {
        providers.failOn("content://mms/1/addr", SecurityException("no"))
        assertThrows(SecurityException::class.java) { resolver().one(row(1, thread = 99)) }
    }

    @Test
    fun `threads and canonical addresses are loaded once per run and again after reset`() {
        group()
        val r = resolver()
        r.one(row(1, thread = 5, box = 2))
        r.resolve(listOf(row(2, thread = 5, box = 2), row(3, thread = 5, box = 2)), stats)
        fun loads() = providers.queries.count { it.uri.toString().startsWith("content://mms-sms") }
        assertThat(loads()).isEqualTo(2)

        r.reset()
        r.one(row(4, thread = 5, box = 2))
        assertThat(loads()).isEqualTo(4)
        val threads = providers.queries.first { it.uri.path == "/conversations" }
        assertThat(threads.uri.getQueryParameter("simple")).isEqualTo("true")
    }

    @Test
    fun `reset restores the per-id budget`() {
        providers.addAddr(1, 1, "+919876500001", 137)
        val r = resolver(budget = 1)
        assertThat(r.one(row(1, thread = 99)).counterpart).isNotNull()
        assertThat(r.one(row(1, thread = 99)).counterpart).isNull()
        r.reset()
        assertThat(r.one(row(1, thread = 99)).counterpart).isNotNull()
    }
}
