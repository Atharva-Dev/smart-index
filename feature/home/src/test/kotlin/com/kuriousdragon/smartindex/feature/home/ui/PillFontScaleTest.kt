package com.kuriousdragon.smartindex.feature.home.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PillFontScaleTest {
    @Test
    fun normalScaleIsUnchanged() {
        assertThat(pillFontScale(1.0f)).isEqualTo(1.0f)
    }

    @Test
    fun capValueIsUnchanged() {
        assertThat(pillFontScale(1.3f)).isEqualTo(1.3f)
    }

    @Test
    fun largeScaleIsCapped() {
        assertThat(pillFontScale(2.0f)).isEqualTo(1.3f)
    }

    @Test
    fun smallScaleIsUnchanged() {
        assertThat(pillFontScale(0.85f)).isEqualTo(0.85f)
    }
}
