package com.kuriousdragon.smartindex.source.telephony.testing

import android.content.Intent
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [PermissionService] whose group states are set directly by the test. */
class FakePermissionService(initial: Map<Group, GroupState> = emptyMap()) : PermissionService {
    private val flow = MutableStateFlow(initial)

    override val state: StateFlow<Map<Group, GroupState>> get() = flow

    fun set(group: Group, groupState: GroupState) {
        flow.value = flow.value + (group to groupState)
    }

    override fun request(group: Group): PermissionRequest = PermissionRequest.AlreadyGranted(group)

    override fun settingsIntent(group: Group): Intent = Intent()

    override fun onRuntimeResult(result: RuntimeResult) = Unit

    override fun refresh() = Unit
}
