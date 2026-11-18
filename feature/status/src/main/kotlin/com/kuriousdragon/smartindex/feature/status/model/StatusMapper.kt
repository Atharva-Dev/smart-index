package com.kuriousdragon.smartindex.feature.status.model

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.TypeGating

/** Pure mapping from the orchestrator and permission snapshots to [StatusUiState] (LLD §3.4). */
object StatusMapper {
    /** The seven status rows in tab order. */
    private val TYPE_ORDER = listOf(
        ItemType.PHOTO,
        ItemType.VIDEO,
        ItemType.DOCUMENT,
        ItemType.OTHER_FILE,
        ItemType.CALL,
        ItemType.RECORDING,
        ItemType.MESSAGE,
    )

    fun map(status: IndexStatus, permissions: Map<Group, GroupState>, dialog: StatusDialog?): StatusUiState =
        StatusUiState(
            banner = banner(status),
            typeRows = typeRows(status.stats, permissions),
            permissionRows = permissionRows(permissions),
            reindex = ReindexUi(enabled = !status.isRunning),
            dialog = dialog,
        )

    /** First match wins: FULL run, error, any other run, idle. */
    fun banner(status: IndexStatus): BannerUi {
        val error = status.error
        return when {
            status.isIndexing ->
                BannerUi.Indexing(status.done, status.total, if (status.total > 0) status.percent else null)

            error != null -> BannerUi.Error(error.kind, error.sources)

            status.isRunning -> BannerUi.Updating

            else -> BannerUi.UpToDate(status.lastRun)
        }
    }

    fun typeRows(stats: IndexStats?, permissions: Map<Group, GroupState>): List<TypeRowUi> =
        TYPE_ORDER.map { type ->
            val group = TypeGating.groupOf(type)
            when {
                !TypeGating.isUsable(permissions[group]) -> TypeRowUi(type, null, SubLine.NoAccess)

                stats == null -> TypeRowUi(type, null, SubLine.None)

                type == ItemType.DOCUMENT ->
                    TypeRowUi(type, stats.countsByType[type] ?: 0, SubLine.DocumentKinds)

                type == ItemType.RECORDING ->
                    TypeRowUi(type, stats.countsByType[type] ?: 0, SubLine.RecordingsLinked(stats.recordingsLinked))

                else -> TypeRowUi(type, stats.countsByType[type] ?: 0, SubLine.None)
            }
        }

    fun permissionRows(permissions: Map<Group, GroupState>): List<PermissionRowUi> =
        Group.entries.map { group ->
            val state = permissions[group] ?: GroupState.DENIED
            val action = when (state) {
                GroupState.GRANTED -> PermissionAction.NONE
                GroupState.PARTIAL -> PermissionAction.ALLOW_ALL
                else -> PermissionAction.ALLOW
            }
            PermissionRowUi(group, state, action)
        }

    /** Index of the "Permissions" section header in the status LazyColumn: banner (0), "Indexed items" header (1), 7 type rows. */
    fun permissionsHeaderIndex(typeRowCount: Int = 7): Int = 2 + typeRowCount
}
