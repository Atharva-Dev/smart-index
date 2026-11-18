package com.kuriousdragon.smartindex.source.telephony.calls

import android.provider.CallLog.Calls
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.telephony.common.NumberDisplay
import com.kuriousdragon.smartindex.source.telephony.common.RecordHasher
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyTitles
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallRowMapperTest {
    private val country = CountryIsoSource { "IN" }
    private val mapper = CallRowMapper(NumberDisplay(country, DefaultPhoneNumberNormalizer(country)))
    private val stats = RunStatsBuilder(Source.CALL_LOG, Mode.FULL)

    private fun row(
        id: Long = 7,
        number: String? = "+91 98765 43210",
        date: Long? = 1_700_000_000_000L,
        duration: Long? = 30,
        type: Int? = Calls.INCOMING_TYPE,
        presentation: Int? = Calls.PRESENTATION_ALLOWED,
    ) = CallRow(id, number, date, duration, type, presentation)

    @Test
    fun `every supported type maps to its direction`() {
        val expected = mapOf(
            Calls.INCOMING_TYPE to Direction.INCOMING,
            Calls.OUTGOING_TYPE to Direction.OUTGOING,
            Calls.MISSED_TYPE to Direction.MISSED,
            Calls.REJECTED_TYPE to Direction.REJECTED,
            Calls.BLOCKED_TYPE to Direction.BLOCKED,
            Calls.ANSWERED_EXTERNALLY_TYPE to Direction.INCOMING,
        )
        for ((type, direction) in expected) {
            assertThat(mapper.map(row(type = type), stats)!!.direction).isEqualTo(direction)
        }
        assertThat(Calls.ANSWERED_EXTERNALLY_TYPE).isEqualTo(7)
        assertThat(stats.skipped).isEmpty()
    }

    @Test
    fun `an unknown type is skipped and its value is noted`() {
        assertThat(mapper.map(row(type = 99), stats)).isNull()
        assertThat(mapper.map(row(type = null), stats)).isNull()
        assertThat(stats.skipped).containsExactly("unknown_type", 2)
        assertThat(stats.notes).containsExactly("unknown_type_value_99", 1, "unknown_type_value_null", 1)
    }

    @Test
    fun `a null or non-positive date is skipped as no_date`() {
        assertThat(mapper.map(row(date = null), stats)).isNull()
        assertThat(mapper.map(row(date = 0), stats)).isNull()
        assertThat(stats.skipped).containsExactly("no_date", 2)
    }

    @Test
    fun `duration is seconds times 1000 and null or negative becomes zero`() {
        assertThat(mapper.map(row(duration = 61), stats)!!.durationMs).isEqualTo(61_000L)
        assertThat(mapper.map(row(duration = null), stats)!!.durationMs).isEqualTo(0L)
        assertThat(mapper.map(row(duration = -5), stats)!!.durationMs).isEqualTo(0L)
    }

    @Test
    fun `allowed presentation keeps the trimmed number and titles it formatted`() {
        val record = mapper.map(row(number = "  +91 98765 43210 "), stats)!!
        assertThat(record.numberRaw).isEqualTo("+91 98765 43210")
        assertThat(record.title).isEqualTo("+91 98765 43210")
    }

    @Test
    fun `a national number is formatted for the country`() {
        assertThat(mapper.map(row(number = "09876543210"), stats)!!.title).isEqualTo("098765 43210")
    }

    @Test
    fun `an alphanumeric number is kept as is`() {
        val record = mapper.map(row(number = "AX-HDFCBK"), stats)!!
        assertThat(record.numberRaw).isEqualTo("AX-HDFCBK")
        assertThat(record.title).isEqualTo("AX-HDFCBK")
    }

    @Test
    fun `restricted presentation has no number and the private title even when a number is present`() {
        val record = mapper.map(row(number = "+919876543210", presentation = Calls.PRESENTATION_RESTRICTED), stats)!!
        assertThat(record.numberRaw).isNull()
        assertThat(record.title).isEqualTo(TelephonyTitles.PRIVATE_NUMBER)
        assertThat(record.title).isEqualTo("Private number")
    }

    @Test
    fun `payphone presentation has no number and the payphone title`() {
        val record = mapper.map(row(number = null, presentation = Calls.PRESENTATION_PAYPHONE), stats)!!
        assertThat(record.numberRaw).isNull()
        assertThat(record.title).isEqualTo("Payphone")
    }

    @Test
    fun `unknown and unavailable and other presentations give the unknown number title`() {
        for (p in listOf(Calls.PRESENTATION_UNKNOWN, 5, 42)) {
            val record = mapper.map(row(number = "+919876543210", presentation = p), stats)!!
            assertThat(record.numberRaw).isNull()
            assertThat(record.title).isEqualTo(TelephonyTitles.UNKNOWN_NUMBER)
        }
    }

    @Test
    fun `a blank number or the -2 sentinel with allowed presentation is an unknown number`() {
        for (number in listOf(null, "", "   ", "-2", "-1")) {
            val record = mapper.map(row(number = number), stats)!!
            assertThat(record.numberRaw).isNull()
            assertThat(record.title).isEqualTo("Unknown number")
        }
    }

    @Test
    fun `a missing presentation is treated as allowed`() {
        assertThat(mapper.map(row(presentation = null), stats)!!.numberRaw).isEqualTo("+91 98765 43210")
    }

    @Test
    fun `the record has the call shape and a hashed sourceVersion`() {
        val record = mapper.map(row(id = 42, date = 1_700_000_123_000L), stats)!!
        assertThat(record.key).isEqualTo(SourceKey(Source.CALL_LOG, 42))
        assertThat(record.type).isEqualTo(ItemType.CALL)
        assertThat(record.primaryTs).isEqualTo(1_700_000_123_000L)
        assertThat(record.dates).containsExactly(DateKind.EVENT, 1_700_000_123_000L)
        assertThat(record.msgKind).isNull()
        assertThat(record.threadId).isNull()
        assertThat(record.body).isNull()
        assertThat(record.hasAttachment).isFalse()
        assertThat(record.folder).isNull()
        assertThat(record.mime).isNull()
        assertThat(record.ext).isNull()
        assertThat(record.sizeBytes).isNull()
        assertThat(record.contentUri).isNull()
        assertThat(record.sourceVersion).isEqualTo(RecordHasher.hash(record.copy(sourceVersion = 0L)))
        assertThat(record.sourceVersion).isNotEqualTo(0L)
    }
}
