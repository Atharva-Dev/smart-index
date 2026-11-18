package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.feature.status.R
import com.kuriousdragon.smartindex.feature.status.format.LastRun
import com.kuriousdragon.smartindex.feature.status.format.StatusFormatter
import com.kuriousdragon.smartindex.feature.status.format.joinWords
import com.kuriousdragon.smartindex.feature.status.model.BannerUi

/** Which Material container role a banner uses (LLD §3.5 banner table). */
internal enum class BannerRole { SURFACE, PRIMARY, ERROR }

internal fun bannerRole(banner: BannerUi): BannerRole = when (banner) {
    is BannerUi.UpToDate, BannerUi.Updating -> BannerRole.SURFACE
    is BannerUi.Indexing -> BannerRole.PRIMARY
    is BannerUi.Error -> BannerRole.ERROR
}

/** [announcedTitle] differs from [title] only while indexing: it uses the 10 % bucket. */
private class BannerText(val title: String, val body: String, val announcedTitle: String = title)

/** The Index status banner (LLD §3.5): one polite live region; the visible texts are hidden from accessibility. */
@Composable
internal fun StatusBanner(banner: BannerUi, formatter: StatusFormatter, modifier: Modifier = Modifier) {
    val text = bannerText(banner, formatter)
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (bannerRole(banner)) {
        BannerRole.SURFACE -> {
            container = scheme.surfaceContainer
            content = scheme.onSurface
        }

        BannerRole.PRIMARY -> {
            container = scheme.primaryContainer
            content = scheme.onPrimaryContainer
        }

        BannerRole.ERROR -> {
            container = scheme.errorContainer
            content = scheme.onErrorContainer
        }
    }
    val callIcon = SmartIndexTheme.colors.types.getValue(TypeColorKey.CALL).icon
    val icon: ImageVector
    val iconTint: Color
    when {
        banner is BannerUi.UpToDate && banner.lastRunMs != null -> {
            icon = Icons.Outlined.CheckCircle
            iconTint = callIcon
        }

        banner is BannerUi.UpToDate -> {
            icon = Icons.Outlined.Info
            iconTint = scheme.onSurfaceVariant
        }

        banner is BannerUi.Error -> {
            icon = Icons.Outlined.Warning
            iconTint = content
        }

        banner is BannerUi.Indexing -> {
            icon = Icons.Outlined.Refresh
            iconTint = content
        }

        else -> {
            icon = Icons.Outlined.Refresh
            iconTint = scheme.onSurfaceVariant
        }
    }
    val description = stringResource(R.string.a11y_banner, text.announcedTitle, text.body)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StatusTestTags.BANNER)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        shape = RoundedCornerShape(Radius.Tile),
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(Spacing.L).clearAndSetSemantics {}) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Column {
                    Text(text.title, style = MaterialTheme.typography.titleSmall)
                    Text(text.body, style = MaterialTheme.typography.bodySmall)
                }
            }
            when (banner) {
                is BannerUi.Indexing -> {
                    Spacer(Modifier.height(Spacing.M))
                    val percent = banner.percent
                    if (percent == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }

                BannerUi.Updating -> {
                    Spacer(Modifier.height(Spacing.M))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun bannerText(banner: BannerUi, formatter: StatusFormatter): BannerText = when (banner) {
    is BannerUi.UpToDate -> {
        val ms = banner.lastRunMs
        if (ms == null) {
            val body = stringResource(R.string.status_never_body)
            BannerText(stringResource(R.string.status_never_title), body)
        } else {
            val body = when (val run = formatter.lastRun(ms)) {
                is LastRun.Today -> stringResource(R.string.status_ok_body_today, run.time)
                is LastRun.Yesterday -> stringResource(R.string.status_ok_body_yesterday, run.time)
                is LastRun.Other -> stringResource(R.string.status_ok_body_other, run.date, run.time)
            }
            BannerText(stringResource(R.string.status_ok_title), body)
        }
    }

    BannerUi.Updating -> {
        val body = stringResource(R.string.status_updating_body)
        BannerText(stringResource(R.string.status_updating_title), body)
    }

    is BannerUi.Indexing -> {
        val percent = banner.percent
        if (percent == null) {
            val body = stringResource(R.string.status_indexing_body_unknown)
            BannerText(stringResource(R.string.status_indexing_title_unknown), body)
        } else {
            val body = stringResource(
                R.string.status_indexing_body,
                formatter.count(banner.done),
                formatter.count(banner.total),
            )
            BannerText(
                title = stringResource(R.string.status_indexing_title, percent),
                body = body,
                announcedTitle = stringResource(R.string.status_indexing_title, formatter.announcePercent(percent)),
            )
        }
    }

    is BannerUi.Error -> errorText(banner)
}

@Composable
private fun errorText(banner: BannerUi.Error): BannerText {
    val (title, body) = when (banner.kind) {
        IndexError.Kind.STORAGE_FULL ->
            stringResource(R.string.status_err_storage_title) to stringResource(R.string.status_err_storage_body)

        IndexError.Kind.SOURCE_FAILED -> {
            val names = banner.sources.map { sourceAreaRes(it) }.distinct().map { stringResource(it) }
            stringResource(R.string.status_err_source_title) to
                stringResource(R.string.status_err_source_body, joinWords(names, stringResource(R.string.area_and)))
        }

        IndexError.Kind.LINKING_FAILED ->
            stringResource(R.string.status_err_linking_title) to stringResource(R.string.status_err_linking_body)
    }
    return BannerText(title, body)
}

private fun sourceAreaRes(source: Source): Int = when (source) {
    Source.MEDIA_STORE -> R.string.area_files
    Source.CALL_LOG -> R.string.area_calls
    Source.SMS, Source.MMS -> R.string.area_messages
}
