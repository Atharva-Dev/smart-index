package com.kuriousdragon.smartindex.core.model

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

// Permission contracts (HLD §6.1 `Group`, §6.5 `PermissionService`, §7 type gating, ADR-008).
// Owner: "LLD — App shell, build & privacy hardening" (SIDX-6). Implemented by :core:permissions
// (`DefaultPermissionService`, bound once in its Hilt module).

/** The five onboarding permission groups (Design Spec §6.11 steps 1–5), in step order. */
enum class Group { PHOTOS_VIDEOS, FILES, MESSAGES, CALL_LOG, CONTACTS }

/** The state of one [Group]. */
enum class GroupState {
    /** Every permission of the group is granted (FILES: All files access is on). */
    GRANTED,

    /**
     * Some access only: PHOTOS_VIDEOS with Android 14+ "selected photos" (READ_MEDIA_VISUAL_USER_SELECTED)
     * or only one of READ_MEDIA_IMAGES / READ_MEDIA_VIDEO. Usable: index only what is visible.
     */
    PARTIAL,

    /** Not granted, and asking again shows the system dialog (or opens the All files settings page). */
    DENIED,

    /** Not granted and the system no longer shows the dialog: only the app settings page can grant it. */
    DENIED_PERMANENTLY,
}

/**
 * What the UI must launch to ask for a [Group] (returned by [PermissionService.request]).
 * The UI launches it with `rememberGroupRequester` from `:core:permissions`, never by hand.
 */
sealed interface PermissionRequest {
    /** The group being asked for. */
    val group: Group

    /** The group is already GRANTED: nothing to launch. */
    data class AlreadyGranted(override val group: Group) : PermissionRequest

    /** Ask with `ActivityResultContracts.RequestMultiplePermissions` for exactly [permissions]. */
    data class Runtime(override val group: Group, val permissions: List<String>) : PermissionRequest

    /**
     * Open a settings screen with `startActivity`/`StartActivityForResult`: the All files access page for
     * [Group.FILES], or the app details page when the group is [GroupState.DENIED_PERMANENTLY].
     */
    data class Settings(override val group: Group, val intent: Intent) : PermissionRequest
}

/**
 * Outcome of one [PermissionRequest.Runtime], reported back with [PermissionService.onRuntimeResult].
 *
 * @property grants the map returned by `RequestMultiplePermissions` (permission → granted).
 * @property rationaleBefore permissions for which `shouldShowRequestPermissionRationale` was true just
 *   before launching.
 * @property rationaleAfter the same, read right after the result arrived.
 */
data class RuntimeResult(
    val group: Group,
    val grants: Map<String, Boolean>,
    val rationaleBefore: Set<String>,
    val rationaleAfter: Set<String>,
)

/**
 * Android runtime-permission state per [Group] (HLD §6.5). Main-safe; the implementation is a singleton.
 *
 * Freshness contract (relied on by `:sync`, LLD SIDX-12): [state]`.value` always holds a complete map with
 * every [Group] and is recomputed from the system on creation, on every activity resume, after every
 * request result and on [refresh]. Android kills the process when a runtime permission or All files access
 * is revoked, so a running process never sees a stale GRANTED.
 */
interface PermissionService {
    /** Current state of every [Group]; always contains all five keys. */
    val state: StateFlow<Map<Group, GroupState>>

    /** What to launch to ask for [group] now (see [PermissionRequest]). */
    fun request(group: Group): PermissionRequest

    /**
     * The settings page for [group]: All files access (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) for
     * [Group.FILES], otherwise this app's details page (`ACTION_APPLICATION_DETAILS_SETTINGS`). Used by the
     * "Allow in Settings" dialog's **Open settings** and the tab's **Open app settings** (Design Spec §6.9, §6.11).
     */
    fun settingsIntent(group: Group): Intent

    /** Records the asked-once / permanently-denied flags from [result], then refreshes [state]. */
    fun onRuntimeResult(result: RuntimeResult)

    /** Re-reads every group from the system and updates [state] if anything changed. Cheap; idempotent. */
    fun refresh()
}
