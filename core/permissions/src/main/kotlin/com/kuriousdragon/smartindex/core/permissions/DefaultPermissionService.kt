package com.kuriousdragon.smartindex.core.permissions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import com.kuriousdragon.smartindex.core.model.SiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The single [PermissionService] implementation (LLD — App shell §3.2). */
@Singleton
class DefaultPermissionService @Inject constructor(
    @ApplicationContext context: Context,
    private val system: SystemPermissions,
    private val flags: PermissionFlags,
) : PermissionService {
    private val _state = MutableStateFlow(computeAll())
    override val state: StateFlow<Map<Group, GroupState>> = _state.asStateFlow()

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = refresh()
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    override fun request(group: Group): PermissionRequest {
        val current = _state.value.getValue(group)
        return when {
            current == GroupState.GRANTED -> PermissionRequest.AlreadyGranted(group)

            group == Group.FILES || current == GroupState.DENIED_PERMANENTLY ->
                PermissionRequest.Settings(group, settingsIntent(group))

            else -> PermissionRequest.Runtime(group, GroupPermissions.runtime(group, system.sdkInt))
        }
    }

    override fun settingsIntent(group: Group): Intent {
        val packageUri = Uri.fromParts("package", system.packageName, null)
        if (group != Group.FILES) return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val appSpecific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        return if (system.canResolve(appSpecific)) {
            appSpecific
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    override fun onRuntimeResult(result: RuntimeResult) {
        val selectedPhotos = result.group == Group.PHOTOS_VIDEOS &&
            result.grants[GroupPermissions.VISUAL_USER_SELECTED] == true
        if (!selectedPhotos) {
            for ((p, granted) in result.grants) {
                if (granted) continue
                when {
                    p in result.rationaleAfter -> {
                        flags.setDeniedOnce(p, true)
                        flags.setPermanent(p, false)
                    }

                    p in result.rationaleBefore || flags.deniedOnce(p) -> flags.setPermanent(p, true)

                    else -> Unit
                }
            }
        }
        refresh()
    }

    override fun refresh() {
        _state.value = computeAll()
    }

    private fun computeAll(): Map<Group, GroupState> = Group.entries.associateWith { group ->
        try {
            computeState(group)
        } catch (e: RuntimeException) {
            SiLog.w("Perm", "check failed group=$group", e)
            GroupState.DENIED
        }
    }

    private fun computeState(group: Group): GroupState = when (group) {
        Group.FILES ->
            if (system.isExternalStorageManager()) GroupState.GRANTED else GroupState.DENIED

        Group.PHOTOS_VIDEOS -> photosState()

        else -> {
            val p = GroupPermissions.runtime(group, system.sdkInt).single()
            when {
                granted(p) -> GroupState.GRANTED
                flags.permanent(p) -> GroupState.DENIED_PERMANENTLY
                else -> GroupState.DENIED
            }
        }
    }

    private fun photosState(): GroupState {
        val images = granted(android.Manifest.permission.READ_MEDIA_IMAGES)
        val video = granted(android.Manifest.permission.READ_MEDIA_VIDEO)
        val selected = system.sdkInt >= 34 &&
            granted(GroupPermissions.VISUAL_USER_SELECTED)
        return when {
            images && video -> GroupState.GRANTED

            images || video || selected -> GroupState.PARTIAL

            flags.permanent(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                flags.permanent(android.Manifest.permission.READ_MEDIA_VIDEO) -> GroupState.DENIED_PERMANENTLY

            else -> GroupState.DENIED
        }
    }

    /** Checks [permission] and clears its flags when granted (a later revoke starts from "never asked"). */
    private fun granted(permission: String): Boolean {
        val g = system.isGranted(permission)
        if (g) flags.clear(permission)
        return g
    }
}
