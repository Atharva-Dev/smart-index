package com.kuriousdragon.smartindex.feature.home.state

import androidx.compose.runtime.Immutable
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.Tab
import java.time.LocalDate

enum class HomeMode { HOME, SEARCH }

@Immutable
data class HomeUiState(
    val mode: HomeMode,
    val env: DeviceEnv,
    /** The Home day (kept while in Search). */
    val day: LocalDate,
    /** Raw text-field content (not trimmed, max 200 chars). */
    val query: String,
    val dateFilter: DateFilter,
    val tab: Tab,
    /** Null = unknown, hide badges. */
    val counts: Map<ItemType, Int>?,
    /** ALL is always Open. */
    val tabAccess: Map<Tab, TabAccess>,
    val commit: Commit,
    val banners: BannersUi,
    val overlay: Overlay,
    /** IndexStatus.isCatchUp: thin progress under the tabs. */
    val isCatchUp: Boolean,
    /** Drives ReportDrawnWhen (LLD §5.8). */
    val firstPageShown: Boolean,
) {
    val canGoNext: Boolean get() = day.isBefore(env.today)
}

sealed interface TabAccess {
    data object Open : TabAccess

    data class Locked(val group: Group, val state: GroupState) : TabAccess
}

/** Which recording the list row shows as playing (separate StateFlow, LLD §3.4). */
@Immutable
data class RowPlayback(val recordingId: Long?, val isPlaying: Boolean)
