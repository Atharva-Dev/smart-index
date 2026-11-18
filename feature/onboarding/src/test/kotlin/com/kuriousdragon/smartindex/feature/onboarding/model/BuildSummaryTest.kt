package com.kuriousdragon.smartindex.feature.onboarding.model

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import org.junit.Test

class BuildSummaryTest {
    @Test
    fun `granted partial denied permanently denied and missing map to the right status`() {
        val rows =
            buildSummary(
                mapOf(
                    Group.PHOTOS_VIDEOS to GroupState.PARTIAL,
                    Group.FILES to GroupState.GRANTED,
                    Group.MESSAGES to GroupState.DENIED,
                    Group.CALL_LOG to GroupState.DENIED_PERMANENTLY,
                ),
            )
        assertThat(rows.associate { it.group to it.status }).containsExactly(
            Group.PHOTOS_VIDEOS, SummaryStatus.ALLOWED_SELECTED_ONLY,
            Group.FILES, SummaryStatus.ALLOWED,
            Group.MESSAGES, SummaryStatus.SKIPPED,
            Group.CALL_LOG, SummaryStatus.SKIPPED,
            Group.CONTACTS, SummaryStatus.SKIPPED,
        )
    }

    @Test
    fun `always five rows in onboarding group order`() {
        assertThat(buildSummary(emptyMap()).map { it.group }).containsExactlyElementsIn(ONBOARDING_GROUPS).inOrder()
    }
}
