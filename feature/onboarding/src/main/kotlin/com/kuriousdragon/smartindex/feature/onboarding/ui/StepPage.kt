package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.feature.onboarding.R
import com.kuriousdragon.smartindex.feature.onboarding.model.ONBOARDING_GROUPS
import com.kuriousdragon.smartindex.feature.onboarding.model.stepContent

/** One permission step: header, circle, title, body, reads card and privacy line, with CTA and Skip pinned. */
@Composable
internal fun StepPage(
    group: Group,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = stepContent(group)
    val colors = groupColors(group)
    PageScaffold(
        modifier = modifier.testTag(OnboardingTestTags.step(group)),
        actions = {
            PinnedActions(
                primaryText = stringResource(content.cta),
                primaryTag = OnboardingTestTags.ALLOW,
                onPrimary = onAllow,
                onSkip = onSkip,
            )
        },
    ) {
        Spacer(Modifier.height(Spacing.L))
        StepHeader(step = ONBOARDING_GROUPS.indexOf(group) + 1)
        Spacer(Modifier.height(Spacing.XL))
        IllustrationCircle(
            icon = groupIcon(group),
            tile = colors.tile,
            tint = colors.icon,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Spacing.XL))
        Text(
            text = stringResource(content.title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(Spacing.M))
        Text(
            text = stringResource(content.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.XL))
        ReadsCard(heading = content.readsHeading, items = content.reads)
        Spacer(Modifier.height(Spacing.L))
        PrivacyLine(R.string.step_privacy)
        Spacer(Modifier.height(Spacing.XL))
    }
}
