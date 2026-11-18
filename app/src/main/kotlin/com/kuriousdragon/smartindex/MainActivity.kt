package com.kuriousdragon.smartindex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.navigation.SmartIndexNavHost
import com.kuriousdragon.smartindex.start.StartViewModel
import dagger.hilt.android.AndroidEntryPoint

/** The single activity (ADR-001): hosts the navigation graph once the start destination is known. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartIndexTheme {
                val start by startViewModel.startDestination.collectAsStateWithLifecycle()
                val destination = start
                if (destination == null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {}
                } else {
                    val navController = rememberNavController()
                    SmartIndexNavHost(
                        startDestination = destination,
                        navController = navController,
                        onOnboardingFinished = { startViewModel.onOnboardingFinished(navController) },
                    )
                }
            }
        }
    }
}
