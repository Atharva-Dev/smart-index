package com.kuriousdragon.smartindex.feature.onboarding.testing

import android.content.Intent
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [PermissionService] whose group states are set by the test; records `request` and `refresh` calls. */
class FakePermissionService(
    initial: Map<Group, GroupState> = Group.entries.associateWith { GroupState.DENIED },
) : PermissionService {
    private val flow = MutableStateFlow(initial)

    val requested = mutableListOf<Group>()
    var refreshCount = 0
        private set

    override val state: StateFlow<Map<Group, GroupState>> get() = flow

    fun setState(
        group: Group,
        groupState: GroupState,
    ) {
        flow.value = flow.value + (group to groupState)
    }

    override fun request(group: Group): PermissionRequest {
        requested += group
        return when {
            flow.value[group] == GroupState.GRANTED -> PermissionRequest.AlreadyGranted(group)

            group == Group.FILES || flow.value[group] == GroupState.DENIED_PERMANENTLY ->
                PermissionRequest.Settings(group, Intent())

            else -> PermissionRequest.Runtime(group, emptyList())
        }
    }

    override fun settingsIntent(group: Group): Intent = Intent()

    override fun onRuntimeResult(result: RuntimeResult) = Unit

    override fun refresh() {
        refreshCount++
    }
}
