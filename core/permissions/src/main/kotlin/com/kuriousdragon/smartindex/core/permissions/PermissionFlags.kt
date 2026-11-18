package com.kuriousdragon.smartindex.core.permissions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Persisted asked-once / permanently-denied flags per runtime permission (LLD — App shell §3.2, §4.2). */
class PermissionFlags @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deniedOnce(p: String): Boolean = prefs.getBoolean(DENIED_ONCE + p, false)

    fun setDeniedOnce(p: String, value: Boolean) {
        prefs.edit().putBoolean(DENIED_ONCE + p, value).apply()
    }

    fun permanent(p: String): Boolean = prefs.getBoolean(PERMANENT + p, false)

    fun setPermanent(p: String, value: Boolean) {
        prefs.edit().putBoolean(PERMANENT + p, value).apply()
    }

    /** Clears both flags of [p]. */
    fun clear(p: String) {
        prefs.edit().remove(DENIED_ONCE + p).remove(PERMANENT + p).apply()
    }

    private companion object {
        const val PREFS_NAME = "sidx_permissions"
        const val DENIED_ONCE = "denied_once:"
        const val PERMANENT = "permanent:"
    }
}
