package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Design Spec §5.1. Dynamic colour (Material You) is deliberately never used.

val SmartIndexLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2B55C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0A2463),
    inversePrimary = Color(0xFFB4C5FF),
    surface = Color(0xFFFBFAFE),
    background = Color(0xFFFBFAFE),
    surfaceContainerLow = Color(0xFFF4F4FA),
    surfaceContainer = Color(0xFFEEEEF5),
    surfaceContainerHigh = Color(0xFFE6E7EF),
    surfaceVariant = Color(0xFFE6E7EF),
    onSurface = Color(0xFF1A1B21),
    onBackground = Color(0xFF1A1B21),
    onSurfaceVariant = Color(0xFF454752),
    outline = Color(0xFF757684),
    outlineVariant = Color(0xFFC5C6D3),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

val SmartIndexDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF0B2A6E),
    primaryContainer = Color(0xFF264196),
    onPrimaryContainer = Color(0xFFDCE3FF),
    inversePrimary = Color(0xFF2B55C4),
    surface = Color(0xFF121318),
    background = Color(0xFF121318),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceVariant = Color(0xFF292A30),
    onSurface = Color(0xFFE3E2E9),
    onBackground = Color(0xFFE3E2E9),
    onSurfaceVariant = Color(0xFFC5C6D3),
    outline = Color(0xFF8F909E),
    outlineVariant = Color(0xFF454752),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

/** Colours Material 3 has no role for: the warning container and the per-item-type colours. */
data class SmartIndexExtendedColors(
    val warnContainer: Color,
    val onWarnContainer: Color,
    val types: Map<TypeColorKey, TypeColors>,
)

val SmartIndexLightExtendedColors = SmartIndexExtendedColors(
    warnContainer = Color(0xFFFFEFC7),
    onWarnContainer = Color(0xFF4A3600),
    types = LightTypeColors,
)

val SmartIndexDarkExtendedColors = SmartIndexExtendedColors(
    warnContainer = Color(0xFF4A3600),
    onWarnContainer = Color(0xFFFFE08F),
    types = DarkTypeColors,
)

val LocalSmartIndexColors = staticCompositionLocalOf { SmartIndexLightExtendedColors }
