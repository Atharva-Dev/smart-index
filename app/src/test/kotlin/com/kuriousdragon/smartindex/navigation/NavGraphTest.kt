package com.kuriousdragon.smartindex.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.feature.onboarding.OnboardingPlaceholder
import com.kuriousdragon.smartindex.feature.status.StatusPlaceholder
import com.kuriousdragon.smartindex.feature.status.navigateToStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NavGraphTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun setContent(start: String) {
        rule.setContent {
            nav = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            SmartIndexNavHost(
                startDestination = start,
                navController = nav,
                onOnboardingFinished = {
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                },
                statusContent = { StatusPlaceholder(it) },
                onboardingScreen = { OnboardingPlaceholder(it) },
                homeContent = { onOpenStatus -> composable("home") { HomeTestPlaceholder(onOpenStatus) } },
            )
        }
    }

    private fun backStackRoutes() = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun `finishing onboarding lands on home and removes onboarding from the back stack`() {
        setContent("onboarding")
        rule.onNodeWithText("Onboarding").assertExists()
        rule.onNodeWithTag("onboarding_start").performClick()
        rule.waitForIdle()
        assertThat(nav.currentDestination?.route).isEqualTo("home")
        assertThat(backStackRoutes().filter { it.startsWith("onboarding") }).isEmpty()
    }

    @Test
    fun `home permissions button opens status with the permissions section`() {
        setContent("home")
        rule.onNodeWithTag("home_permissions").performClick()
        rule.waitForIdle()
        assertThat(nav.currentDestination?.route).isEqualTo("status?section={section}")
        assertThat(nav.currentBackStackEntry?.arguments?.getString("section")).isEqualTo("permissions")
        rule.onNodeWithText("Index status").assertExists()
    }

    @Test
    fun `home status button opens status without a section`() {
        setContent("home")
        rule.onNodeWithTag("home_status").performClick()
        rule.waitForIdle()
        assertThat(nav.currentDestination?.route).isEqualTo("status?section={section}")
        assertThat(nav.currentBackStackEntry?.arguments?.getString("section")).isNull()
    }

    @Test
    fun `back from status returns to home`() {
        setContent("home")
        rule.runOnUiThread { nav.navigateToStatus("permissions") }
        rule.waitForIdle()
        rule.onNodeWithTag("status_back").performClick()
        rule.waitForIdle()
        assertThat(nav.currentDestination?.route).isEqualTo("home")
    }

    @Test
    fun `starting at home leaves only home on the back stack`() {
        setContent("home")
        rule.waitForIdle()
        assertThat(backStackRoutes().filter { it != "home" }).isEmpty()
        assertThat(backStackRoutes().none { it.startsWith("onboarding") }).isTrue()
    }
}

/** Stands in for the real Home destination so this test needs no Hilt. */
@Composable
private fun HomeTestPlaceholder(onOpenStatus: (section: String?) -> Unit) {
    Column {
        Text("Home")
        Button(onClick = { onOpenStatus(null) }, modifier = Modifier.testTag("home_status")) { Text("Index status") }
        Button(onClick = { onOpenStatus("permissions") }, modifier = Modifier.testTag("home_permissions")) {
            Text("Permissions")
        }
    }
}
