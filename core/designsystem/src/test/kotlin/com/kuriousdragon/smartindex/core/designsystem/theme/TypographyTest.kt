package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypographyTest {
    private fun assertStyle(s: TextStyle, size: Int, line: Int, weight: FontWeight) {
        assertThat(s.fontSize).isEqualTo(size.sp)
        assertThat(s.lineHeight).isEqualTo(line.sp)
        assertThat(s.fontWeight).isEqualTo(weight)
        assertThat(s.fontFamily).isEqualTo(FontFamily.Default)
    }

    @Test
    fun scaleMatchesDesignSpec() {
        val t = SmartIndexTypography
        assertStyle(t.headlineSmall, 24, 32, FontWeight.W400)
        assertStyle(t.titleLarge, 22, 28, FontWeight.W400)
        assertStyle(t.titleMedium, 16, 24, FontWeight.W500)
        assertStyle(t.titleSmall, 14, 20, FontWeight.W500)
        assertStyle(t.bodyLarge, 16, 24, FontWeight.W400)
        assertStyle(t.bodyMedium, 14, 20, FontWeight.W400)
        assertStyle(t.bodySmall, 12, 16, FontWeight.W400)
        assertStyle(t.labelLarge, 14, 20, FontWeight.W500)
        assertStyle(t.labelMedium, 12, 16, FontWeight.W500)
        assertStyle(t.labelSmall, 11, 16, FontWeight.W500)
    }
}
