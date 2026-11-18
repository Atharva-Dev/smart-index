package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.typeColorKey
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType

internal val TileSize = 48.dp
private val TileIconSize = 20.dp

/** The type icon of a leading tile. Calls pick the arrow of their [direction]. */
internal fun tileIcon(type: ItemType, direction: Direction?): ImageVector = when (type) {
    ItemType.PHOTO -> Icons.Filled.Image

    ItemType.VIDEO -> Icons.Filled.Videocam

    ItemType.DOCUMENT -> Icons.Filled.Description

    ItemType.OTHER_FILE -> Icons.Filled.InsertDriveFile

    ItemType.RECORDING -> Icons.Filled.Mic

    ItemType.MESSAGE -> Icons.Filled.Sms

    ItemType.CALL -> when (direction) {
        Direction.INCOMING -> Icons.Filled.CallReceived
        Direction.OUTGOING -> Icons.Filled.CallMade
        Direction.MISSED, Direction.REJECTED, Direction.BLOCKED -> Icons.Filled.CallMissed
        else -> Icons.Filled.Call
    }
}

/**
 * The 48 x 48 leading visual of a row without a thumbnail (Design Spec 6.5): a rounded tile (radius 12) with a
 * 20 dp icon and, when [extLabel] is set, the uppercase extension in `labelSmall`; calls and messages use a
 * circle. Colours come from [typeColorKey] and `SmartIndexTheme.colors.types`. Decorative: not focusable.
 */
@Composable
fun TypeTile(
    type: ItemType,
    direction: Direction?,
    extLabel: String?,
    modifier: Modifier = Modifier,
    corner: Dp = Radius.Tile,
) {
    val colors = SmartIndexTheme.colors.types.getValue(typeColorKey(type, direction))
    val circle = type == ItemType.CALL || type == ItemType.MESSAGE
    Box(
        modifier = modifier
            .size(TileSize)
            .background(colors.tile, if (circle) CircleShape else RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = tileIcon(type, direction),
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(TileIconSize),
            )
            if (extLabel != null && !circle) {
                Text(
                    text = extLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.icon,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
