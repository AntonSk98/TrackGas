package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R

@Composable
internal fun ReadingLoadContent(state: ReadingLoadState, onRetry: () -> Unit, inline: Boolean = false) {
    if (state == ReadingLoadState.READY) return
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(if (inline) 0.dp else 12.dp),
        horizontalAlignment = if (inline) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (state == ReadingLoadState.LOADING) {
            if (!inline) CircularProgressIndicator()
            Text(stringResource(R.string.loading_readings))
        } else {
            Text(
                stringResource(R.string.load_storage_error),
                color = if (inline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (inline) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            else Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
