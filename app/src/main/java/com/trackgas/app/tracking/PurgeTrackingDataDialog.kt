package com.trackgas.app.tracking

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = { if (!pending) onDismiss() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss, pending) {
                detectTapGestures { if (!pending) onDismiss() }
            })
            Surface(
                modifier = Modifier.safeDrawingPadding().imePadding().padding(16.dp)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
    }
}
