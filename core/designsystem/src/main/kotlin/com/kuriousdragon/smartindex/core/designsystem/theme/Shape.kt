package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Design Spec §5.4 corner radii. */
object Radius {
    val Badge = 4.dp
    val Chip = 8.dp
    val Tile = 12.dp
    val Player = 16.dp
    val Sheet = 28.dp
}

val SmartIndexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
