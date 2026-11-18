package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.feature.home.HomeTrace
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TraceEffectsTest {
    @get:Rule
    val rule = createComposeRule()

    private val ends = mutableListOf<Pair<String, Int>>()
    private val shown = mutableListOf<Int>()
    private val oldSink = HomeTrace.sink

    private var commit by mutableStateOf(commit(1, PerfTrace.TIMELINE_FIRST_PAGE))
    private var refresh by mutableStateOf<LoadState>(LoadState.NotLoading(endOfPaginationReached = false))

    init {
        HomeTrace.sink = object : HomeTrace.Sink {
            override fun begin(name: String, cookie: Int) = Unit

            override fun end(name: String, cookie: Int) {
                ends += name to cookie
            }
        }
    }

    @After
    fun tearDown() {
        HomeTrace.sink = oldSink
    }

    private fun commit(id: Int, section: String?) = Commit(
        id = id,
        request = ListRequest.Timeline(TestEnv.today, null),
        grouping = Grouping.PART_OF_DAY,
        env = TestEnv.env,
        traceSection = section,
    )

    private fun setContent() = rule.setContent {
        FirstPageTraceEffect(commit, { refresh }, { shown += it })
    }

    @Test
    fun `the previous generation's NotLoading does not end the trace`() {
        setContent()
        rule.waitForIdle()
        assertThat(ends).isEmpty()
        assertThat(shown).isEmpty()
    }

    @Test
    fun `Loading then NotLoading ends the trace once and reports the commit`() {
        setContent()
        refresh = LoadState.Loading
        rule.waitForIdle()
        assertThat(ends).isEmpty()
        refresh = LoadState.NotLoading(endOfPaginationReached = true)
        rule.waitForIdle()
        assertThat(ends).containsExactly(PerfTrace.TIMELINE_FIRST_PAGE to 1)
        assertThat(shown).containsExactly(1)
        refresh = LoadState.Loading
        refresh = LoadState.NotLoading(endOfPaginationReached = true)
        rule.waitForIdle()
        assertThat(ends).hasSize(1)
    }

    @Test
    fun `a superseded commit is never ended`() {
        setContent()
        refresh = LoadState.Loading
        rule.waitForIdle()
        commit = commit(2, PerfTrace.SEARCH_FIRST_PAGE)
        rule.waitForIdle()
        refresh = LoadState.NotLoading(endOfPaginationReached = true)
        rule.waitForIdle()
        assertThat(ends.map { it.second }).doesNotContain(1)
        assertThat(shown).doesNotContain(1)
    }

    @Test
    fun `a commit without a trace section is reported at once and never ended`() {
        commit = commit(3, null)
        setContent()
        rule.waitForIdle()
        assertThat(shown).containsExactly(3)
        assertThat(ends).isEmpty()
    }
}
