package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
import java.util.Locale

@Composable
internal fun DeleteReadingDialog(
    dialog: ReadingDialog.ConfirmingDeletion,
    locale: Locale,
    loadState: ReadingLoadState,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pending = dialog.pending
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(stringResource(R.string.delete_reading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(
                    R.string.delete_confirmation,
                    stringResource(R.string.volume, numberText(dialog.reading.cubicMetres, locale, raw = true)),
                    dateText(dialog.reading.date, locale),
                ))
                if (dialog.phase is DeletePhase.Failed || dialog.phase == DeletePhase.Missing) {
                    Text(
                        stringResource(if (dialog.phase == DeletePhase.Missing) R.string.reading_missing else R.string.delete_storage_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                ReadingLoadContent(loadState, onRetry, inline = true)
                if (pending) Text(stringResource(R.string.deleting_reading))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !pending && loadState == ReadingLoadState.READY,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(enabled = !pending, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
