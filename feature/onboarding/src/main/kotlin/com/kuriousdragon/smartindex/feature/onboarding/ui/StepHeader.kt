package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.onboarding.R

private const val STEP_COUNT = 5
private val DotHeight = 8.dp
private val DotWidth = 8.dp
private val CurrentDotWidth = 20.dp

/** "Step n of 5" at the start and the five progress dots at the end; a polite live region. */
@Composable
internal fun StepHeader(
    step: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.step_label, step),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepDots(step)
    }
}

@Composable
private fun StepDots(step: Int) {
    val description = stringResource(R.string.a11y_step_dots, step)
    Row(
        // One semantics node; the dots themselves are hidden from accessibility.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        for (index in 1..STEP_COUNT) {
            val current = index == step
            val dotColor =
                if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Row(
                modifier =
                    Modifier
                        .testTag(OnboardingTestTags.stepDot(index))
                        .width(if (current) CurrentDotWidth else DotWidth)
                        .height(DotHeight)
                        .background(dotColor, CircleShape),
            ) {}
        }
    }
}
