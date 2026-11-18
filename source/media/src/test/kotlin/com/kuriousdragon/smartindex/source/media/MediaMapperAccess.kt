package com.kuriousdragon.smartindex.source.media

import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.source.media.map.MediaMapper
import com.kuriousdragon.smartindex.source.media.map.MediaRow

/** Test access to the real mapper for the mapper-throws seam. */
internal object MediaMapperAccess {
    fun map(row: MediaRow, type: ItemType, now: Long): ItemRecord = MediaMapper.map(row, type, now)
}
