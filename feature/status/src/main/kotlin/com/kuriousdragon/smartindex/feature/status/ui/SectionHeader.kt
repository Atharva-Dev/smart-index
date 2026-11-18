package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.feature.status.R

/** List section header: titleSmall, primary, padding 16 / 16 / 8 / 16 (top, end, bottom, start), a heading. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** The "Permissions" header; carries [StatusTestTags.PERMISSIONS_HEADER]. */
@Composable
internal fun PermissionsHeader(modifier: Modifier = Modifier) {
    SectionHeader(
        text = stringResource(R.string.status_sections_permissions),
        modifier = modifier.testTag(StatusTestTags.PERMISSIONS_HEADER),
    )
}

/** The "Indexed items" header. */
@Composable
internal fun ItemsHeader(modifier: Modifier = Modifier) {
    SectionHeader(text = stringResource(R.string.status_sections_items), modifier = modifier)
}
