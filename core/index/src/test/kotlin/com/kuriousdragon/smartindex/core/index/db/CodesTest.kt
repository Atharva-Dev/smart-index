package com.kuriousdragon.smartindex.core.index.db

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import org.junit.Assert.assertThrows
import org.junit.Test

class CodesTest {
    @Test
    fun `item type codes match the LLD table and round-trip`() {
        val expected = ItemType.entries.zip(listOf(1, 2, 3, 4, 5, 6, 7))
        expected.forEach { (e, c) ->
            assertThat(e.code()).isEqualTo(c)
            assertThat(itemType(c)).isEqualTo(e)
        }
        assertThat(ItemType.entries).hasSize(7)
    }

    @Test
    fun `source codes match the LLD table and round-trip`() {
        Source.entries.zip(listOf(1, 2, 3, 4)).forEach { (e, c) ->
            assertThat(e.code()).isEqualTo(c)
            assertThat(source(c)).isEqualTo(e)
        }
        assertThat(Source.entries).hasSize(4)
    }

    @Test
    fun `direction codes match the LLD table and round-trip`() {
        Direction.entries.zip(listOf(1, 2, 3, 4, 5, 6, 7)).forEach { (e, c) ->
            assertThat(e.code()).isEqualTo(c)
            assertThat(direction(c)).isEqualTo(e)
        }
        assertThat(Direction.entries).hasSize(7)
    }

    @Test
    fun `msg kind codes match the LLD table and round-trip`() {
        MsgKind.entries.zip(listOf(1, 2)).forEach { (e, c) ->
            assertThat(e.code()).isEqualTo(c)
            assertThat(msgKind(c)).isEqualTo(e)
        }
        assertThat(MsgKind.entries).hasSize(2)
    }

    @Test
    fun `date kind codes match the LLD table and round-trip`() {
        DateKind.entries.zip(listOf(1, 2, 3, 4)).forEach { (e, c) ->
            assertThat(e.code()).isEqualTo(c)
            assertThat(dateKind(c)).isEqualTo(e)
        }
        assertThat(DateKind.entries).hasSize(4)
    }

    @Test
    fun `unknown codes throw IllegalStateException`() {
        assertThrows(IllegalStateException::class.java) { itemType(0) }
        assertThrows(IllegalStateException::class.java) { source(99) }
        assertThrows(IllegalStateException::class.java) { direction(8) }
        assertThrows(IllegalStateException::class.java) { msgKind(3) }
        assertThrows(IllegalStateException::class.java) { dateKind(-1) }
    }
}
