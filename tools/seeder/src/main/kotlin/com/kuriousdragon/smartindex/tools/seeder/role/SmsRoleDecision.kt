package com.kuriousdragon.smartindex.tools.seeder.role

/** Pure decisions of the SMS-role flow (LLD §3.4, R11), unit-tested without Android. */
object SmsRoleDecision {
    /**
     * The package to store as `previousSmsPackage`: the current default SMS app, unless there is none, it is
     * ours, or a previous package is already stored (never overwritten). Null = leave the stored value alone.
     */
    fun previousToStore(stored: String?, currentDefault: String?, ownPackage: String): String? = when {
        !stored.isNullOrEmpty() -> null
        currentDefault.isNullOrEmpty() -> null
        currentDefault == ownPackage -> null
        else -> currentDefault
    }

    /** The adb command that gives the role back to [previousPackage]. */
    fun restoreCommand(previousPackage: String): String =
        "adb shell cmd role add-role-holder --user 0 android.app.role.SMS $previousPackage"

    /** The adb command that gives the role to the seeder without the system dialog. */
    fun acquireCommand(ownPackage: String): String =
        "adb shell cmd role add-role-holder --user 0 android.app.role.SMS $ownPackage"
}
