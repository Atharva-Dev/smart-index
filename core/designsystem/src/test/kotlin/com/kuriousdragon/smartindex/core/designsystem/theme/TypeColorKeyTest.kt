package com.kuriousdragon.smartindex.core.designsystem.theme

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Test

class TypeColorKeyTest {
    @Test
    fun mapsEveryItemType() {
        val expected = mapOf(
            ItemType.PHOTO to TypeColorKey.PHOTO,
            ItemType.VIDEO to TypeColorKey.VIDEO,
            ItemType.DOCUMENT to TypeColorKey.DOC,
            ItemType.OTHER_FILE to TypeColorKey.FILE,
            ItemType.CALL to TypeColorKey.CALL,
            ItemType.RECORDING to TypeColorKey.REC,
            ItemType.MESSAGE to TypeColorKey.MSG,
        )
        assertThat(expected.keys).containsExactlyElementsIn(ItemType.values().toList())
        expected.forEach { (type, key) -> assertThat(typeColorKey(type, null)).isEqualTo(key) }
    }

    @Test
    fun missedCallUsesMissedKeyOnly() {
        assertThat(typeColorKey(ItemType.CALL, Direction.MISSED)).isEqualTo(TypeColorKey.MISSED)
        assertThat(typeColorKey(ItemType.CALL, Direction.INCOMING)).isEqualTo(TypeColorKey.CALL)
        assertThat(typeColorKey(ItemType.CALL, Direction.REJECTED)).isEqualTo(TypeColorKey.CALL)
        assertThat(typeColorKey(ItemType.MESSAGE, Direction.MISSED)).isEqualTo(TypeColorKey.MSG)
    }
}
