package com.kuriousdragon.smartindex.source.telephony

import android.Manifest
import android.net.Uri
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Device probe (LLD — Telephony indexer §7.4). Run manually on the Samsung target, not in CI:
 * `adb shell pm grant <test package> android.permission.READ_SMS` (and READ_CALL_LOG), then
 * `./gradlew :source:telephony:connectedDebugAndroidTest`. Skips when the permissions are not granted.
 * Logs counts and type integers only, never numbers or text.
 */
class TelephonyProviderProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver

    @Before
    fun requirePermissions() {
        assumeTrue("READ_SMS not granted", granted(Manifest.permission.READ_SMS))
        assumeTrue("READ_CALL_LOG not granted", granted(Manifest.permission.READ_CALL_LOG))
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Test
    fun mmsPartUriReturnsItsColumns() {
        val uri = Uri.parse("content://mms/part")
        val columns = listOf("_id", "mid", "seq", "ct", "text")
        queryOrFail(uri, columns).use { c ->
            columns.forEach { assertThat(c.getColumnIndex(it)).isAtLeast(0) }
            Log.i(TAG, "mms/part rows=${c.count}")
        }
    }

    @Test
    fun conversationsSimpleReturnsItsColumns() {
        val uri = Telephony.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon().appendQueryParameter("simple", "true").build()
        queryOrFail(uri, listOf("_id", "recipient_ids")).use { c ->
            assertThat(c.getColumnIndex("recipient_ids")).isAtLeast(0)
            Log.i(TAG, "conversations rows=${c.count}")
        }
    }

    @Test
    fun canonicalAddressesReturnsItsColumns() {
        val uri = Uri.parse("content://mms-sms/canonical-addresses")
        queryOrFail(uri, listOf("_id", "address")).use { c ->
            assertThat(c.getColumnIndex("address")).isAtLeast(0)
            Log.i(TAG, "canonical-addresses rows=${c.count}")
        }
    }

    @Test
    fun perMessageAddrUriReturnsItsColumns() {
        val messageId = resolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id"), null, null, "_id DESC LIMIT 1")
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        assumeTrue("no MMS on the device", messageId != null)
        val uri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(messageId.toString()).appendPath("addr").build()
        queryOrFail(uri, listOf("address", "type")).use { c ->
            assertThat(c.getColumnIndex("type")).isAtLeast(0)
            Log.i(TAG, "mms/<id>/addr rows=${c.count}")
        }
    }

    @Test
    fun logsDistinctCallAndSmsTypeValues() {
        Log.i(TAG, "call types=${distinctTypes(CallLog.Calls.CONTENT_URI, CallLog.Calls.TYPE)}")
        Log.i(TAG, "sms types=${distinctTypes(Telephony.Sms.CONTENT_URI, Telephony.Sms.TYPE)}")
    }

    private fun queryOrFail(uri: Uri, projection: List<String>) =
        checkNotNull(resolver.query(uri, projection.toTypedArray(), null, null, null)) { "null cursor for $uri" }

    private fun distinctTypes(uri: Uri, column: String): Map<Int, Int> {
        val counts = sortedMapOf<Int, Int>()
        checkNotNull(resolver.query(uri, arrayOf(column), null, null, null)) { "null cursor for $uri" }.use { c ->
            while (c.moveToNext()) counts.merge(c.getInt(0), 1, Int::plus)
        }
        return counts
    }

    private companion object {
        const val TAG = "SI.Tel.Probe"
    }
}
