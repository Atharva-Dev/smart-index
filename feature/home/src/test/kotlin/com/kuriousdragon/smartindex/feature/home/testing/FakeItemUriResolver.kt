package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.OpenTarget
import kotlinx.coroutines.CompletableDeferred

/** [ItemUriResolver] returning [target]; records every call. */
internal class FakeItemUriResolver(var target: OpenTarget = OpenTarget.NotAFile) : ItemUriResolver {
    val calls = mutableListOf<Pair<ItemType, String?>>()

    /** When set, [resolve] suspends on it first (to test the open guard). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun resolve(type: ItemType, contentUri: String?): OpenTarget {
        calls += type to contentUri
        gate?.await()
        return target
    }
}
