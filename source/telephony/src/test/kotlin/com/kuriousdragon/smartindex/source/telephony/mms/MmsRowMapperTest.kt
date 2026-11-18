package com.kuriousdragon.smartindex.source.telephony.mms

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
class MmsRowMapperTest {
    private val country = CountryIsoSource { "IN" }
    private val mapper = MmsRowMapper(NumberDisplay(country, DefaultPhoneNumberNormalizer(country)))
    private val stats = RunStatsBuilder(Source.MMS, Mode.FULL)

    private companion object {
        const val HOTEL = "होटल सी व्यू में बुकिंग"
        val ONE = MmsParticipants("+91 98765 43210", emptyList())
    }

    private fun row(
        id: Long = 7,
        threadId: Long? = 3,
        date: Long? = 1_700_000_000L,
        box: Int? = 1,
        subject: String? = null,
        charset: Int? = null,
    ) = MmsRow(id, threadId, date, box, subject, charset)

    private fun map(
        row: MmsRow = row(),
        content: MmsContent = MmsContent("hi", false),
        participants: MmsParticipants = ONE,
    ) = mapper.map(row, content, participants, stats)

    @Test
    fun `a date in seconds is multiplied by 1000`() {
        val item = map(row(date = 1_700_000_000L))!!
        assertThat(item.primaryTs).isEqualTo(1_700_000_000_000L)
        assertThat(item.dates).containsExactly(DateKind.EVENT, 1_700_000_000_000L)
    }

    @Test
    fun `a date that is already in milliseconds is kept`() {
        assertThat(map(row(date = 1_700_000_000_000L))!!.primaryTs).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `a null or non-positive date is skipped and counted`() {
        assertThat(map(row(date = null))).isNull()
        assertThat(map(row(date = 0))).isNull()
        assertThat(stats.skipped["no_date"]).isEqualTo(2)
    }

    @Test
    fun `every box maps to its direction and failed is SENT`() {
        val expected = mapOf(1 to Direction.RECEIVED, 2 to Direction.SENT, 4 to Direction.SENT, 5 to Direction.SENT)
        for ((box, direction) in expected) assertThat(map(row(box = box))!!.direction).isEqualTo(direction)
        assertThat(stats.skipped).isEmpty()
    }

    @Test
    fun `an unknown box is skipped and its value noted`() {
        for (box in listOf(0, 3, 99, null)) assertThat(map(row(box = box))).isNull()
        assertThat(stats.skipped["unknown_type"]).isEqualTo(4)
        assertThat(stats.notes).containsKey("unknown_type_value_99")
    }

    @Test
    fun `the record has the MMS shape of the item table`() {
        val item = map(row(threadId = 0), MmsContent("hi", true))!!
        assertThat(item.key).isEqualTo(SourceKey(Source.MMS, 7))
        assertThat(item.type).isEqualTo(ItemType.MESSAGE)
        assertThat(item.msgKind).isEqualTo(MsgKind.MMS)
        assertThat(item.hasAttachment).isTrue()
        assertThat(item.threadId).isNull()
        assertThat(item.numberRaw).isEqualTo("+91 98765 43210")
        assertThat(item.durationMs).isNull()
        assertThat(item.folder).isNull()
        assertThat(item.contentUri).isNull()
        assertThat(item.sourceVersion).isEqualTo(RecordHasher.hash(item))
        assertThat(map(row(threadId = 4))!!.threadId).isEqualTo(4L)
    }

    @Test
    fun `the subject is prefixed to the body with a newline`() {
        assertThat(map(row(subject = "  Trip  "), MmsContent("see you", false))!!.body)
            .isEqualTo("Trip\nsee you")
        assertThat(map(row(subject = "Trip"), MmsContent.EMPTY)!!.body).isEqualTo("Trip")
    }

    @Test
    fun `no subject variants are dropped`() {
        for (s in listOf("no subject", "No Subject", "nosubject", "<no subject>", "(No Subject)", "  ", "")) {
            assertThat(map(row(subject = s), MmsContent("body", false))!!.body).isEqualTo("body")
        }
    }

    @Test
    fun `a UTF-8 subject stored as ISO-8859-1 is re-decoded to Devanagari`() {
        val stored = String(HOTEL.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertThat(stored).isNotEqualTo(HOTEL)
        assertThat(map(row(subject = stored, charset = 106), MmsContent.EMPTY)!!.body).isEqualTo(HOTEL)
    }

    @Test
    fun `an already decoded Devanagari subject is kept`() {
        assertThat(map(row(subject = HOTEL, charset = 106), MmsContent.EMPTY)!!.body).isEqualTo(HOTEL)
    }

    @Test
    fun `a Latin-1 subject that is not valid UTF-8 is kept as is`() {
        assertThat(map(row(subject = "café", charset = 106), MmsContent.EMPTY)!!.body).isEqualTo("café")
        assertThat(map(row(subject = "café", charset = null), MmsContent.EMPTY)!!.body).isEqualTo("café")
    }

    @Test
    fun `a Devanagari body is kept exactly and the body is capped`() {
        assertThat(map(content = MmsContent(HOTEL, false))!!.body).isEqualTo(HOTEL)
        val long = "a".repeat(TelephonyConfig.MAX_BODY_CHARS + 50)
        assertThat(map(content = MmsContent(long, false))!!.body).hasLength(TelephonyConfig.MAX_BODY_CHARS)
    }

    @Test
    fun `empty content gives a null body`() {
        assertThat(map(content = MmsContent.EMPTY)!!.body).isNull()
    }

    @Test
    fun `the title is the formatted number with a plus others suffix for groups`() {
        assertThat(map(participants = MmsParticipants("098765 43210", emptyList()))!!.title).isEqualTo("098765 43210")
        assertThat(
            map(participants = MmsParticipants("098765 43210", listOf("+91 11111 22222", "+91 77777 88888")))!!.title,
        ).isEqualTo("098765 43210 +2")
        assertThat(map(participants = MmsParticipants("AX-HDFCBK", emptyList()))!!.title).isEqualTo("AX-HDFCBK")
    }

    @Test
    fun `the other participants are passed to the record and the title count is unchanged`() {
        val others = listOf("+91 11111 22222", "+91 77777 88888")
        val item = map(participants = MmsParticipants("098765 43210", others))!!
        assertThat(item.otherNumbers).containsExactlyElementsIn(others).inOrder()
        assertThat(item.title).isEqualTo("098765 43210 +2")
        assertThat(map()!!.otherNumbers).isEmpty()
    }

    @Test
    fun `the source version changes when only the other participants change`() {
        val one = map(participants = MmsParticipants("098765 43210", listOf("+91 11111 22222")))!!
        val two = map(participants = MmsParticipants("098765 43210", listOf("+91 77777 88888")))!!
        assertThat(one.sourceVersion).isNotEqualTo(two.sourceVersion)
    }

    @Test
    fun `without a counterpart the title is the unknown sender or recipient label`() {
        assertThat(map(row(box = 1), participants = MmsParticipants.UNKNOWN)!!.title)
            .isEqualTo(TelephonyTitles.UNKNOWN_SENDER)
        assertThat(map(row(box = 2), participants = MmsParticipants.UNKNOWN)!!.title)
            .isEqualTo(TelephonyTitles.UNKNOWN_RECIPIENT)
        assertThat(map(row(box = 2), participants = MmsParticipants(" ", listOf("1", "2", "3")))!!.numberRaw).isNull()
    }
}
