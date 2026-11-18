package com.kuriousdragon.smartindex.feature.home.model

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DefaultDeviceEnvSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()
    private var nowMs = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.UK)
        set24Hour(true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private fun source() = DefaultDeviceEnvSource(context) { nowMs }

    private fun set24Hour(on: Boolean) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, if (on) "24" else "12")
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `initial env reads zone locale 24 hour flag and today from the clock`() {
        val env = source().env.value
        assertThat(env.zone).isEqualTo(ZoneId.of("UTC"))
        assertThat(env.locale).isEqualTo(Locale.UK)
        assertThat(env.is24Hour).isTrue()
        assertThat(env.today).isEqualTo(LocalDate.of(2026, 11, 3))
    }

    @Test
    fun `timezone change broadcast updates the env`() = runTest {
        val s = source()
        s.env.test {
            assertThat(awaitItem().zone).isEqualTo(ZoneId.of("UTC"))
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            broadcast(Intent.ACTION_TIMEZONE_CHANGED)
            assertThat(awaitItem().zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
        }
    }

    @Test
    fun `locale change broadcast updates the env`() = runTest {
        val s = source()
        s.env.test {
            awaitItem()
            Locale.setDefault(Locale.GERMANY)
            broadcast(Intent.ACTION_LOCALE_CHANGED)
            assertThat(awaitItem().locale).isEqualTo(Locale.GERMANY)
        }
    }

    @Test
    fun `time change broadcast updates today`() = runTest {
        val s = source()
        s.env.test {
            awaitItem()
            nowMs = Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()
            broadcast(Intent.ACTION_TIME_CHANGED)
            assertThat(awaitItem().today).isEqualTo(LocalDate.of(2026, 11, 10))
        }
    }

    @Test
    fun `date change broadcast rolls today over`() = runTest {
        val s = source()
        s.env.test {
            awaitItem()
            nowMs = Instant.parse("2026-11-04T00:00:01Z").toEpochMilli()
            broadcast(Intent.ACTION_DATE_CHANGED)
            assertThat(awaitItem().today).isEqualTo(LocalDate.of(2026, 11, 4))
        }
    }

    @Test
    fun `an equal env does not emit`() = runTest {
        val s = source()
        s.env.test {
            awaitItem()
            s.refresh()
            broadcast(Intent.ACTION_DATE_CHANGED)
            expectNoEvents()
        }
    }

    @Test
    fun `refresh re-reads the 12 24 hour setting`() = runTest {
        val s = source()
        s.env.test {
            assertThat(awaitItem().is24Hour).isTrue()
            set24Hour(false)
            s.refresh()
            assertThat(awaitItem().is24Hour).isFalse()
        }
    }
}
