package com.kuriousdragon.smartindex.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val HOME_ROUTE = "home"

/** Home destination. [onOpenStatus] gets `null` or the status section to scroll to. */
fun NavGraphBuilder.homeScreen(onOpenStatus: (section: String?) -> Unit) {
    composable(HOME_ROUTE) { HomeRoute(onOpenStatus) }
}
