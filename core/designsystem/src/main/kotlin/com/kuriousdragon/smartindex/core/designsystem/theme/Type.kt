package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/** Design Spec §5.3. Sizes and line heights are sp. */
val SmartIndexTypography = Typography(
    headlineSmall = style(24, 32, FontWeight.W400),
    titleLarge = style(22, 28, FontWeight.W400),
    titleMedium = style(16, 24, FontWeight.W500),
    titleSmall = style(14, 20, FontWeight.W500),
    bodyLarge = style(16, 24, FontWeight.W400),
    bodyMedium = style(14, 20, FontWeight.W400),
    bodySmall = style(12, 16, FontWeight.W400),
    labelLarge = style(14, 20, FontWeight.W500),
    labelMedium = style(12, 16, FontWeight.W500),
    labelSmall = style(11, 16, FontWeight.W500),
)

/** Style of the matched part of a search result. */
val searchHighlight: SpanStyle
    @Composable get() = SpanStyle(fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.primary)
