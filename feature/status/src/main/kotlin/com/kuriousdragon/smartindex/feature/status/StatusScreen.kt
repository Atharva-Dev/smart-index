package com.kuriousdragon.smartindex.feature.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.permissions.ui.AllowInSettingsDialog
import com.kuriousdragon.smartindex.feature.status.format.StatusFormatter
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusEnv
import com.kuriousdragon.smartindex.feature.status.model.StatusMapper
import com.kuriousdragon.smartindex.feature.status.model.StatusUiState
import com.kuriousdragon.smartindex.feature.status.ui.ItemsHeader
import com.kuriousdragon.smartindex.feature.status.ui.PermissionRow
import com.kuriousdragon.smartindex.feature.status.ui.PermissionsHeader
import com.kuriousdragon.smartindex.feature.status.ui.PrivacyNote
import com.kuriousdragon.smartindex.feature.status.ui.ReindexDialog
import com.kuriousdragon.smartindex.feature.status.ui.ReindexSection
import com.kuriousdragon.smartindex.feature.status.ui.StatusBanner
import com.kuriousdragon.smartindex.feature.status.ui.StatusTestTags
import com.kuriousdragon.smartindex.feature.status.ui.TypeRow

/** Item keys of the status list (LLD §3.5); the order of [StatusMapper.permissionsHeaderIndex] depends on them. */
internal const val KEY_BANNER = "banner"
internal const val KEY_HDR_ITEMS = "hdr_items"
internal const val KEY_HDR_PERMISSIONS = "hdr_permissions"
internal const val KEY_PRIVACY = "privacy"
internal const val KEY_REINDEX = "reindex"

/** Stateless Index status screen (LLD §3.5, §5.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    state: StatusUiState,
    scrollToPermissions: Boolean,
    env: StatusEnv,
    nowMs: Long,
    onBack: () -> Unit,
    onAllow: (Group) -> Unit,
    onReindexClick: () -> Unit,
    onReindexConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onSettingsDialogOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember(env, nowMs) { StatusFormatter(env, nowMs) }
    val listState = rememberLazyListState()
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollToPermissions) {
        if (scrollToPermissions && !scrolled) {
            scrolled = true
            listState.scrollToItem(StatusMapper.permissionsHeaderIndex(state.typeRows.size))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.status_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Spacing.MinTouch).testTag(StatusTestTags.BACK),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_back),
                        )
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .testTag(StatusTestTags.LIST)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                item(key = KEY_BANNER) {
                    StatusBanner(state.banner, formatter, Modifier.padding(horizontal = Spacing.L))
                }
                item(key = KEY_HDR_ITEMS) { ItemsHeader() }
                items(state.typeRows, key = { "type_${it.type.name}" }) { row -> TypeRow(row, formatter) }
                item(key = KEY_HDR_PERMISSIONS) { PermissionsHeader() }
                items(state.permissionRows, key = { "perm_${it.group.name}" }) { row ->
                    PermissionRow(row, onAllow = { onAllow(row.group) })
                }
                item(key = KEY_PRIVACY) { PrivacyNote() }
                item(key = KEY_REINDEX) { ReindexSection(state.reindex, onClick = onReindexClick) }
            }
        }
    }

    when (val dialog = state.dialog) {
        StatusDialog.ConfirmReindex -> ReindexDialog(onConfirm = onReindexConfirm, onDismiss = onDialogDismiss)

        is StatusDialog.AllowInSettings -> AllowInSettingsDialog(
            group = dialog.group,
            onDismiss = onDialogDismiss,
            onOpenSettings = onSettingsDialogOpenSettings,
        )

        null -> Unit
    }
}
