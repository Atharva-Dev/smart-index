package com.kuriousdragon.smartindex.feature.onboarding.model

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState

data class OnboardingUiState(
    val page: OnboardingPage = OnboardingPage.Welcome,
    /** Current state of all five groups (copy of PermissionService.state.value). */
    val groupStates: Map<Group, GroupState> = emptyMap(),
    /** Five rows in [ONBOARDING_GROUPS] order; only shown on the Summary. */
    val summary: List<SummaryRow> = emptyList(),
    /** Non-null → show AllowInSettingsDialog(group) (C3). Never FILES. */
    val settingsDialog: Group? = null,
    /** "Start indexing" was tapped; the button is disabled until the screen is left. */
    val isStarting: Boolean = false,
)

data class SummaryRow(val group: Group, val status: SummaryStatus)

enum class SummaryStatus { ALLOWED, ALLOWED_SELECTED_ONLY, SKIPPED }

/** GRANTED → ALLOWED; PARTIAL → ALLOWED_SELECTED_ONLY; everything else (incl. unknown) → SKIPPED. */
fun buildSummary(states: Map<Group, GroupState>): List<SummaryRow> =
    ONBOARDING_GROUPS.map { group ->
        val status =
            when (states[group]) {
                GroupState.GRANTED -> SummaryStatus.ALLOWED
                GroupState.PARTIAL -> SummaryStatus.ALLOWED_SELECTED_ONLY
                else -> SummaryStatus.SKIPPED
            }
        SummaryRow(group, status)
    }

sealed interface OnboardingEffect {
    /** The route must run its group requester for [group] (runtime dialog, or the Settings page). */
    data class RequestGroup(val group: Group) : OnboardingEffect

    /** reindexAll() was called; the route calls onFinished(). */
    data object Finish : OnboardingEffect
}
