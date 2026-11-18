package com.kuriousdragon.smartindex.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleRulesTest {
    @Test
    fun `allowed edges of HLD 8_1 pass`() {
        val edges = mapOf(
            ":app" to listOf(":core:model", ":feature:home", ":sync", ":source:media"),
            ":core:index" to listOf(":core:model"),
            ":source:media" to listOf(":core:model", ":core:index"),
            ":sync" to listOf(":core:model", ":source:telephony"),
            ":feature:home" to listOf(":core:model", ":core:designsystem", ":feature:player"),
            ":tools:seeder" to listOf(":core:model"),
            ":benchmark" to listOf(":app"),
        )
        assertThat(ModuleRules.violations(edges)).isEmpty()
    }

    @Test
    fun `feature to source, source to source and core to core are rejected`() {
        val edges = mapOf(
            ":feature:home" to listOf(":source:media", ":sync"),
            ":source:recordings" to listOf(":source:telephony"),
            ":core:index" to listOf(":core:permissions"),
            ":core:model" to listOf(":core:index"),
            ":tools:seeder" to listOf(":app"),
        )
        assertThat(ModuleRules.violations(edges)).containsExactly(
            ":core:index must not depend on :core:permissions",
            ":core:model must not depend on :core:index",
            ":feature:home must not depend on :source:media",
            ":feature:home must not depend on :sync",
            ":source:recordings must not depend on :source:telephony",
            ":tools:seeder must not depend on :app",
        ).inOrder()
    }

    @Test
    fun `test configurations are recognised, main ones are not`() {
        listOf(
            "testImplementation", "testDebugImplementation", "testDebugRuntimeClasspath", "androidTestImplementation",
            "debugAndroidTestImplementation", "debugUnitTestCompileClasspath", "kspTest", "kspDebugUnitTest",
            "kspDebugAndroidTest", "kaptTest",
        ).forEach { assertThat(ModuleRules.isTestConfiguration(it)).isTrue() }
        listOf(
            "implementation",
            "api",
            "debugImplementation",
            "releaseRuntimeClasspath",
            "debugCompileClasspath",
            "ksp",
            "kspDebug",
            "compileOnly",
        ).forEach { assertThat(ModuleRules.isTestConfiguration(it)).isFalse() }
    }

    @Test
    fun `a main-configuration edge from the seeder to source recordings still fails`() {
        val edges = mapOf(":tools:seeder" to listOf(":core:model", ":source:recordings"))
        assertThat(ModuleRules.violations(edges))
            .containsExactly(":tools:seeder must not depend on :source:recordings")
    }
}
