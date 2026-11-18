package com.kuriousdragon.smartindex.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kuriousdragon.smartindex.core.designsystem.theme.Durations
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.permissions.ui.AllowInSettingsDialog
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingUiState
import com.kuriousdragon.smartindex.feature.onboarding.ui.OnboardingTestTags
import com.kuriousdragon.smartindex.feature.onboarding.ui.StepPage
import com.kuriousdragon.smartindex.feature.onboarding.ui.SummaryPage
import com.kuriousdragon.smartindex.feature.onboarding.ui.WelcomePage

/** Stateless onboarding host: switches pages (cross-fade, instant under reduced motion) and hosts the settings dialog. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onGetStarted: () -> Unit,
    onAllow: (Group) -> Unit,
    onSkip: () -> Unit,
    onStartIndexing: () -> Unit,
    onSettingsDialogCancel: () -> Unit,
    onSettingsDialogOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .testTag(OnboardingTestTags.ROOT),
    ) {
        val pageContent: @Composable (OnboardingPage) -> Unit = { page ->
            key(page) {
                when (page) {
                    OnboardingPage.Welcome -> WelcomePage(onGetStarted = onGetStarted)

                    is OnboardingPage.Step ->
                        StepPage(group = page.group, onAllow = { onAllow(page.group) }, onSkip = onSkip)

                    OnboardingPage.Summary ->
                        SummaryPage(
                            rows = state.summary,
                            isStarting = state.isStarting,
                            onStartIndexing = onStartIndexing,
                        )
                }
            }
        }
        if (reducedMotion) {
            pageContent(state.page)
        } else {
            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    fadeIn(tween(Durations.CROSS_FADE_MS)) togetherWith
                        fadeOut(tween(Durations.CROSS_FADE_MS))
                },
                label = "onboardingPage",
                content = { page -> pageContent(page) },
            )
        }
        state.settingsDialog?.let { group ->
            Box(Modifier.testTag(OnboardingTestTags.SETTINGS_DIALOG)) {
                AllowInSettingsDialog(
                    group = group,
                    onDismiss = onSettingsDialogCancel,
                    onOpenSettings = onSettingsDialogOpenSettings,
                )
            }
        }
    }
}
