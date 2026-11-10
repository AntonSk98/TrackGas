package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PurgeTrackingDataDialog(
    dialog: ReadingDialog.ConfirmingPurge,
    loadState: ReadingLoadState,
    onRetryLoad: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pending = dialog.pending
    ResponsiveDialog(
        onDismissRequest = onDismiss,
        dismissEnabled = !pending,
    ) {
        ResponsiveDialogContent {
            Text(stringResource(R.string.purge_tracking_data), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.purge_confirmation))
            if (dialog.phase is PurgePhase.Failed) {
                Text(
                    stringResource(R.string.purge_storage_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ReadingLoadContent(loadState, onRetryLoad, inline = true)
            if (pending) Text(stringResource(R.string.purging_data))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    onClick = onDismiss,
                    enabled = !pending,
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    onClick = onConfirm,
                    enabled = !pending && loadState == ReadingLoadState.READY,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.purge_all_data)) }
            }
        }
    }
}
