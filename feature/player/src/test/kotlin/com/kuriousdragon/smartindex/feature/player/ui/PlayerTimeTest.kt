package com.kuriousdragon.smartindex.feature.player.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerTimeTest {
    @Test fun `unknown is 0 colon 00`() = assertThat(formatClock(0)).isEqualTo("0:00")

    @Test fun `minutes and seconds`() = assertThat(formatClock(83_999)).isEqualTo("1:23")

    @Test fun `hours switch format`() = assertThat(formatClock(3_725_000)).isEqualTo("1:02:05")

    @Test fun `negative counts as zero`() = assertThat(formatClock(-5)).isEqualTo("0:00")
}
