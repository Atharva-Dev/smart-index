package com.kuriousdragon.smartindex.feature.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuriousdragon.smartindex.core.permissions.ui.rememberGroupRequester
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusEffect
import com.kuriousdragon.smartindex.feature.status.model.rememberStatusEnv

/** Connects [StatusViewModel] to [StatusScreen] and the permission requester (LLD §3.5). */
@Composable
fun StatusRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val env = rememberStatusEnv()
    val nowMs = remember(state.banner) { System.currentTimeMillis() }
    val request = rememberGroupRequester(viewModel.permissionService) { group, groupState ->
        viewModel.onPermissionResult(group, groupState)
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StatusEffect.RequestGroup -> request(effect.group)
            }
        }
    }

    StatusScreen(
        state = state,
        scrollToPermissions = viewModel.scrollToPermissions,
        env = env,
        nowMs = nowMs,
        onBack = onBack,
        onAllow = viewModel::onAllowClick,
        onReindexClick = viewModel::onReindexClick,
        onReindexConfirm = viewModel::onReindexConfirm,
        onDialogDismiss = {
            when (state.dialog) {
                StatusDialog.ConfirmReindex -> viewModel.onReindexCancel()
                is StatusDialog.AllowInSettings -> viewModel.onSettingsDialogCancel()
                null -> Unit
            }
        },
        onSettingsDialogOpenSettings = viewModel::onSettingsDialogOpenSettings,
        modifier = modifier.semantics { testTagsAsResourceId = true },
    )
}
