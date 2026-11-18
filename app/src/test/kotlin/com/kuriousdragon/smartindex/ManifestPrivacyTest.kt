package com.kuriousdragon.smartindex

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric smoke test of the merged debug manifest as the package manager sees it (FR-016, NFR-006,
 * QA Scope §3 P1 Privacy). The build-time gates (verifyNoNetworkPermissions, verifyManifestHardening)
 * cover every variant; this test pins the runtime view.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class ManifestPrivacyTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES,
    )

    @Test
    fun `no network permission is requested`() {
        val requested = info.requestedPermissions.orEmpty().toList()
        assertThat(requested).doesNotContain("android.permission.INTERNET")
        assertThat(requested).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
    }

    @Test
    fun `the HLD 7 permission set is declared`() {
        assertThat(info.requestedPermissions.orEmpty().toList()).containsAtLeast(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_CONTACTS",
        )
    }

    @Test
    fun `backup is disabled`() {
        assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test
    fun `only MainActivity is declared and it is exported as the launcher`() {
        val activities = info.activities.orEmpty().filter { it.packageName == context.packageName }
        // ui-test-manifest (debugImplementation, Compose tests) adds an exported ComponentActivity to debug only;
        // verifyManifestHardening tolerates it in debuggable variants.
        val exported = activities.filter { it.exported }.map { it.name }
            .filter { it != "androidx.activity.ComponentActivity" }
        assertThat(exported).containsExactly(MainActivity::class.java.name)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertThat(launch?.component?.className).isEqualTo(MainActivity::class.java.name)
    }
}
