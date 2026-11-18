package com.kuriousdragon.smartindex.core.index.db.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.RoomRawQuery

/** Read side of the search SQL (LLD §5.6); queries come from `SearchSql`, since they touch the FTS table. */
@Dao
interface SearchDao {
    @RawQuery
    suspend fun keys(query: RoomRawQuery): List<KeyRow>

    @RawQuery
    suspend fun typeCounts(query: RoomRawQuery): List<TypeCount>
}
