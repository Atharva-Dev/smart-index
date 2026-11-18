package com.kuriousdragon.smartindex.core.designsystem.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lightThemeExposesLightTokens() {
        var primary = Color.Unspecified
        var warn = Color.Unspecified
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                warn = SmartIndexTheme.colors.warnContainer
            }
        }
        rule.waitForIdle()
        assertThat(primary).isEqualTo(Color(0xFF2B55C4))
        assertThat(warn).isEqualTo(Color(0xFFFFEFC7))
    }

    @Test
    fun darkThemeExposesDarkTokens() {
        var primary = Color.Unspecified
        var warn = Color.Unspecified
        rule.setContent {
            SmartIndexTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                warn = SmartIndexTheme.colors.warnContainer
            }
        }
        rule.waitForIdle()
        assertThat(primary).isEqualTo(Color(0xFFB4C5FF))
        assertThat(warn).isEqualTo(Color(0xFF4A3600))
    }

    @Test
    fun reducedMotionFollowsAnimatorDurationScale() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertThat(isReducedMotion(context)).isTrue()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertThat(isReducedMotion(context)).isFalse()

        var reduced = false
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.setContent { SmartIndexTheme(darkTheme = false) { reduced = LocalReducedMotion.current } }
        rule.waitForIdle()
        assertThat(reduced).isTrue()
    }
}
