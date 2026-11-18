package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.onboarding.R

/** Full-width filled 48 dp primary button and an optional **Skip** text button; place inside [PageScaffold]'s actions. */
@Composable
internal fun ColumnScope.PinnedActions(
    primaryText: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouch).testTag(primaryTag),
        ) {
            Text(primaryText)
        }
        if (onSkip != null) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTouch).testTag(OnboardingTestTags.SKIP),
            ) {
                Text(stringResource(R.string.step_skip))
            }
        }
    }
}
