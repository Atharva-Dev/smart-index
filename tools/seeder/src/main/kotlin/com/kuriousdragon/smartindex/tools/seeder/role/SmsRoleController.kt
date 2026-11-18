package com.kuriousdragon.smartindex.tools.seeder.role

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony

/** Acquire / remember-previous / restore hints for the default-SMS role (LLD §3.4). */
class SmsRoleController(private val context: Context) {
    private val roleManager = context.getSystemService(RoleManager::class.java)
    private val prefs = SeederPrefs(context)

    val isHeld: Boolean get() = roleManager.isRoleHeld(RoleManager.ROLE_SMS)

    val previousPackage: String? get() = prefs.previousSmsPackage

    /** Saves the current default SMS app once (never ours, never overwriting); call before requesting the role. */
    fun rememberPrevious() {
        val toStore = SmsRoleDecision.previousToStore(
            stored = prefs.previousSmsPackage,
            currentDefault = Telephony.Sms.getDefaultSmsPackage(context),
            ownPackage = context.packageName,
        )
        if (toStore != null) prefs.previousSmsPackage = toStore
    }

    /** The system "Set as default SMS app" request; null when the role is unavailable. */
    fun requestIntent(): Intent? =
        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            null
        }

    fun restoreCommand(): String? = previousPackage?.let { SmsRoleDecision.restoreCommand(it) }

    fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
