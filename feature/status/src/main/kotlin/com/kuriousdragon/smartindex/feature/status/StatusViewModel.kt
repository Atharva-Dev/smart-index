package com.kuriousdragon.smartindex.feature.status

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusEffect
import com.kuriousdragon.smartindex.feature.status.model.StatusMapper
import com.kuriousdragon.smartindex.feature.status.model.StatusUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

private const val TAG = "Status"
private const val KEY_DIALOG = "dialog"
private const val KEY_SECTION = "section"
private const val DIALOG_REINDEX = "REINDEX"
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * State holder of the Index status screen (LLD §3.4, §5.4–§5.6).
 *
 * [permissionService] is public only so the route can build `rememberGroupRequester`; nothing else reads it
 * (LLD §6.3).
 */
@HiltViewModel
class StatusViewModel @Inject constructor(
    private val statusSource: IndexStatusSource,
    private val indexControl: IndexControl,
    val permissionService: PermissionService,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val dialog = MutableStateFlow(restoreDialog())

    /** True when the screen was opened for the Permissions section; read once. */
    val scrollToPermissions: Boolean = savedState.get<String?>(KEY_SECTION) == STATUS_SECTION_PERMISSIONS

    val uiState: StateFlow<StatusUiState> = combine(
        statusSource.status,
        permissionService.state,
        dialog,
    ) { status, permissions, dialogNow ->
        StatusMapper.map(status, permissions, dialogNow)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StatusMapper.map(statusSource.status.value, permissionService.state.value, dialog.value),
    )

    private val effectChannel = Channel<StatusEffect>(Channel.BUFFERED)

    /** One-shot effects (launch a permission request). */
    val effects: Flow<StatusEffect> = effectChannel.receiveAsFlow()

    fun onAllowClick(group: Group) {
        val permanentlyDenied = permissionService.state.value[group] == GroupState.DENIED_PERMANENTLY
        if (permanentlyDenied && group != Group.FILES) {
            SiLog.i(TAG, "allow in settings dialog group=${group.name}")
            setDialog(StatusDialog.AllowInSettings(group))
        } else {
            SiLog.i(TAG, "request group=${group.name}")
            effectChannel.trySend(StatusEffect.RequestGroup(group))
        }
    }

    fun onPermissionResult(group: Group, state: GroupState) {
        SiLog.i(TAG, "permission result group=${group.name} state=${state.name}")
        indexControl.onPermissionsChanged()
    }

    fun onSettingsDialogCancel() {
        setDialog(null)
    }

    fun onSettingsDialogOpenSettings() {
        val current = dialog.value
        setDialog(null)
        if (current is StatusDialog.AllowInSettings) {
            SiLog.i(TAG, "open settings group=${current.group.name}")
            effectChannel.trySend(StatusEffect.RequestGroup(current.group))
        }
    }

    fun onReindexClick() {
        if (!statusSource.status.value.isRunning) setDialog(StatusDialog.ConfirmReindex)
    }

    fun onReindexConfirm() {
        setDialog(null)
        if (!statusSource.status.value.isRunning) {
            SiLog.i(TAG, "reindex confirmed")
            indexControl.reindexAll()
        }
    }

    fun onReindexCancel() {
        setDialog(null)
    }

    private fun setDialog(value: StatusDialog?) {
        dialog.value = value
        savedState[KEY_DIALOG] = value?.let(::encode)
    }

    private fun encode(value: StatusDialog): String = when (value) {
        StatusDialog.ConfirmReindex -> DIALOG_REINDEX
        is StatusDialog.AllowInSettings -> value.group.name
    }

    private fun restoreDialog(): StatusDialog? {
        val raw = savedState.get<String?>(KEY_DIALOG) ?: return null
        if (raw == DIALOG_REINDEX) return StatusDialog.ConfirmReindex
        val group = Group.entries.firstOrNull { it.name == raw }
        return if (group == null || group == Group.FILES) null else StatusDialog.AllowInSettings(group)
    }
}
