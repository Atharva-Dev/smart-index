package com.kuriousdragon.smartindex.core.permissions.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import com.kuriousdragon.smartindex.core.model.SiLog

/**
 * Returns a launcher: call it with a [Group] to ask for it. [onResult] receives the group's state afterwards
 * (LLD — App shell §3.3, §5.2). The pending group and the "rationale before" set survive recreation.
 */
@Composable
fun rememberGroupRequester(service: PermissionService, onResult: (Group, GroupState) -> Unit): (Group) -> Unit =
    rememberGroupRequester(service, onResult, ActivityCompat::shouldShowRequestPermissionRationale)

/** Same as the public overload with the rationale probe injectable (tests; Robolectric always answers false). */
@Composable
internal fun rememberGroupRequester(
    service: PermissionService,
    onResult: (Group, GroupState) -> Unit,
    shouldShowRationale: (Activity, String) -> Boolean,
): (Group) -> Unit {
    val activity = LocalActivity.current
    val currentService by rememberUpdatedState(service)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentRationale by rememberUpdatedState(shouldShowRationale)
    var pendingRuntime by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSettings by rememberSaveable { mutableStateOf<String?>(null) }
    // Permission names joined by spaces (they never contain one): a String saves into the Bundle as is.
    var rationaleBefore by rememberSaveable { mutableStateOf("") }

    fun report(group: Group) {
        currentOnResult(group, currentService.state.value.getValue(group))
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val group = pendingRuntime?.let(Group::valueOf) ?: return@rememberLauncherForActivityResult
        pendingRuntime = null
        val after = grants.keys.filter { activity != null && currentRationale(activity, it) }.toSet()
        currentService.onRuntimeResult(
            RuntimeResult(
                group,
                grants,
                rationaleBefore.split(' ').filter {
                    it.isNotEmpty()
                }.toSet(),
                after,
            ),
        )
        rationaleBefore = ""
        report(group)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val group = pendingSettings?.let(Group::valueOf) ?: return@rememberLauncherForActivityResult
        pendingSettings = null
        currentService.refresh()
        report(group)
    }

    return remember(runtimeLauncher, settingsLauncher, activity) {
        { group: Group ->
            when (val request = currentService.request(group)) {
                is PermissionRequest.AlreadyGranted -> currentOnResult(group, GroupState.GRANTED)

                is PermissionRequest.Runtime -> {
                    rationaleBefore = request.permissions
                        .filter { activity != null && currentRationale(activity, it) }
                        .joinToString(" ")
                    pendingRuntime = group.name
                    runtimeLauncher.launch(request.permissions.toTypedArray())
                }

                is PermissionRequest.Settings -> {
                    pendingSettings = group.name
                    try {
                        settingsLauncher.launch(request.intent)
                    } catch (_: ActivityNotFoundException) {
                        SiLog.w("Perm", "no settings activity group=$group")
                        pendingSettings = null
                        report(group)
                    }
                }
            }
        }
    }
}
