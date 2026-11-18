package com.kuriousdragon.smartindex.core.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Seam over the platform so the service can be tested without a real permission system. */
interface SystemPermissions {
    val sdkInt: Int
    val packageName: String

    /** `ContextCompat.checkSelfPermission == PERMISSION_GRANTED`. */
    fun isGranted(permission: String): Boolean

    /** `Environment.isExternalStorageManager()`. */
    fun isExternalStorageManager(): Boolean

    /** `packageManager.resolveActivity(intent, 0) != null`. */
    fun canResolve(intent: Intent): Boolean
}

class AndroidSystemPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemPermissions {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
    override val packageName: String get() = context.packageName

    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun isExternalStorageManager(): Boolean = Environment.isExternalStorageManager()

    override fun canResolve(intent: Intent): Boolean = context.packageManager.resolveActivity(intent, 0) != null
}
