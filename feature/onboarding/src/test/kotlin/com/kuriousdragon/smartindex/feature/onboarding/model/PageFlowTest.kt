package com.kuriousdragon.smartindex.feature.onboarding.model

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage.Step
import org.junit.Test

class PageFlowTest {
    private val none = emptyMap<Group, GroupState>()

    private fun all(state: GroupState) = ONBOARDING_GROUPS.associateWith { state }

    @Test
    fun `next from Welcome with nothing answered is step 1`() {
        assertThat(PageFlow.next(OnboardingPage.Welcome, none)).isEqualTo(Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `next walks all unanswered steps then Summary`() {
        var page: OnboardingPage = OnboardingPage.Welcome
        val seen = mutableListOf<OnboardingPage>()
        repeat(6) {
            page = PageFlow.next(page, none)
            seen += page
        }
        assertThat(seen).containsExactly(
            Step(Group.PHOTOS_VIDEOS),
            Step(Group.FILES),
            Step(Group.MESSAGES),
            Step(Group.CALL_LOG),
            Step(Group.CONTACTS),
            OnboardingPage.Summary,
        ).inOrder()
    }

    @Test
    fun `granted groups are skipped going forward`() {
        val states = mapOf(Group.PHOTOS_VIDEOS to GroupState.GRANTED, Group.FILES to GroupState.GRANTED)
        assertThat(PageFlow.next(OnboardingPage.Welcome, states)).isEqualTo(Step(Group.MESSAGES))
        assertThat(PageFlow.next(Step(Group.MESSAGES), states + (Group.CALL_LOG to GroupState.GRANTED)))
            .isEqualTo(Step(Group.CONTACTS))
    }

    @Test
    fun `partial counts as answered`() {
        val states = mapOf(Group.PHOTOS_VIDEOS to GroupState.PARTIAL)
        assertThat(PageFlow.next(OnboardingPage.Welcome, states)).isEqualTo(Step(Group.FILES))
        assertThat(PageFlow.isAnswered(GroupState.PARTIAL)).isTrue()
    }

    @Test
    fun `denied and denied permanently and unknown are not answered`() {
        assertThat(PageFlow.isAnswered(GroupState.DENIED)).isFalse()
        assertThat(PageFlow.isAnswered(GroupState.DENIED_PERMANENTLY)).isFalse()
        assertThat(PageFlow.isAnswered(null)).isFalse()
        assertThat(PageFlow.isAnswered(GroupState.GRANTED)).isTrue()
        val states = all(GroupState.DENIED_PERMANENTLY)
        assertThat(PageFlow.next(OnboardingPage.Welcome, states)).isEqualTo(Step(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `all answered goes Welcome to Summary and Summary back to Welcome`() {
        val states = all(GroupState.GRANTED)
        assertThat(PageFlow.next(OnboardingPage.Welcome, states)).isEqualTo(OnboardingPage.Summary)
        assertThat(PageFlow.previous(OnboardingPage.Summary, states)).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `Summary stays Summary`() {
        assertThat(PageFlow.next(OnboardingPage.Summary, none)).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `next after the last unanswered step is Summary`() {
        val states = all(GroupState.GRANTED) + (Group.MESSAGES to GroupState.DENIED)
        assertThat(PageFlow.next(Step(Group.MESSAGES), states)).isEqualTo(OnboardingPage.Summary)
    }

    @Test
    fun `previous from Summary is the last unanswered step`() {
        val states =
            all(GroupState.GRANTED) + (Group.FILES to GroupState.DENIED) + (Group.MESSAGES to GroupState.DENIED)
        assertThat(PageFlow.previous(OnboardingPage.Summary, states)).isEqualTo(Step(Group.MESSAGES))
        assertThat(PageFlow.previous(OnboardingPage.Summary, none)).isEqualTo(Step(Group.CONTACTS))
    }

    @Test
    fun `previous from a step is the previous unanswered step else Welcome`() {
        val states = mapOf(Group.FILES to GroupState.GRANTED)
        assertThat(PageFlow.previous(Step(Group.MESSAGES), states)).isEqualTo(Step(Group.PHOTOS_VIDEOS))
        assertThat(PageFlow.previous(Step(Group.FILES), none)).isEqualTo(Step(Group.PHOTOS_VIDEOS))
        assertThat(PageFlow.previous(Step(Group.PHOTOS_VIDEOS), none)).isEqualTo(OnboardingPage.Welcome)
        assertThat(PageFlow.previous(Step(Group.MESSAGES), all(GroupState.GRANTED))).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `previous from Welcome is null`() {
        assertThat(PageFlow.previous(OnboardingPage.Welcome, none)).isNull()
    }

    @Test
    fun `encode and decode round trip for all seven pages`() {
        val pages = listOf(OnboardingPage.Welcome) + ONBOARDING_GROUPS.map { Step(it) } + OnboardingPage.Summary
        assertThat(pages).hasSize(7)
        pages.forEachIndexed { index, page ->
            assertThat(PageFlow.encode(page)).isEqualTo(index)
            assertThat(PageFlow.decode(PageFlow.encode(page))).isEqualTo(page)
        }
    }

    @Test
    fun `decode of null and out of range is Welcome`() {
        assertThat(PageFlow.decode(null)).isEqualTo(OnboardingPage.Welcome)
        assertThat(PageFlow.decode(99)).isEqualTo(OnboardingPage.Welcome)
        assertThat(PageFlow.decode(-1)).isEqualTo(OnboardingPage.Welcome)
    }

    @Test
    fun `Step number is fixed per group`() {
        assertThat(ONBOARDING_GROUPS.map { Step(it).number }).containsExactly(1, 2, 3, 4, 5).inOrder()
    }
}
