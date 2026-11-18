package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Durations
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius

/**
 * Shimmer progress 0..1..0 over [Durations.SHIMMER_MS]. Under reduced motion no transition is created and the
 * value stays 0 (static).
 */
@Composable
internal fun rememberShimmerProgress(reducedMotion: Boolean): State<Float> {
    if (reducedMotion) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Durations.SHIMMER_MS / 2), RepeatMode.Reverse),
        label = "skeletonProgress",
    )
}

/**
 * Placeholder row shown while the first page loads: a 48 dp tile and two bars in `surfaceContainerHigh` fading
 * to `surfaceContainer`. Static when [LocalReducedMotion] is true. Hidden from accessibility services.
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val progress by rememberShimmerProgress(LocalReducedMotion.current)
    val color = lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
        progress,
    )
    val bar = Modifier.height(12.dp).background(color, RoundedCornerShape(Radius.Badge))
    Row(
        modifier = modifier
            .clearAndSetSemantics { this[SemanticsProperties.TestTag] = HomeTestTags.SKELETON }
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.size(TileSize).background(color, RoundedCornerShape(Radius.Tile))) {}
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Row(modifier = bar.fillMaxWidth(0.6f)) {}
            Row(modifier = bar.fillMaxWidth(0.4f)) {}
        }
    }
}
