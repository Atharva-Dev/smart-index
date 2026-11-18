package com.kuriousdragon.smartindex.feature.home.model

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.TypeGating

/** The Home/Search tabs; declaration order is display order. [tag] is the test tag. */
enum class Tab(val type: ItemType?, val tag: String) {
    ALL(null, "sidx_tab_ALL"),
    PHOTOS(ItemType.PHOTO, "sidx_tab_PHOTO"),
    VIDEOS(ItemType.VIDEO, "sidx_tab_VIDEO"),
    DOCUMENTS(ItemType.DOCUMENT, "sidx_tab_DOCUMENT"),
    OTHER_FILES(ItemType.OTHER_FILE, "sidx_tab_OTHER_FILE"),
    CALLS(ItemType.CALL, "sidx_tab_CALL"),
    RECORDINGS(ItemType.RECORDING, "sidx_tab_RECORDING"),
    MESSAGES(ItemType.MESSAGE, "sidx_tab_MESSAGE"),
    ;

    /** The onboarding group whose grant unlocks this tab; null for [ALL]. */
    val group: Group? get() = type?.let(TypeGating::groupOf)
}
