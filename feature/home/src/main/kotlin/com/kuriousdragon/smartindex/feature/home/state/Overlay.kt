package com.kuriousdragon.smartindex.feature.home.state

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ResultItem

/** The one modal thing shown above the list, if any. */
sealed interface Overlay {
    data object None : Overlay

    data class FileSheet(val item: ResultItem) : Overlay

    data class CallSheet(val item: ResultItem) : Overlay

    data class RecordingSheet(val item: ResultItem) : Overlay

    data object DateFilterSheet : Overlay

    /** "Go to date" (Home). */
    data object HomeDatePicker : Overlay

    /** "Pick a day…". */
    data object SearchDayPicker : Overlay

    /** "Custom range…". */
    data object RangePicker : Overlay

    /** AllowInSettingsDialog; never Group.FILES. */
    data class SettingsDialog(val group: Group) : Overlay
}
