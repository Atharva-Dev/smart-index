package com.kuriousdragon.smartindex.feature.home.model

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import java.time.LocalDate
import org.junit.Test

class ListEntryTest {
    private fun item(id: Long) = ResultItem(
        id = id, type = ItemType.PHOTO, title = "t", time = 0L, folder = null, sizeBytes = null,
        durationMs = null, ext = null, direction = null, msgKind = null, hasAttachment = false,
        contactName = null, number = null, contentUri = null, snippet = null, matches = emptyMap(),
        linkedRecordingId = null,
    )

    @Test
    fun `row key is r plus id`() {
        assertThat(ListEntry.Row(item(42)).key).isEqualTo("r42")
    }

    @Test
    fun `header keys are stable`() {
        assertThat(ListEntry.Header(ListGroup.PartOfDayGroup(PartOfDay.MORNING)).key).isEqualTo("h:PART:MORNING")
        val date = LocalDate.of(2026, 11, 3)
        assertThat(ListEntry.Header(ListGroup.DateGroup(date)).key).isEqualTo("h:DATE:${date.toEpochDay()}")
        assertThat(
            ListEntry.Header(ListGroup.DateGroup(date)).key,
        ).isEqualTo(ListEntry.Header(ListGroup.DateGroup(date)).key)
    }

    @Test
    fun `keys are unique across rows and headers`() {
        val entries = listOf(
            ListEntry.Row(item(1)),
            ListEntry.Row(item(2)),
            *PartOfDay.entries.map { ListEntry.Header(ListGroup.PartOfDayGroup(it)) }.toTypedArray(),
            ListEntry.Header(ListGroup.DateGroup(LocalDate.of(2026, 11, 3))),
            ListEntry.Header(ListGroup.DateGroup(LocalDate.of(2026, 11, 2))),
        )
        assertThat(entries.map { it.key }.toSet()).hasSize(entries.size)
    }
}
