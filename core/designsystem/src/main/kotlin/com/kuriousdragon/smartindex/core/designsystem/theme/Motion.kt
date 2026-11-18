package com.kuriousdragon.smartindex.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/** Design Spec §8 animation durations. */
object Durations {
    const val CROSS_FADE_MS = 150
    const val DIALOG_MS = 200
    const val SHEET_MS = 250
    const val PLAYER_MS = 200
    const val SHIMMER_MS = 1200
}

/** True when the user turned system animations off; animated composables then snap or cross-fade. */
val LocalReducedMotion = staticCompositionLocalOf { false }

fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
