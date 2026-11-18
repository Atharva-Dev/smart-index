package com.kuriousdragon.smartindex.feature.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val STATUS_SECTION_PERMISSIONS = "permissions"
const val STATUS_ROUTE = "status?section={section}"

private const val SECTION_ARG = "section"

/** Opens Index status, optionally scrolled to [section] (e.g. [STATUS_SECTION_PERMISSIONS]). */
fun NavController.navigateToStatus(section: String? = null) {
    val route = if (section == null) "status" else "status?section=$section"
    navigate(route) { launchSingleTop = true }
}

/**
 * Index status destination; [onBack] pops to Home. [screen] is the content seam: production uses [StatusRoute],
 * the app navigation test swaps in [StatusPlaceholder] to stay Hilt-free.
 */
fun NavGraphBuilder.statusScreen(
    onBack: () -> Unit,
    screen: @Composable (onBack: () -> Unit) -> Unit = { StatusRoute(onBack = it) },
) {
    composable(
        route = STATUS_ROUTE,
        arguments = listOf(
            navArgument(SECTION_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        screen(onBack)
    }
}

/** Minimal stand-in for the status screen in Hilt-free tests. */
@Composable
fun StatusPlaceholder(onBack: () -> Unit) {
    Column(modifier = Modifier.safeDrawingPadding()) {
        Text("Index status")
        Button(onClick = onBack, modifier = Modifier.testTag("status_back")) { Text("Back") }
    }
}
