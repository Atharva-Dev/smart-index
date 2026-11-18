package com.kuriousdragon.smartindex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.kuriousdragon.smartindex.feature.home.homeScreen
import com.kuriousdragon.smartindex.feature.onboarding.OnboardingRoute
import com.kuriousdragon.smartindex.feature.onboarding.onboardingGraph
import com.kuriousdragon.smartindex.feature.status.StatusRoute
import com.kuriousdragon.smartindex.feature.status.navigateToStatus
import com.kuriousdragon.smartindex.feature.status.statusScreen

@Composable
fun SmartIndexNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    onOnboardingFinished: () -> Unit,
    statusContent: @Composable (onBack: () -> Unit) -> Unit = { StatusRoute(onBack = it) },
    onboardingScreen: @Composable (onFinished: () -> Unit) -> Unit = { OnboardingRoute(onFinished = it) },
    homeContent: NavGraphBuilder.(onOpenStatus: (String?) -> Unit) -> Unit = { homeScreen(it) },
) {
    NavHost(navController = navController, startDestination = startDestination) {
        onboardingGraph(onOnboardingFinished, onboardingScreen)
        homeContent { navController.navigateToStatus(it) }
        statusScreen(onBack = { navController.popBackStack() }, screen = statusContent)
    }
}
