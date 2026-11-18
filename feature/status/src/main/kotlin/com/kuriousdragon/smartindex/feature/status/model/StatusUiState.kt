package com.kuriousdragon.smartindex.feature.status.model

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Source

/** Everything the Index status screen shows (LLD §3.4). */
data class StatusUiState(
    val banner: BannerUi,
    /** Seven rows in tab order: PHOTO, VIDEO, DOCUMENT, OTHER_FILE, CALL, RECORDING, MESSAGE. */
    val typeRows: List<TypeRowUi>,
    /** Five rows in Group enum order (the onboarding order). */
    val permissionRows: List<PermissionRowUi>,
    val reindex: ReindexUi,
    val dialog: StatusDialog? = null,
)

sealed interface BannerUi {
    /** Idle, no error. [lastRunMs] null means "Not indexed yet". */
    data class UpToDate(val lastRunMs: Long?) : BannerUi

    /** A run is active but it is not FULL (silent catch-up, PREPARING, LINKING). */
    data object Updating : BannerUi

    /** A FULL run is active. [percent] is null while the total is unknown. */
    data class Indexing(val done: Int, val total: Int, val percent: Int?) : BannerUi

    data class Error(val kind: IndexError.Kind, val sources: Set<Source>) : BannerUi
}

data class TypeRowUi(val type: ItemType, val count: Int?, val subLine: SubLine)

sealed interface SubLine {
    data object None : SubLine

    /** "PDF, Office and text files". */
    data object DocumentKinds : SubLine

    /** "{n} linked to calls". */
    data class RecordingsLinked(val n: Int) : SubLine

    /** Error colour; the count is shown as "—". */
    data object NoAccess : SubLine
}

data class PermissionRowUi(val group: Group, val state: GroupState, val action: PermissionAction)

/** NONE: allowed. ALLOW: tonal "Allow". ALLOW_ALL: PARTIAL photos, tonal "Allow all". */
enum class PermissionAction { NONE, ALLOW, ALLOW_ALL }

/** False while [com.kuriousdragon.smartindex.core.model.IndexStatus.isRunning]; the helper text is shown then. */
data class ReindexUi(val enabled: Boolean)

sealed interface StatusDialog {
    data class AllowInSettings(val group: Group) : StatusDialog

    data object ConfirmReindex : StatusDialog
}

sealed interface StatusEffect {
    data class RequestGroup(val group: Group) : StatusEffect
}
