package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.recordings.call
import com.kuriousdragon.smartindex.source.recordings.numberNormalizer
import com.kuriousdragon.smartindex.source.recordings.parse.ParsedRecordingName
import java.time.LocalDateTime
import org.junit.Test

class IdentityMatcherTest {
    private val matcher = IdentityMatcher(numberNormalizer)
    private val at = LocalDateTime.of(2024, 11, 3, 10, 0)

    private fun parsed(name: String?, number: String?) = ParsedRecordingName("p", name, number, at, false)

    private fun compare(
        name: String? = null,
        number: String? = null,
        callNumber: String? = "+919876543210",
        callName: String? = "Rahul",
        parsedNull: Boolean = false,
    ): Identity {
        val r = matcher.ofRecording(if (parsedNull) null else parsed(name, number))
        val c = matcher.ofCall(call(1, "2024-11-03T10:00:00", 60, number = callNumber, name = callName))
        return matcher.compare(r, c)
    }

    @Test
    fun `same E164 number matches`() {
        assertThat(compare(number = "+91 98765 43210")).isEqualTo(Identity.MATCH_NUMBER)
    }

    @Test
    fun `national number matches E164 call number`() {
        assertThat(compare(number = "9876543210")).isEqualTo(Identity.MATCH_NUMBER)
    }

    @Test
    fun `last 10 digits rule rescues a country code mismatch`() {
        assertThat(compare(number = "+449876543210")).isEqualTo(Identity.MATCH_NUMBER)
    }

    @Test
    fun `different numbers contradict even when names are equal`() {
        assertThat(compare(name = "Rahul", number = "+919999999999")).isEqualTo(Identity.CONTRADICT_NUMBER)
    }

    @Test
    fun `private call falls through to name rules`() {
        assertThat(compare(name = "Rahul", number = "9876543210", callNumber = null)).isEqualTo(Identity.MATCH_NAME)
        assertThat(compare(name = "Priya", callNumber = null)).isEqualTo(Identity.CONTRADICT_NAME)
    }

    @Test
    fun `equal names match`() {
        assertThat(compare(name = "rahul")).isEqualTo(Identity.MATCH_NAME)
    }

    @Test
    fun `first name versus full name is partial`() {
        assertThat(compare(name = "Rahul", callName = "Rahul Sharma")).isEqualTo(Identity.PARTIAL_NAME)
        assertThat(compare(name = "Rahul Sharma", callName = "Rahul")).isEqualTo(Identity.PARTIAL_NAME)
    }

    @Test
    fun `unknown prefix plus name versus name is partial`() {
        assertThat(compare(name = "Appel Rahul")).isEqualTo(Identity.PARTIAL_NAME)
    }

    @Test
    fun `different names contradict`() {
        assertThat(compare(name = "Rahul", callName = "Priya")).isEqualTo(Identity.CONTRADICT_NAME)
    }

    @Test
    fun `missing contact name is unknown`() {
        assertThat(compare(name = "Rahul", callName = null)).isEqualTo(Identity.UNKNOWN)
    }

    @Test
    fun `unparsed recording is unknown`() {
        assertThat(compare(parsedNull = true)).isEqualTo(Identity.UNKNOWN)
        assertThat(matcher.ofRecording(null)).isEqualTo(RecordingIdentity(null, null, null))
    }

    @Test
    fun `last10 needs at least ten digits`() {
        assertThat(IdentityMatcher.last10("919876543210")).isEqualTo("9876543210")
        assertThat(IdentityMatcher.last10("12345")).isNull()
        assertThat(IdentityMatcher.last10(null)).isNull()
    }

    @Test
    fun `identity flags`() {
        assertThat(Identity.CONTRADICT_NAME.contradicts).isTrue()
        assertThat(Identity.CONTRADICT_NUMBER.contradicts).isTrue()
        assertThat(Identity.MATCH_NAME.isMatch).isTrue()
        assertThat(Identity.PARTIAL_NAME.isMatch).isFalse()
        assertThat(Identity.UNKNOWN.contradicts).isFalse()
    }
}
