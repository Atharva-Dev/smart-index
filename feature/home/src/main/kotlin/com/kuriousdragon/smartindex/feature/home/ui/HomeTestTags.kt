package com.kuriousdragon.smartindex.feature.home.ui

import com.kuriousdragon.smartindex.feature.home.model.Tab

/** Test tags (LLD — Home & Search UI §4.11). Use these constants, never string literals. */
object HomeTestTags {
    const val SEARCH_BAR = "sidx_search_bar"
    const val SEARCH_FIELD = "sidx_search_field"
    const val SEARCH_CLEAR = "sidx_search_clear"
    const val LIST = "sidx_list"
    const val ROW = "sidx_row"
    const val NO_RESULTS = "sidx_no_results"
    const val EMPTY_DAY = "sidx_empty_day"
    const val DATE_CHIP = "sidx_date_chip"
    const val PREV_DAY = "sidx_prev_day"
    const val NEXT_DAY = "sidx_next_day"
    const val DATE_BUTTON = "sidx_date_button"
    const val INDEXING_BANNER = "sidx_indexing_banner"

    const val DATE_PRESET_ANY = "sidx_date_preset_ANY"
    const val DATE_PRESET_TODAY = "sidx_date_preset_TODAY"
    const val DATE_PRESET_YESTERDAY = "sidx_date_preset_YESTERDAY"
    const val DATE_PRESET_LAST_7_DAYS = "sidx_date_preset_LAST_7_DAYS"
    const val DATE_PRESET_LAST_30_DAYS = "sidx_date_preset_LAST_30_DAYS"

    const val HINT = "sidx_hint"
    const val EMPTY_TAB = "sidx_empty_tab"
    const val EMPTY_RANGE = "sidx_empty_range"
    const val TAB_LOCKED = "sidx_tab_locked"
    const val LOAD_ERROR = "sidx_load_error"
    const val SKELETON = "sidx_skeleton"
    const val PERMISSION_BANNER = "sidx_permission_banner"
    const val MINI_PLAYER = "sidx_mini_player"
    const val PLAYER_PLAY = "sidx_player_play"
    const val PLAYER_CLOSE = "sidx_player_close"
    const val PLAYER_SLIDER = "sidx_player_slider"
    const val SHEET_FILE = "sidx_sheet_file"
    const val SHEET_CALL = "sidx_sheet_call"
    const val SHEET_RECORDING = "sidx_sheet_recording"

    /** Tag of a date preset, e.g. `datePreset("TODAY")` = `sidx_date_preset_TODAY`. */
    fun datePreset(name: String): String = "sidx_date_preset_$name"

    /** Tag of a tab (equals [Tab.tag]). */
    fun tab(tab: Tab): String = tab.tag
}
