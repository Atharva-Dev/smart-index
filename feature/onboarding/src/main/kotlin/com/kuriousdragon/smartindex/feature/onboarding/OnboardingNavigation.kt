package com.kuriousdragon.smartindex.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

const val ONBOARDING_GRAPH_ROUTE = "onboarding"
const val ONBOARDING_WELCOME_ROUTE = "onboarding/welcome"

/**
 * Onboarding graph; [onFinished] is called when the user taps "Start indexing". [screen] is the seam that lets the
 * `:app` navigation test run without Hilt (LLD C8).
 */
fun NavGraphBuilder.onboardingGraph(
    onFinished: () -> Unit,
    screen: @Composable (onFinished: () -> Unit) -> Unit = { OnboardingRoute(onFinished = it) },
) {
    navigation(startDestination = ONBOARDING_WELCOME_ROUTE, route = ONBOARDING_GRAPH_ROUTE) {
        composable(ONBOARDING_WELCOME_ROUTE) { screen(onFinished) }
    }
}

@Composable
fun OnboardingPlaceholder(onFinished: () -> Unit) {
    Column(modifier = Modifier.safeDrawingPadding()) {
        Text("Onboarding")
        Button(onClick = onFinished, modifier = Modifier.testTag("onboarding_start")) { Text("Start indexing") }
    }
}
