package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.state.IndexingBanner
import com.kuriousdragon.smartindex.feature.home.state.PermissionBanner
import com.kuriousdragon.smartindex.feature.home.state.body
import com.kuriousdragon.smartindex.feature.home.state.title
import java.text.NumberFormat

private val BannerIconSize = 20.dp
private val BannerProgressHeight = 4.dp

/** The progress line of a [BannerCard]: determinate for a fraction, indeterminate for null. */
@Composable
private fun BannerProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val bar = modifier.fillMaxWidth().height(BannerProgressHeight)
    if (fraction == null) {
        LinearProgressIndicator(modifier = bar)
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = bar)
    }
}

/**
 * Inline banner (Design Spec §6.8): radius 12, `surfaceContainer` or, when [warn], the warning container; a 20 dp
 * icon, [title] (`titleSmall`), [body] (`bodySmall`), optional right-aligned text [content] and an optional 4 dp
 * progress line ([showProgress]: [progress] null = indeterminate). The card is a polite live region; [announcement]
 * replaces the spoken text when given (LLD §4.10).
 */
@Composable
fun BannerCard(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
    showProgress: Boolean = false,
    progress: Float? = null,
    announcement: String? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val container = if (warn) SmartIndexTheme.colors.warnContainer else MaterialTheme.colorScheme.surfaceContainer
    val onContainer = if (warn) SmartIndexTheme.colors.onWarnContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(Radius.Tile))
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                if (announcement != null) contentDescription = announcement
            }
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(BannerIconSize))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = onContainer)
            }
        }
        if (showProgress) BannerProgress(progress)
        if (content != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** The announced percent: `done / total` rounded down to a multiple of 10 (0..100), so progress is spoken rarely. */
internal fun announcedPercent(done: Int, total: Int): Int {
    if (total <= 0) return 0
    val percent = (done.coerceIn(0, total).toLong() * 100 / total).toInt()
    return percent / 10 * 10
}

/**
 * "Indexing your phone… {done} of {total} items" with grouped numbers, or the unknown-total title when
 * `total == 0` (indeterminate progress then). Tag `sidx_indexing_banner`.
 */
@Composable
fun IndexingBanner(ui: IndexingBanner, modifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    val known = ui.total > 0
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val title = if (known) {
        stringResource(R.string.banner_indexing_title, format.format(ui.done), format.format(ui.total))
    } else {
        stringResource(R.string.banner_indexing_title_unknown)
    }
    val announcement = if (known) {
        "$title ${announcedPercent(ui.done, ui.total)} percent"
    } else {
        title
    }
    BannerCard(
        icon = Icons.Filled.Info,
        title = title,
        body = stringResource(R.string.banner_indexing_body),
        modifier = modifier.testTag(HomeTestTags.INDEXING_BANNER),
        showProgress = true,
        progress = if (known) ui.done.coerceIn(0, ui.total).toFloat() / ui.total else null,
        announcement = announcement,
    )
}

/** "{Types} are hidden" warning with Not now and Allow access. Tag `sidx_permission_banner`. */
@Composable
fun PermissionBanner(
    ui: PermissionBanner,
    onNotNow: () -> Unit,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    BannerCard(
        icon = Icons.Filled.Warning,
        title = ui.title(res),
        body = ui.body(res),
        modifier = modifier.testTag(HomeTestTags.PERMISSION_BANNER),
        warn = true,
        content = {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.action_not_now)) }
            TextButton(onClick = onAllow) { Text(stringResource(R.string.action_allow_access)) }
        },
    )
}
