package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContrastTest {
    @Test
    fun ratioOfBlackAndWhiteIs21() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(contrastRatio(Color.White, Color.White)).isWithin(0.001).of(1.0)
    }

    private fun pairs(s: ColorScheme, x: SmartIndexExtendedColors): List<Triple<String, Color, Color>> = listOf(
        Triple("onPrimary/primary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSurface/surface", s.onSurface, s.surface),
        Triple("onBackground/background", s.onBackground, s.background),
        Triple("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface),
        Triple("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        Triple("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface),
        Triple("onError/error", s.onError, s.error),
        Triple("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("onWarnContainer/warnContainer", x.onWarnContainer, x.warnContainer),
    ) + x.types.map { (k, v) -> Triple("type $k icon/tile", v.icon, v.tile) }

    private fun check(s: ColorScheme, x: SmartIndexExtendedColors) {
        pairs(s, x).forEach { (name, fg, bg) ->
            assertThat(contrastRatio(fg, bg)).isAtLeast(4.5)
        }
    }

    @Test
    fun lightPairsMeetAa() = check(SmartIndexLightColors, SmartIndexLightExtendedColors)

    @Test
    fun darkPairsMeetAa() = check(SmartIndexDarkColors, SmartIndexDarkExtendedColors)
}
