package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/** The Smart Index Material 3 theme. Follows the system dark-mode setting; no in-app switch (Design Spec §5.1). */
@Composable
fun SmartIndexTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val reduced = isReducedMotion(LocalContext.current)
    CompositionLocalProvider(
        LocalSmartIndexColors provides if (darkTheme) SmartIndexDarkExtendedColors else SmartIndexLightExtendedColors,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) SmartIndexDarkColors else SmartIndexLightColors,
            typography = SmartIndexTypography,
            shapes = SmartIndexShapes,
            content = content,
        )
    }
}

/** Accessor for the extended tokens: `SmartIndexTheme.colors`. */
object SmartIndexTheme {
    val colors: SmartIndexExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSmartIndexColors.current
}
