package com.kuriousdragon.smartindex.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifyNoNetworkPermissionsTaskTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun taskFor(fixture: String): VerifyNoNetworkPermissionsTask {
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder("p")).build()
        val manifest = File(tmp.root, "AndroidManifest.xml")
        manifest.writeText(checkNotNull(javaClass.getResource("/manifests/$fixture")).readText())
        return project.tasks.register("verifyFixture", VerifyNoNetworkPermissionsTask::class.java) {
            mergedManifest.set(manifest)
            variantName.set("release")
            report.set(File(tmp.root, "report.txt"))
        }.get()
    }

    @Test
    fun `task fails when the merged manifest requests INTERNET`() {
        val task = taskFor("internet.xml")
        val e = assertThrows(GradleException::class.java) { task.verify() }
        assertThat(e).hasMessageThat().contains("android.permission.INTERNET")
        assertThat(e).hasMessageThat().contains("variant 'release'")
        assertThat(File(tmp.root, "report.txt").readText()).contains("android.permission.INTERNET")
    }

    @Test
    fun `task passes and writes an OK report for a clean manifest`() {
        taskFor("clean.xml").verify()
        assertThat(File(tmp.root, "report.txt").readText()).startsWith("OK")
    }
}
