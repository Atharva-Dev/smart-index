package com.kuriousdragon.smartindex.tools.seeder.role

import android.content.Context
import android.content.SharedPreferences

/** Seeder preferences: `previousSmsPackage` and `keepSmsIds` (real incoming SMS a wipe must never delete). */
class SeederPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var previousSmsPackage: String?
        get() = prefs.getString(KEY_PREVIOUS, null)
        set(value) {
            prefs.edit().putString(KEY_PREVIOUS, value).apply()
        }

    val keepSmsIds: Set<Long>
        get() = prefs.getStringSet(KEY_KEEP, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun addKeepSmsId(id: Long) {
        val next = prefs.getStringSet(KEY_KEEP, emptySet()).orEmpty() + id.toString()
        prefs.edit().putStringSet(KEY_KEEP, next).apply()
    }

    private companion object {
        const val NAME = "seeder"
        const val KEY_PREVIOUS = "previousSmsPackage"
        const val KEY_KEEP = "keepSmsIds"
    }
}
