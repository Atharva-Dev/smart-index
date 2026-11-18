package com.kuriousdragon.smartindex.feature.home.testing

import android.content.Intent
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PermissionService] whose state is a plain [MutableStateFlow]; every group GRANTED by default. */
internal class FakePermissionService(
    initial: Map<Group, GroupState> = Group.entries.associateWith { GroupState.GRANTED },
) : PermissionService {
    val mutable = MutableStateFlow(initial)
    override val state: StateFlow<Map<Group, GroupState>> get() = mutable

    val requested = mutableListOf<Group>()
    val runtimeResults = mutableListOf<RuntimeResult>()
    var refreshCount = 0

    fun set(group: Group, state: GroupState) {
        mutable.value = mutable.value + (group to state)
    }

    override fun request(group: Group): PermissionRequest {
        requested += group
        return if (mutable.value[group] == GroupState.GRANTED) {
            PermissionRequest.AlreadyGranted(group)
        } else {
            PermissionRequest.Runtime(group, emptyList())
        }
    }

    override fun settingsIntent(group: Group): Intent = Intent("fake.SETTINGS.${group.name}")

    override fun onRuntimeResult(result: RuntimeResult) {
        runtimeResults += result
    }

    override fun refresh() {
        refreshCount++
    }
}
