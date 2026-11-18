package com.kuriousdragon.smartindex.feature.home.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything formatting and day boundaries depend on. */
@Immutable
data class DeviceEnv(val zone: ZoneId, val locale: Locale, val is24Hour: Boolean, val today: LocalDate)

interface DeviceEnvSource {
    val env: StateFlow<DeviceEnv>

    fun refresh()
}

/** Feature-private clock so it can never collide with another module's clock binding. */
fun interface HomeClock {
    fun nowMs(): Long
}

@Singleton
class DefaultDeviceEnvSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: HomeClock,
) : DeviceEnvSource {
    private val state = MutableStateFlow(read())
    override val env: StateFlow<DeviceEnv> = state.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** Recomputes the environment; an equal value does not emit. */
    override fun refresh() {
        state.value = read()
    }

    private fun read(): DeviceEnv {
        val zone = ZoneId.systemDefault()
        return DeviceEnv(
            zone = zone,
            locale = Locale.getDefault(),
            is24Hour = DateFormat.is24HourFormat(context),
            today = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate(),
        )
    }
}
