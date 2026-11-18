package com.kuriousdragon.smartindex.feature.onboarding.model

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState

/** The five permission groups in step order (Design Spec §6.11): steps 1…5. */
val ONBOARDING_GROUPS: List<Group> =
    listOf(
        Group.PHOTOS_VIDEOS,
        Group.FILES,
        Group.MESSAGES,
        Group.CALL_LOG,
        Group.CONTACTS,
    )

sealed interface OnboardingPage {
    data object Welcome : OnboardingPage

    /** Step [number] of 5 (1-based, fixed per group, so "Step 4 of 5" is stable even if earlier steps were skipped). */
    data class Step(val group: Group) : OnboardingPage {
        val number: Int get() = ONBOARDING_GROUPS.indexOf(group) + 1
    }

    data object Summary : OnboardingPage
}

object PageFlow {
    private const val WELCOME_CODE = 0
    private const val SUMMARY_CODE = 6

    /** C2: GRANTED and PARTIAL are answered; null (unknown), DENIED and DENIED_PERMANENTLY are not. */
    fun isAnswered(state: GroupState?): Boolean = state == GroupState.GRANTED || state == GroupState.PARTIAL

    /** Welcome → first unanswered step, else Summary. Step(g) → next unanswered step after g, else Summary. Summary → Summary. */
    fun next(
        from: OnboardingPage,
        states: Map<Group, GroupState>,
    ): OnboardingPage =
        when (from) {
            OnboardingPage.Summary -> OnboardingPage.Summary

            OnboardingPage.Welcome -> firstUnanswered(ONBOARDING_GROUPS, states) ?: OnboardingPage.Summary

            is OnboardingPage.Step -> {
                val after = ONBOARDING_GROUPS.drop(ONBOARDING_GROUPS.indexOf(from.group) + 1)
                firstUnanswered(after, states) ?: OnboardingPage.Summary
            }
        }

    /** Summary → last unanswered step, else Welcome. Step(g) → previous unanswered step before g, else Welcome. Welcome → null. */
    fun previous(
        from: OnboardingPage,
        states: Map<Group, GroupState>,
    ): OnboardingPage? =
        when (from) {
            OnboardingPage.Welcome -> null

            OnboardingPage.Summary -> firstUnanswered(ONBOARDING_GROUPS.asReversed(), states) ?: OnboardingPage.Welcome

            is OnboardingPage.Step -> {
                val before = ONBOARDING_GROUPS.take(ONBOARDING_GROUPS.indexOf(from.group)).asReversed()
                firstUnanswered(before, states) ?: OnboardingPage.Welcome
            }
        }

    /** SavedStateHandle encoding: Welcome 0, Step 1…5 (= number), Summary 6. [decode] maps anything else to Welcome. */
    fun encode(page: OnboardingPage): Int =
        when (page) {
            OnboardingPage.Welcome -> WELCOME_CODE
            is OnboardingPage.Step -> page.number
            OnboardingPage.Summary -> SUMMARY_CODE
        }

    fun decode(value: Int?): OnboardingPage =
        when (value) {
            null -> OnboardingPage.Welcome
            in 1..ONBOARDING_GROUPS.size -> OnboardingPage.Step(ONBOARDING_GROUPS[value - 1])
            SUMMARY_CODE -> OnboardingPage.Summary
            else -> OnboardingPage.Welcome
        }

    private fun firstUnanswered(
        groups: List<Group>,
        states: Map<Group, GroupState>,
    ): OnboardingPage.Step? =
        groups.firstOrNull { !isAnswered(states[it]) }?.let { OnboardingPage.Step(it) }
}
