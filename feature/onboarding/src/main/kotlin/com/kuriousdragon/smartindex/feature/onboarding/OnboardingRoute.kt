package com.kuriousdragon.smartindex.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuriousdragon.smartindex.core.permissions.ui.rememberGroupRequester
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingEffect
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage

/** Stateful onboarding route: wires the ViewModel to the group requester, back handling and [OnboardingScreen]. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val request = rememberGroupRequester(viewModel.permissionService) { group, groupState ->
        viewModel.onGroupResult(group, groupState)
    }
    val currentRequest by rememberUpdatedState(request)
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.RequestGroup -> currentRequest(effect.group)
                OnboardingEffect.Finish -> currentOnFinished()
            }
        }
    }

    BackHandler(enabled = state.page != OnboardingPage.Welcome || state.settingsDialog != null) {
        viewModel.onBack()
    }

    OnboardingScreen(
        state = state,
        onGetStarted = viewModel::onGetStarted,
        onAllow = viewModel::onAllowClick,
        onSkip = viewModel::onSkip,
        onStartIndexing = viewModel::onStartIndexing,
        onSettingsDialogCancel = viewModel::onSettingsDialogCancel,
        onSettingsDialogOpenSettings = viewModel::onSettingsDialogOpenSettings,
        modifier = modifier.semantics { testTagsAsResourceId = true },
    )
}
