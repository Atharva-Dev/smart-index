package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnvSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeDeviceEnvSource(initial: DeviceEnv = TestEnv.env) : DeviceEnvSource {
    val mutable = MutableStateFlow(initial)
    override val env: StateFlow<DeviceEnv> get() = mutable
    var refreshes = 0

    override fun refresh() {
        refreshes++
    }
}
