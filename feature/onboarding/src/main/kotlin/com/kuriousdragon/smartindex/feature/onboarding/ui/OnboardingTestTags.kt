package com.kuriousdragon.smartindex.feature.onboarding.ui

import com.kuriousdragon.smartindex.core.model.Group

/** `Modifier.testTag` values of the onboarding screens (LLD §4.7). */
object OnboardingTestTags {
    const val ROOT = "sidx_onboarding_root"
    const val WELCOME = "sidx_onboarding_welcome"
    const val GET_STARTED = "sidx_onboarding_get_started"
    const val ALLOW = "sidx_onboarding_allow"
    const val SKIP = "sidx_onboarding_skip"
    const val SUMMARY = "sidx_onboarding_summary"
    const val START = "sidx_onboarding_start"
    const val SETTINGS_DIALOG = "sidx_onboarding_settings_dialog"

    fun stepDot(index: Int): String = "sidx_onboarding_step_dot_$index"

    fun step(group: Group): String = "sidx_onboarding_step_${group.name}"

    fun summaryRow(group: Group): String = "sidx_onboarding_summary_${group.name}"
}
