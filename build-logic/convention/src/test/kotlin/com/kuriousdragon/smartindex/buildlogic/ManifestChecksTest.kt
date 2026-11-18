package com.kuriousdragon.smartindex.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManifestChecksTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/manifests/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `clean manifest has no network permission`() {
        assertThat(ManifestChecks.networkPermissionViolations(fixture("clean.xml"))).isEmpty()
    }

    @Test
    fun `INTERNET and sdk-23 ACCESS_NETWORK_STATE are reported`() {
        assertThat(ManifestChecks.networkPermissionViolations(fixture("internet.xml"))).containsExactly(
            "network permission requested: android.permission.INTERNET",
            "network permission requested: android.permission.ACCESS_NETWORK_STATE",
        )
    }

    @Test
    fun `clean manifest passes hardening in release and debug`() {
        assertThat(ManifestChecks.hardeningViolations(fixture("clean.xml"), debuggable = false)).isEmpty()
        assertThat(ManifestChecks.hardeningViolations(fixture("clean.xml"), debuggable = true)).isEmpty()
    }

    @Test
    fun `exported non-launcher components and allowBackup are reported`() {
        assertThat(ManifestChecks.hardeningViolations(fixture("exported.xml"), debuggable = false)).containsExactly(
            "application android:allowBackup must be \"false\"",
            "application android:dataExtractionRules must be set",
            "exported activity is not allowed: com.example.DeepLinkActivity",
            "exported activity is not allowed: androidx.compose.ui.tooling.PreviewActivity",
            "exported receiver is not allowed: com.example.Receiver",
        )
    }

    @Test
    fun `compose preview activity is tolerated only in debuggable variants`() {
        assertThat(ManifestChecks.hardeningViolations(fixture("exported.xml"), debuggable = true))
            .doesNotContain("exported activity is not allowed: androidx.compose.ui.tooling.PreviewActivity")
    }

    @Test
    fun `seeder SMS role components are accepted`() {
        assertThat(ManifestChecks.hardeningViolations(fixture("seeder-sms-role.xml"), debuggable = true)).isEmpty()
    }
}
