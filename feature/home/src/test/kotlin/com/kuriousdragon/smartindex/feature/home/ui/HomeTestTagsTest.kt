package com.kuriousdragon.smartindex.feature.home.ui

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.feature.home.model.Tab
import org.junit.Test

class HomeTestTagsTest {
    @Test
    fun `harness tags equal the strings of LLD 4_11`() {
        assertThat(HomeTestTags.SEARCH_BAR).isEqualTo("sidx_search_bar")
        assertThat(HomeTestTags.SEARCH_FIELD).isEqualTo("sidx_search_field")
        assertThat(HomeTestTags.SEARCH_CLEAR).isEqualTo("sidx_search_clear")
        assertThat(HomeTestTags.LIST).isEqualTo("sidx_list")
        assertThat(HomeTestTags.ROW).isEqualTo("sidx_row")
        assertThat(HomeTestTags.NO_RESULTS).isEqualTo("sidx_no_results")
        assertThat(HomeTestTags.EMPTY_DAY).isEqualTo("sidx_empty_day")
        assertThat(HomeTestTags.DATE_CHIP).isEqualTo("sidx_date_chip")
        assertThat(HomeTestTags.PREV_DAY).isEqualTo("sidx_prev_day")
        assertThat(HomeTestTags.NEXT_DAY).isEqualTo("sidx_next_day")
        assertThat(HomeTestTags.DATE_BUTTON).isEqualTo("sidx_date_button")
        assertThat(HomeTestTags.INDEXING_BANNER).isEqualTo("sidx_indexing_banner")
    }

    @Test
    fun `additional tags equal the strings of LLD 4_11`() {
        assertThat(HomeTestTags.HINT).isEqualTo("sidx_hint")
        assertThat(HomeTestTags.EMPTY_TAB).isEqualTo("sidx_empty_tab")
        assertThat(HomeTestTags.EMPTY_RANGE).isEqualTo("sidx_empty_range")
        assertThat(HomeTestTags.TAB_LOCKED).isEqualTo("sidx_tab_locked")
        assertThat(HomeTestTags.LOAD_ERROR).isEqualTo("sidx_load_error")
        assertThat(HomeTestTags.SKELETON).isEqualTo("sidx_skeleton")
        assertThat(HomeTestTags.PERMISSION_BANNER).isEqualTo("sidx_permission_banner")
        assertThat(HomeTestTags.MINI_PLAYER).isEqualTo("sidx_mini_player")
        assertThat(HomeTestTags.PLAYER_PLAY).isEqualTo("sidx_player_play")
        assertThat(HomeTestTags.PLAYER_CLOSE).isEqualTo("sidx_player_close")
        assertThat(HomeTestTags.PLAYER_SLIDER).isEqualTo("sidx_player_slider")
        assertThat(HomeTestTags.SHEET_FILE).isEqualTo("sidx_sheet_file")
        assertThat(HomeTestTags.SHEET_CALL).isEqualTo("sidx_sheet_call")
        assertThat(HomeTestTags.SHEET_RECORDING).isEqualTo("sidx_sheet_recording")
    }

    @Test
    fun `date preset tags match the constants`() {
        assertThat(HomeTestTags.datePreset("ANY")).isEqualTo(HomeTestTags.DATE_PRESET_ANY)
        assertThat(HomeTestTags.datePreset("TODAY")).isEqualTo(HomeTestTags.DATE_PRESET_TODAY)
        assertThat(HomeTestTags.datePreset("YESTERDAY")).isEqualTo(HomeTestTags.DATE_PRESET_YESTERDAY)
        assertThat(HomeTestTags.datePreset("LAST_7_DAYS")).isEqualTo(HomeTestTags.DATE_PRESET_LAST_7_DAYS)
        assertThat(HomeTestTags.datePreset("LAST_30_DAYS")).isEqualTo(HomeTestTags.DATE_PRESET_LAST_30_DAYS)
        assertThat(HomeTestTags.DATE_PRESET_ANY).isEqualTo("sidx_date_preset_ANY")
    }

    @Test
    fun `tab tags match Tab tag`() {
        for (tab in Tab.entries) assertThat(HomeTestTags.tab(tab)).isEqualTo(tab.tag)
    }
}
