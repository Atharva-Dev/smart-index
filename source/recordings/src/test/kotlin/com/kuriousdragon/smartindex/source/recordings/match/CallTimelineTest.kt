package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import org.junit.Test

class CallTimelineTest {
    private fun prepared(id: Long, ts: Long) =
        PreparedCall(
            RecordingInputs.Call(id, ts, 1000, null, null, null, null),
            CallIdentity(null, null, null),
        )

    private fun timeline(vararg ts: Long) = CallTimeline(ts.mapIndexed { i, t -> prepared(i + 1L, t) })

    private fun CallTimeline.idsIn(from: Long, to: Long): List<Long> {
        val out = ArrayList<Long>()
        forEachInRange(from, to) { out += it.call.id }
        return out
    }

    @Test
    fun `empty timeline has size 0, lowerBound 0 and visits nothing`() {
        val t = timeline()
        assertThat(t.size).isEqualTo(0)
        assertThat(t.lowerBound(100)).isEqualTo(0)
        assertThat(t.idsIn(0, 1000)).isEmpty()
    }

    @Test
    fun `all calls before the range are not visited`() {
        val t = timeline(10, 20, 30)
        assertThat(t.lowerBound(100)).isEqualTo(3)
        assertThat(t.idsIn(100, 200)).isEmpty()
    }

    @Test
    fun `all calls after the range are not visited`() {
        val t = timeline(1000, 2000)
        assertThat(t.lowerBound(0)).isEqualTo(0)
        assertThat(t.idsIn(0, 999)).isEmpty()
    }

    @Test
    fun `duplicates of one timestamp are all visited and lowerBound returns the first`() {
        val t = timeline(10, 50, 50, 50, 90)
        assertThat(t.lowerBound(50)).isEqualTo(1)
        assertThat(t.idsIn(50, 50)).containsExactly(2L, 3L, 4L).inOrder()
    }

    @Test
    fun `bounds are inclusive on both ends`() {
        val t = timeline(10, 20, 30, 40)
        assertThat(t.idsIn(20, 30)).containsExactly(2L, 3L).inOrder()
        assertThat(t.idsIn(21, 29)).isEmpty()
        assertThat(t.idsIn(10, 40)).containsExactly(1L, 2L, 3L, 4L).inOrder()
    }

    @Test
    fun `lowerBound is the first index with timestamp at or after t`() {
        val t = timeline(10, 20, 30)
        assertThat(t.lowerBound(9)).isEqualTo(0)
        assertThat(t.lowerBound(10)).isEqualTo(0)
        assertThat(t.lowerBound(11)).isEqualTo(1)
        assertThat(t.lowerBound(30)).isEqualTo(2)
        assertThat(t.lowerBound(31)).isEqualTo(3)
    }
}
