package com.kuriousdragon.smartindex.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kuriousdragon.smartindex.core.permissions.ui.rememberGroupRequester
import com.kuriousdragon.smartindex.feature.home.state.HomeEffect
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.state.Overlay
import com.kuriousdragon.smartindex.feature.home.state.UiText
import com.kuriousdragon.smartindex.feature.home.ui.FirstPageTraceEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_MESSAGE_MS = 10_000L

/** Starts an activity; throws what `Context.startActivity` throws. A seam for tests. */
internal fun interface IntentLauncher {
    fun launch(intent: Intent)
}

/** Stateful Home / Search destination: ViewModel, effects, lifecycle and the back handler. */
@Composable
fun HomeRoute(onOpenStatus: (section: String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    HomeRoute(
        onOpenStatus = onOpenStatus,
        viewModel = hiltViewModel<HomeViewModel>(),
        launcher = IntentLauncher { context.startActivity(it) },
        isChangingConfigurations = { activity?.isChangingConfigurations == true },
        modifier = modifier,
    )
}

@Composable
internal fun HomeRoute(
    onOpenStatus: (section: String?) -> Unit,
    viewModel: HomeViewModel,
    launcher: IntentLauncher,
    isChangingConfigurations: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.rowPlayback.collectAsStateWithLifecycle()
    val items = viewModel.pages.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val requestGroup = rememberGroupRequester(viewModel.permissionService, viewModel::onPermissionResult)
    val currentLauncher by rememberUpdatedState(launcher)
    val currentOnOpenStatus by rememberUpdatedState(onOpenStatus)
    val currentRequestGroup by rememberUpdatedState(requestGroup)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Launch -> if (!tryLaunch(effect.attempts, currentLauncher)) {
                    showMessage(snackbar, effect.noHandler.resolve(context), false, touchExploring(context))
                }

                is HomeEffect.ShowMessage ->
                    showMessage(snackbar, effect.text.resolve(context), effect.long, touchExploring(context))

                is HomeEffect.RequestPermission -> currentRequestGroup(effect.group)

                is HomeEffect.OpenStatus -> currentOnOpenStatus(effect.section)
            }
        }
    }

    BackHandler(enabled = state.mode == HomeMode.SEARCH || state.overlay != Overlay.None) { viewModel.onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!isChangingConfigurations()) viewModel.pausePlayback()
    }
    ReportDrawnWhen { state.firstPageShown }
    FirstPageTraceEffect(
        commit = state.commit,
        refresh = { items.loadState.refresh },
        onShown = viewModel::onFirstPageShown,
    )

    HomeScreen(
        state = state,
        items = items,
        playback = playback,
        snackbarHost = snackbar,
        player = viewModel.player,
        callbacks = remember(viewModel) { HomeCallbacks.forViewModel(viewModel) },
        modifier = modifier,
    )
}

/** Tries each intent in order; ActivityNotFoundException and SecurityException move on. True when one started. */
internal fun tryLaunch(attempts: List<Intent>, launcher: IntentLauncher): Boolean {
    for (intent in attempts) {
        try {
            launcher.launch(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            continue
        } catch (_: SecurityException) {
            continue
        }
    }
    return false
}

private fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.toTypedArray())
    is UiText.Plain -> text
}

private fun touchExploring(context: Context): Boolean =
    context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

/**
 * Queues a snackbar without blocking the effect collector. Short = 4 s, long = 10 s with a dismiss action,
 * Indefinite (with a dismiss action) while touch exploration is on.
 */
private fun CoroutineScope.showMessage(host: SnackbarHostState, text: String, long: Boolean, touchExploring: Boolean) {
    launch {
        when {
            touchExploring -> host.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Indefinite)

            long -> {
                val shown: Job = launch {
                    host.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Indefinite)
                }
                delay(LONG_MESSAGE_MS)
                shown.cancel()
            }

            else -> host.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }
}
