package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.A11yLabels
import com.kuriousdragon.smartindex.feature.home.format.MetaIcon
import com.kuriousdragon.smartindex.feature.home.format.RowAction
import com.kuriousdragon.smartindex.feature.home.format.RowUi
import com.kuriousdragon.smartindex.feature.home.format.TitleEllipsis
import com.kuriousdragon.smartindex.feature.home.format.Trailing
import com.kuriousdragon.smartindex.feature.home.format.toAnnotated

private const val LARGE_FONT_SCALE = 1.5f
private const val SNIPPET_LINES = 3
private val TrailingSize = 48.dp
private val TrailingVisual = 40.dp
private val MetaIconSize = 16.dp

/**
 * The one list row for every item type (Design Spec 6.5, LLD 4.5). Stateless: every string comes from [ui].
 *
 * The row is `Row(testTag = sidx_row)` holding two siblings: a weighted clickable box (leading visual plus text
 * column, one accessibility node carrying [RowUi.a11yLabel], an `onClickLabel` and the custom actions of
 * [RowUi.actions]) and the 48 x 48 trailing slot, so the trailing button keeps its own focus and label.
 *
 * @param ui the formatted row.
 * @param playing the recording behind this row is playing: primaryContainer background and a filled pause button.
 * @param isCurrent the recording behind this row is the player's current one (playing or paused). A paused
 *   current row keeps the play triangle (C3) but tints it primary.
 * @param onClick tap on the box: open (files), show actions (calls, recordings) or open in Messages.
 * @param onMore tap on the trailing more button.
 * @param onPlayPause tap on the trailing play or pause button.
 * @param onAction a TalkBack custom action chosen from [RowUi.actions].
 * @param thumbnail the Coil request for a PHOTO or VIDEO leading image. [RowUi] carries no
 *   [ThumbnailRequest], so the caller fills it from the `ResultItem` with `ThumbnailRequest.of(item)`
 *   (`null` shows only the type tile).
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun ListRow(
    ui: RowUi,
    playing: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onPlayPause: () -> Unit,
    onAction: (RowAction) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: ThumbnailRequest? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val resources = LocalResources.current
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

    val background = when {
        playing -> colors.primaryContainer
        pressed -> colors.surfaceContainerHigh
        else -> Color.Transparent
    }
    val titleColor = if (playing) colors.onPrimaryContainer else colors.onSurface
    val pauseLabel = stringResource(R.string.a11y_pause_recording)
    val clickLabel = stringResource(
        when (ui.type) {
            ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE -> R.string.sheet_open
            ItemType.CALL, ItemType.RECORDING -> R.string.a11y_show_actions
            ItemType.MESSAGE -> R.string.a11y_msg_open
        },
    )
    val customActions = remember(ui.actions, playing, onAction, pauseLabel) {
        ui.actions.map { action ->
            val label = if (playing && (action == RowAction.PLAY || action == RowAction.PLAY_RECORDING)) {
                pauseLabel
            } else {
                A11yLabels.actionLabel(action, resources)
            }
            CustomAccessibilityAction(label) {
                onAction(action)
                true
            }
        }
    }

    Row(
        modifier = modifier
            .testTag(HomeTestTags.ROW)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(background)
            .then(if (focused) Modifier.border(2.dp, colors.primary) else Modifier)
            .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClickLabel = clickLabel,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = ui.a11yLabel
                    this.customActions = customActions
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Leading(ui, thumbnail)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TitleAndTime(ui, titleColor, largeFont)
                MetaLine(ui, largeFont)
                ui.snippet?.let { snippet ->
                    Text(
                        text = snippet.toAnnotated(highlightStyle()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = SNIPPET_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TrailingSlot(ui, playing, isCurrent, onMore, onPlayPause)
    }
}

@Composable
private fun highlightStyle() = SpanStyle(fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Leading(ui: RowUi, thumbnail: ThumbnailRequest?) {
    when (ui.type) {
        ItemType.PHOTO, ItemType.VIDEO -> Thumbnail(ui, thumbnail)
        else -> TypeTile(ui.type, ui.direction, ui.extLabel)
    }
}

@Composable
private fun TitleAndTime(ui: RowUi, titleColor: Color, largeFont: Boolean) {
    val title: @Composable (Modifier) -> Unit = { m ->
        Text(
            text = ui.title.toAnnotated(highlightStyle()),
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = if (ui.titleEllipsis ==
                TitleEllipsis.MIDDLE
            ) {
                TextOverflow.MiddleEllipsis
            } else {
                TextOverflow.Ellipsis
            },
            modifier = m,
        )
    }
    val time: @Composable () -> Unit = {
        Text(
            text = ui.time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
        )
    }
    if (largeFont) {
        Column {
            title(Modifier)
            time()
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.weight(1f))
            time()
        }
    }
}

@Composable
private fun MetaLine(ui: RowUi, largeFont: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ui.metaIcon?.let { icon ->
            val tint = if (icon == MetaIcon.MISSED || icon == MetaIcon.REJECTED || icon == MetaIcon.BLOCKED) {
                SmartIndexTheme.colors.types.getValue(TypeColorKey.MISSED).icon
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(metaImage(icon), contentDescription = null, tint = tint, modifier = Modifier.size(MetaIconSize))
        }
        Text(
            text = ui.meta.toAnnotated(highlightStyle()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (largeFont) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (ui.showAttachment) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MetaIconSize),
            )
        }
    }
}

private fun metaImage(icon: MetaIcon): ImageVector = when (icon) {
    MetaIcon.INCOMING -> Icons.Filled.CallReceived
    MetaIcon.OUTGOING -> Icons.Filled.CallMade
    MetaIcon.MISSED, MetaIcon.REJECTED, MetaIcon.BLOCKED -> Icons.Filled.CallMissed
    MetaIcon.RECORDING -> Icons.Filled.Mic
    MetaIcon.SMS_RECEIVED, MetaIcon.SMS_SENT -> Icons.Filled.Sms
}

@Composable
private fun TrailingSlot(
    ui: RowUi,
    playing: Boolean,
    isCurrent: Boolean,
    onMore: () -> Unit,
    onPlayPause: () -> Unit,
) {
    when (ui.trailing) {
        Trailing.MORE -> IconButton(onClick = onMore, modifier = Modifier.size(TrailingSize)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.a11y_action_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Trailing.PLAY -> {
            val label = if (playing) {
                stringResource(R.string.a11y_pause_recording)
            } else {
                stringResource(R.string.a11y_play_recording_of, ui.title.text)
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(TrailingSize)) {
                val primary = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(TrailingVisual)
                        .background(if (playing) primary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = label,
                        tint = when {
                            playing -> MaterialTheme.colorScheme.onPrimary
                            isCurrent -> primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Trailing.NONE -> Spacer(Modifier.size(TrailingSize))
    }
}
