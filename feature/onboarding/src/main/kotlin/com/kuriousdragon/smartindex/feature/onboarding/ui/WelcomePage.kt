package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.onboarding.R

private val CheckSize = 20.dp
private val FeatureStrings = listOf(R.string.welcome_feature_1, R.string.welcome_feature_2, R.string.welcome_feature_3)

/** The Welcome page (no Skip). */
@Composable
internal fun WelcomePage(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        modifier = modifier.testTag(OnboardingTestTags.WELCOME),
        actions = {
            PinnedActions(
                primaryText = stringResource(R.string.welcome_cta),
                primaryTag = OnboardingTestTags.GET_STARTED,
                onPrimary = onGetStarted,
            )
        },
    ) {
        Spacer(Modifier.height(Spacing.XXL))
        IllustrationCircle(
            icon = Icons.Outlined.Search,
            tile = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Spacing.XL))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.M))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.XL))
        FeatureStrings.forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(CheckSize),
                )
                Text(
                    text = stringResource(feature),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.M))
        }
        Spacer(Modifier.height(Spacing.S))
        PrivacyLine(R.string.welcome_privacy)
        Spacer(Modifier.height(Spacing.XL))
    }
}
