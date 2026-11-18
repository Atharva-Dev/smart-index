package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTokensTest {
    private fun roles(s: ColorScheme): Map<String, Color> = mapOf(
        "primary" to s.primary, "onPrimary" to s.onPrimary, "primaryContainer" to s.primaryContainer,
        "onPrimaryContainer" to s.onPrimaryContainer, "inversePrimary" to s.inversePrimary,
        "surface" to s.surface, "background" to s.background,
        "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceVariant" to s.surfaceVariant,
        "onSurface" to s.onSurface, "onBackground" to s.onBackground, "onSurfaceVariant" to s.onSurfaceVariant,
        "outline" to s.outline, "outlineVariant" to s.outlineVariant, "inverseSurface" to s.inverseSurface,
        "inverseOnSurface" to s.inverseOnSurface, "error" to s.error, "onError" to s.onError,
        "errorContainer" to s.errorContainer, "onErrorContainer" to s.onErrorContainer, "scrim" to s.scrim,
    )

    // role, light, dark
    private val table = listOf(
        Triple("primary", 0xFF2B55C4, 0xFFB4C5FF), Triple("onPrimary", 0xFFFFFFFF, 0xFF0B2A6E),
        Triple("primaryContainer", 0xFFDCE3FF, 0xFF264196), Triple("onPrimaryContainer", 0xFF0A2463, 0xFFDCE3FF),
        Triple("inversePrimary", 0xFFB4C5FF, 0xFF2B55C4),
        Triple("surface", 0xFFFBFAFE, 0xFF121318), Triple("background", 0xFFFBFAFE, 0xFF121318),
        Triple("surfaceContainerLow", 0xFFF4F4FA, 0xFF1A1B21), Triple("surfaceContainer", 0xFFEEEEF5, 0xFF1E1F25),
        Triple("surfaceContainerHigh", 0xFFE6E7EF, 0xFF292A30), Triple("surfaceVariant", 0xFFE6E7EF, 0xFF292A30),
        Triple("onSurface", 0xFF1A1B21, 0xFFE3E2E9), Triple("onBackground", 0xFF1A1B21, 0xFFE3E2E9),
        Triple("onSurfaceVariant", 0xFF454752, 0xFFC5C6D3), Triple("outline", 0xFF757684, 0xFF8F909E),
        Triple("outlineVariant", 0xFFC5C6D3, 0xFF454752), Triple("inverseSurface", 0xFF2F3036, 0xFFE3E2E9),
        Triple("inverseOnSurface", 0xFFF1F0F7, 0xFF2F3036), Triple("error", 0xFFBA1A1A, 0xFFFFB4AB),
        Triple("onError", 0xFFFFFFFF, 0xFF690005), Triple("errorContainer", 0xFFFFDAD6, 0xFF93000A),
        Triple("onErrorContainer", 0xFF410002, 0xFFFFDAD6), Triple("scrim", 0xFF000000, 0xFF000000),
    )

    @Test
    fun lightSchemeMatchesDesignSpec() {
        val actual = roles(SmartIndexLightColors)
        table.forEach { (role, light, _) -> assertThat(actual[role]).isEqualTo(Color(light)) }
    }

    @Test
    fun darkSchemeMatchesDesignSpec() {
        val actual = roles(SmartIndexDarkColors)
        table.forEach { (role, _, dark) -> assertThat(actual[role]).isEqualTo(Color(dark)) }
    }

    @Test
    fun tableCoversEveryAssertedRole() {
        assertThat(table.map { it.first }).containsExactlyElementsIn(roles(SmartIndexLightColors).keys)
    }

    @Test
    fun extendedWarnColours() {
        assertThat(SmartIndexLightExtendedColors.warnContainer).isEqualTo(Color(0xFFFFEFC7))
        assertThat(SmartIndexLightExtendedColors.onWarnContainer).isEqualTo(Color(0xFF4A3600))
        assertThat(SmartIndexDarkExtendedColors.warnContainer).isEqualTo(Color(0xFF4A3600))
        assertThat(SmartIndexDarkExtendedColors.onWarnContainer).isEqualTo(Color(0xFFFFE08F))
    }

    @Test
    fun typeColoursMatchDesignSpec() {
        // key, light icon, light tile, dark icon, dark tile
        val expected = listOf(
            listOf(TypeColorKey.PHOTO, 0xFF7A3EB1, 0xFFF1E6FB, 0xFFDDB8FF, 0xFF3B2052),
            listOf(TypeColorKey.VIDEO, 0xFFB5400B, 0xFFFDE7DC, 0xFFFFB693, 0xFF4F2410),
            listOf(TypeColorKey.DOC, 0xFF1F5FBF, 0xFFE1EBFB, 0xFFADC6FF, 0xFF1B3360),
            listOf(TypeColorKey.FILE, 0xFF4E6470, 0xFFE6ECEF, 0xFFB4CAD5, 0xFF2A363C),
            listOf(TypeColorKey.CALL, 0xFF25723A, 0xFFDFF1E3, 0xFF8DD89E, 0xFF16371F),
            listOf(TypeColorKey.MISSED, 0xFFBA1A1A, 0xFFFFE4E1, 0xFFFFB4AB, 0xFF5C1712),
            listOf(TypeColorKey.REC, 0xFFA3195B, 0xFFFCE3EE, 0xFFFFB0CF, 0xFF4E1730),
            listOf(TypeColorKey.MSG, 0xFF00696B, 0xFFD9F2F1, 0xFF7FD5D3, 0xFF0F3938),
        )
        assertThat(expected.size).isEqualTo(TypeColorKey.values().size)
        expected.forEach { row ->
            val key = row[0] as TypeColorKey
            assertThat(SmartIndexLightExtendedColors.types[key])
                .isEqualTo(TypeColors(Color(row[1] as Long), Color(row[2] as Long)))
            assertThat(SmartIndexDarkExtendedColors.types[key])
                .isEqualTo(TypeColors(Color(row[3] as Long), Color(row[4] as Long)))
        }
    }
}
