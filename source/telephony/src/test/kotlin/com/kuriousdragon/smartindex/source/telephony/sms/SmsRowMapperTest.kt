package com.kuriousdragon.smartindex.source.telephony.sms

import android.provider.Telephony.Sms
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
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
class SmsRowMapperTest {
    private val country = CountryIsoSource { "IN" }
    private val mapper = SmsRowMapper(NumberDisplay(country, DefaultPhoneNumberNormalizer(country)))
    private val stats = RunStatsBuilder(Source.SMS, Mode.FULL)

    private fun row(
        id: Long = 7,
        threadId: Long? = 3,
        address: String? = "+91 98765 43210",
        date: Long? = 1_700_000_000_000L,
        type: Int? = Sms.MESSAGE_TYPE_INBOX,
        body: String? = "hello",
    ) = SmsRow(id, threadId, address, date, type, body)

    @Test
    fun `every supported box maps to its direction`() {
        val expected = mapOf(
            Sms.MESSAGE_TYPE_INBOX to Direction.RECEIVED,
            Sms.MESSAGE_TYPE_SENT to Direction.SENT,
            Sms.MESSAGE_TYPE_OUTBOX to Direction.SENT,
            Sms.MESSAGE_TYPE_FAILED to Direction.SENT,
            Sms.MESSAGE_TYPE_QUEUED to Direction.SENT,
        )
        for ((type, direction) in expected) {
            assertThat(mapper.map(row(type = type), stats)!!.direction).isEqualTo(direction)
        }
        assertThat(stats.skipped).isEmpty()
    }

    @Test
    fun `an unknown type is skipped and its value is noted`() {
        for (type in listOf(
            0,
            99,
            Sms.MESSAGE_TYPE_DRAFT,
            null,
        )) {
            assertThat(mapper.map(row(type = type), stats)).isNull()
        }
        assertThat(stats.skipped).containsExactly("unknown_type", 4)
        assertThat(stats.notes["unknown_type_value_99"]).isEqualTo(1)
    }

    @Test
    fun `a null or non-positive date is skipped as no_date`() {
        assertThat(mapper.map(row(date = null), stats)).isNull()
        assertThat(mapper.map(row(date = 0), stats)).isNull()
        assertThat(mapper.map(row(date = -5), stats)).isNull()
        assertThat(stats.skipped).containsExactly("no_date", 3)
    }

    @Test
    fun `a null or blank address gives the unknown sender or recipient title and no number`() {
        for (address in listOf(null, "", "   ", "-2", "-1")) {
            val received = mapper.map(row(address = address, type = Sms.MESSAGE_TYPE_INBOX), stats)!!
            assertThat(received.numberRaw).isNull()
            assertThat(received.title).isEqualTo(TelephonyTitles.UNKNOWN_SENDER)
            val sent = mapper.map(row(address = address, type = Sms.MESSAGE_TYPE_SENT), stats)!!
            assertThat(sent.numberRaw).isNull()
            assertThat(sent.title).isEqualTo(TelephonyTitles.UNKNOWN_RECIPIENT)
        }
    }

    @Test
    fun `a phone number is trimmed and titled formatted`() {
        val record = mapper.map(row(address = "  +91 98765 43210 "), stats)!!
        assertThat(record.numberRaw).isEqualTo("+91 98765 43210")
        assertThat(record.title).isEqualTo("+91 98765 43210")
        assertThat(mapper.map(row(address = "09876543210"), stats)!!.title).isEqualTo("098765 43210")
    }

    @Test
    fun `an alphanumeric sender title is kept as is`() {
        val record = mapper.map(row(address = "AX-HDFCBK"), stats)!!
        assertThat(record.numberRaw).isEqualTo("AX-HDFCBK")
        assertThat(record.title).isEqualTo("AX-HDFCBK")
    }

    @Test
    fun `a short code title is kept as is`() {
        val record = mapper.map(row(address = "56161"), stats)!!
        assertThat(record.numberRaw).isEqualTo("56161")
        assertThat(record.title).isEqualTo("56161")
    }

    @Test
    fun `a Devanagari body is preserved exactly`() {
        val text = "होटल सी व्यू में बुकिंग"
        val record = mapper.map(row(body = text), stats)!!
        assertThat(record.body).isEqualTo(text)
        assertThat(record.body!!.toByteArray(Charsets.UTF_8)).isEqualTo(text.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `the body is never trimmed`() {
        assertThat(mapper.map(row(body = "  a b \n"), stats)!!.body).isEqualTo("  a b \n")
    }

    @Test
    fun `a body over 10000 characters is cut to 10000`() {
        val record = mapper.map(row(body = "x".repeat(12_000)), stats)!!
        assertThat(record.body).hasLength(TelephonyConfig.MAX_BODY_CHARS)
        assertThat(record.body).isEqualTo("x".repeat(10_000))
    }

    @Test
    fun `a null or blank body becomes null`() {
        assertThat(mapper.map(row(body = null), stats)!!.body).isNull()
        assertThat(mapper.map(row(body = ""), stats)!!.body).isNull()
        assertThat(mapper.map(row(body = " \n\t"), stats)!!.body).isNull()
    }

    @Test
    fun `thread id is kept only when positive`() {
        assertThat(mapper.map(row(threadId = 12), stats)!!.threadId).isEqualTo(12L)
        assertThat(mapper.map(row(threadId = 0), stats)!!.threadId).isNull()
        assertThat(mapper.map(row(threadId = null), stats)!!.threadId).isNull()
    }

    @Test
    fun `the record has the SMS shape and a hashed sourceVersion`() {
        val record = mapper.map(row(id = 42, date = 1_700_000_123_000L), stats)!!
        assertThat(record.key).isEqualTo(SourceKey(Source.SMS, 42))
        assertThat(record.type).isEqualTo(ItemType.MESSAGE)
        assertThat(record.msgKind).isEqualTo(MsgKind.SMS)
        assertThat(record.primaryTs).isEqualTo(1_700_000_123_000L)
        assertThat(record.dates).containsExactly(DateKind.EVENT, 1_700_000_123_000L)
        assertThat(record.durationMs).isNull()
        assertThat(record.hasAttachment).isFalse()
        assertThat(record.folder).isNull()
        assertThat(record.mime).isNull()
        assertThat(record.ext).isNull()
        assertThat(record.sizeBytes).isNull()
        assertThat(record.contentUri).isNull()
        assertThat(record.sourceVersion).isEqualTo(RecordHasher.hash(record.copy(sourceVersion = 0L)))
        assertThat(record.sourceVersion).isNotEqualTo(0L)
    }

    @Test
    fun `a body or box change changes the sourceVersion`() {
        val base = mapper.map(row(), stats)!!.sourceVersion
        assertThat(mapper.map(row(body = "other"), stats)!!.sourceVersion).isNotEqualTo(base)
        assertThat(mapper.map(row(type = Sms.MESSAGE_TYPE_SENT), stats)!!.sourceVersion).isNotEqualTo(base)
        assertThat(mapper.map(row(), stats)!!.sourceVersion).isEqualTo(base)
    }
}
