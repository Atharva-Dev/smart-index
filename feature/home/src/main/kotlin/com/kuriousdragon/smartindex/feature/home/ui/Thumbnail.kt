package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import com.kuriousdragon.smartindex.feature.home.format.RowUi

private val ScrimColor = Color.Black.copy(alpha = 0.7f)
private val ThumbnailCorner = 8.dp
private const val PILL_MAX_FONT_SCALE = 1.3f

/** The duration pill sits on a fixed 48 dp tile and cannot grow, so its font scale is capped. */
internal fun pillFontScale(current: Float): Float = current.coerceAtMost(PILL_MAX_FONT_SCALE)

/**
 * Leading visual of a PHOTO or VIDEO row. The type tile is always drawn first and the Coil [AsyncImage] is laid
 * on top (`ContentScale.Crop`, clipped to 8 dp), so while loading or after a failure the tile stays visible.
 * [request] is `null` when the item has no thumbnail; only the tile shows then. A VIDEO also gets a play badge
 * and, when [RowUi.videoDuration] is set, a duration pill (white on black 70 %) at the bottom-right.
 */
@Composable
fun Thumbnail(ui: RowUi, request: ThumbnailRequest?, modifier: Modifier = Modifier) {
    // Stable per item: a recycled row with another item gets a new key and a fresh load.
    val model = remember(ui.id, request?.contentUri) { request }
    Box(modifier = modifier.size(TileSize)) {
        TypeTile(
            type = ui.type,
            direction = ui.direction,
            extLabel = null,
            corner = ThumbnailCorner,
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(TileSize).clip(RoundedCornerShape(ThumbnailCorner)),
            )
        }
        if (ui.type == ItemType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
                    .background(ScrimColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            ui.videoDuration?.let { duration ->
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, pillFontScale(d.fontScale))) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .background(ScrimColor, RoundedCornerShape(Radius.Badge))
                            .padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}
