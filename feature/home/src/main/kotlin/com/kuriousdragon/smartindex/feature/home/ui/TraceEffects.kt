package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.paging.LoadState
import com.kuriousdragon.smartindex.feature.home.HomeTrace
import com.kuriousdragon.smartindex.feature.home.model.Commit
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

/**
 * Ends the first-page trace section of [commit] (LLD §5.8) and reports it with [onShown].
 *
 * The Loading-then-NotLoading rule decides that a snapshot belongs to this commit: the previous generation's
 * NotLoading is skipped because a new generation always starts with Loading. A commit without a trace section
 * (Hint, Locked, tab-only change) calls [onShown] at once. A superseded commit is cancelled with its effect and
 * never ended.
 */
@Composable
internal fun FirstPageTraceEffect(commit: Commit, refresh: () -> LoadState, onShown: (Int) -> Unit) {
    val section = commit.traceSection
    LaunchedEffect(commit.id) {
        if (section == null) {
            onShown(commit.id)
            return@LaunchedEffect
        }
        snapshotFlow(refresh).dropWhile { it !is LoadState.Loading }.first { it is LoadState.NotLoading }
        withFrameNanos { }
        HomeTrace.sink.end(section, commit.id)
        onShown(commit.id)
    }
}
