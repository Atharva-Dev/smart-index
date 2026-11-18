package com.kuriousdragon.smartindex.core.permissions

import android.content.Intent

class FakeSystemPermissions(
    override var sdkInt: Int = 34,
    override val packageName: String = "com.kuriousdragon.smartindex",
) : SystemPermissions {
    val granted = mutableSetOf<String>()
    var storageManager = false
    var resolvable = true
    var failing = false

    override fun isGranted(permission: String): Boolean {
        if (failing) throw SecurityException("boom")
        return permission in granted
    }

    override fun isExternalStorageManager(): Boolean {
        if (failing) throw SecurityException("boom")
        return storageManager
    }

    override fun canResolve(intent: Intent): Boolean = resolvable
}
