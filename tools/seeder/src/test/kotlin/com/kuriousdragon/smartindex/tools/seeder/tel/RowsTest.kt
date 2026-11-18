package com.kuriousdragon.smartindex.tools.seeder.tel

import android.provider.CallLog
import android.provider.Telephony
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedCall
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedMms
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedSms
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RowsTest {
    private val call = SeedCall(1, "+919812345678", 1, CallLog.Calls.MISSED_TYPE, 1_700_000_123_456L, 42)

    @Test
    fun `call row has every column and the marker phone account`() {
        val v = Rows.callValues(call)
        assertThat(v.getAsString(CallLog.Calls.NUMBER)).isEqualTo("+919812345678")
        assertThat(v.getAsLong(CallLog.Calls.DATE)).isEqualTo(1_700_000_123_456L)
        assertThat(v.getAsInteger(CallLog.Calls.DURATION)).isEqualTo(42)
        assertThat(v.getAsInteger(CallLog.Calls.TYPE)).isEqualTo(CallLog.Calls.MISSED_TYPE)
        assertThat(v.getAsInteger(CallLog.Calls.NUMBER_PRESENTATION)).isEqualTo(1)
        assertThat(v.getAsInteger(CallLog.Calls.NEW)).isEqualTo(0)
        assertThat(v.getAsInteger(CallLog.Calls.IS_READ)).isEqualTo(1)
        assertThat(v.getAsString(CallLog.Calls.PHONE_ACCOUNT_ID)).isEqualTo("sidx-seeder")
        assertThat(v.size()).isEqualTo(8)
    }

    @Test
    fun `a null call number is stored as an empty string`() {
        assertThat(Rows.callValues(call.copy(number = null)).getAsString(CallLog.Calls.NUMBER)).isEmpty()
    }

    @Test
    fun `sms row has every column in milliseconds`() {
        val sms =
            SeedSms(2, "+919800000001", "नमस्ते दुनिया", Telephony.Sms.MESSAGE_TYPE_SENT, 1_700_000_000_999L, "t1")
        val v = Rows.smsValues(sms, 77L)
        assertThat(v.getAsString(Telephony.Sms.ADDRESS)).isEqualTo("+919800000001")
        assertThat(v.getAsString(Telephony.Sms.BODY)).isEqualTo("नमस्ते दुनिया")
        assertThat(v.getAsLong(Telephony.Sms.DATE)).isEqualTo(1_700_000_000_999L)
        assertThat(v.getAsLong(Telephony.Sms.DATE_SENT)).isEqualTo(1_700_000_000_999L)
        assertThat(v.getAsInteger(Telephony.Sms.TYPE)).isEqualTo(Telephony.Sms.MESSAGE_TYPE_SENT)
        assertThat(v.getAsInteger(Telephony.Sms.READ)).isEqualTo(1)
        assertThat(v.getAsInteger(Telephony.Sms.SEEN)).isEqualTo(1)
        assertThat(v.getAsLong(Telephony.Sms.THREAD_ID)).isEqualTo(77L)
        assertThat(v.size()).isEqualTo(8)
    }

    private fun mms(box: Int, subject: String? = "छुट्टी") =
        SeedMms(3, box, 1_700_000_123_456L, listOf("+919800000001", "+919800000002"), subject, listOf("hello"), 1)

    @Test
    fun `received mms pdu has seconds dates, charset 106 and the retrieve type`() {
        val v = Rows.mmsPduValues(mms(Telephony.Mms.MESSAGE_BOX_INBOX), 9L)
        assertThat(v.getAsLong(Telephony.Mms.THREAD_ID)).isEqualTo(9L)
        assertThat(v.getAsLong(Telephony.Mms.DATE)).isEqualTo(1_700_000_123L)
        assertThat(v.getAsLong(Telephony.Mms.DATE_SENT)).isEqualTo(1_700_000_123L)
        assertThat(v.getAsInteger(Telephony.Mms.MESSAGE_BOX)).isEqualTo(1)
        assertThat(v.getAsInteger(Telephony.Mms.MESSAGE_TYPE)).isEqualTo(132)
        assertThat(v.getAsInteger(Telephony.Mms.READ)).isEqualTo(1)
        assertThat(v.getAsInteger(Telephony.Mms.SEEN)).isEqualTo(1)
        assertThat(v.getAsString(Telephony.Mms.SUBJECT)).isEqualTo("छुट्टी")
        assertThat(v.getAsInteger(Telephony.Mms.SUBJECT_CHARSET)).isEqualTo(106)
        assertThat(v.getAsString(Telephony.Mms.CONTENT_TYPE)).isEqualTo("application/vnd.wap.multipart.related")
        assertThat(v.getAsInteger(Telephony.Mms.MMS_VERSION)).isEqualTo(18)
    }

    @Test
    fun `sent mms uses the send-req type and no subject columns when there is no subject`() {
        val v = Rows.mmsPduValues(mms(Telephony.Mms.MESSAGE_BOX_SENT, subject = null), 9L)
        assertThat(v.getAsInteger(Telephony.Mms.MESSAGE_BOX)).isEqualTo(2)
        assertThat(v.getAsInteger(Telephony.Mms.MESSAGE_TYPE)).isEqualTo(128)
        assertThat(v.containsKey(Telephony.Mms.SUBJECT)).isFalse()
        assertThat(v.containsKey(Telephony.Mms.SUBJECT_CHARSET)).isFalse()
    }

    @Test
    fun `text part carries utf8 charset and the text`() {
        val v = Rows.mmsTextPart(5L, 1, "छुट्टी का दिन")
        assertThat(v.getAsLong(Telephony.Mms.Part.MSG_ID)).isEqualTo(5L)
        assertThat(v.getAsString(Telephony.Mms.Part.CONTENT_TYPE)).isEqualTo("text/plain")
        assertThat(v.getAsInteger(Telephony.Mms.Part.CHARSET)).isEqualTo(106)
        assertThat(v.getAsString(Telephony.Mms.Part.TEXT)).isEqualTo("छुट्टी का दिन")
    }

    @Test
    fun `smil and image parts have their content types`() {
        assertThat(Rows.mmsSmilPart(5L).getAsString(Telephony.Mms.Part.CONTENT_TYPE)).isEqualTo("application/smil")
        val image = Rows.mmsImagePart(5L, 0)
        assertThat(image.getAsString(Telephony.Mms.Part.CONTENT_TYPE)).isEqualTo("image/jpeg")
        assertThat(image.getAsLong(Telephony.Mms.Part.MSG_ID)).isEqualTo(5L)
    }

    @Test
    fun `addr row has charset 106`() {
        val v = Rows.mmsAddr(5L, "+919800000001", Rows.ADDR_TO)
        assertThat(v.getAsInteger(Telephony.Mms.Addr.TYPE)).isEqualTo(151)
        assertThat(v.getAsInteger(Telephony.Mms.Addr.CHARSET)).isEqualTo(106)
        assertThat(v.getAsLong(Telephony.Mms.Addr.MSG_ID)).isEqualTo(5L)
        assertThat(v.getAsString(Telephony.Mms.Addr.ADDRESS)).isEqualTo("+919800000001")
    }

    @Test
    fun `sent group mms has the insert-address-token FROM and every recipient TO`() {
        val rows = Rows.mmsAddrRows(mms(Telephony.Mms.MESSAGE_BOX_SENT), 5L)
        assertThat(rows.map { it.getAsInteger(Telephony.Mms.Addr.TYPE) }).containsExactly(137, 151, 151).inOrder()
        assertThat(rows[0].getAsString(Telephony.Mms.Addr.ADDRESS)).isEqualTo("insert-address-token")
        assertThat(rows.drop(1).map { it.getAsString(Telephony.Mms.Addr.ADDRESS) })
            .containsExactly("+919800000001", "+919800000002").inOrder()
    }

    @Test
    fun `received group mms has the counterpart FROM and the other members TO`() {
        val rows = Rows.mmsAddrRows(mms(Telephony.Mms.MESSAGE_BOX_INBOX), 5L)
        assertThat(rows[0].getAsString(Telephony.Mms.Addr.ADDRESS)).isEqualTo("+919800000001")
        assertThat(rows[0].getAsInteger(Telephony.Mms.Addr.TYPE)).isEqualTo(137)
        assertThat(rows.drop(1).map { it.getAsString(Telephony.Mms.Addr.ADDRESS) }).containsExactly("+919800000002")
    }

    @Test
    fun `the fallback jpeg decodes`() {
        val bytes = TinyJpeg.bytes
        assertThat(bytes[0]).isEqualTo(0xFF.toByte())
        assertThat(bytes[1]).isEqualTo(0xD8.toByte())
        assertThat(bytes.last()).isEqualTo(0xD9.toByte())
        val image = javax.imageio.ImageIO.read(bytes.inputStream())
        assertThat(image.width).isEqualTo(1)
        assertThat(image.height).isEqualTo(1)
    }
}
