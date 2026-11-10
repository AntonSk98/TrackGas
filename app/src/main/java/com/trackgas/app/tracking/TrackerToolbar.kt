package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Keeps tracker actions visible while allowing the brand/title to wrap at enlarged font sizes.
 *
 * Unlike a fixed-height app bar, this row can grow beyond its 56 dp content minimum.
 * Actions are measured first; [title] receives the remaining width. Standard app-bar
 * window insets are applied here, and [actions] must supply their own 48 dp touch targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerToolbar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(end = 8.dp)) { title() }
            actions()
        }
    }
}
