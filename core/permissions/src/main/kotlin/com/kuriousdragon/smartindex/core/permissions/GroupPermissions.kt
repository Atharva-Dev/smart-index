package com.kuriousdragon.smartindex.core.permissions

import android.Manifest
import android.annotation.SuppressLint
import com.kuriousdragon.smartindex.core.model.Group

object GroupPermissions {
    /** READ_MEDIA_VISUAL_USER_SELECTED (API 34); only ever used when the SDK is >= 34. */
    @SuppressLint("InlinedApi")
    internal const val VISUAL_USER_SELECTED = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

    /** Runtime permissions asked for [group]; empty for FILES (settings page). */
    fun runtime(group: Group, sdkInt: Int): List<String> = when (group) {
        Group.PHOTOS_VIDEOS -> buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (sdkInt >= 34) add(VISUAL_USER_SELECTED)
        }

        Group.FILES -> emptyList()

        Group.MESSAGES -> listOf(Manifest.permission.READ_SMS)

        Group.CALL_LOG -> listOf(Manifest.permission.READ_CALL_LOG)

        Group.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
    }
}
