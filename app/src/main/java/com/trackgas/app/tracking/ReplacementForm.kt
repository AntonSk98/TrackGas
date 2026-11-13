package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.trackgas.app.R
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReplacementForm(
    dialog: ReadingDialog.Replacing,
    locale: Locale,
    loadState: ReadingLoadState,
    onRetryLoad: () -> Unit,
    onClearError: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var startingValue by rememberSaveable(dialog.dialogId) { mutableStateOf("") }
    val pending = dialog.pending
    val unavailable = (dialog.phase as? ReplacementPhase.Unavailable)?.error
    val invalidStartingValue = unavailable == ReplacementError.INVALID_STARTING_READING
    ResponsiveDialog(
        onDismissRequest = onDismiss,
        dismissEnabled = !pending,
    ) {
        ResponsiveDialogContent {
            Text(stringResource(R.string.replace_meter), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = dateText(dialog.target.date, locale), onValueChange = {},
                modifier = Modifier.fillMaxWidth(), readOnly = true, enabled = !pending,
                label = { Text(stringResource(R.string.date)) },
            )
            OutlinedTextField(
                value = startingValue,
                onValueChange = {
                    startingValue = it
                    onClearError()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !pending,
                label = { Text(stringResource(R.string.starting_value)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = invalidStartingValue,
                supportingText = if (invalidStartingValue) {
                    { Text(stringResource(R.string.replacement_starting_value_invalid)) }
                } else {
                    null
                },
            )
            if (unavailable != null && !invalidStartingValue) {
                Text(
                    stringResource(replacementErrorResource(unavailable)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (dialog.phase is ReplacementPhase.Failed) {
                Text(
                    stringResource(R.string.replacement_storage_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ReadingLoadContent(loadState, onRetryLoad, inline = true)
            if (pending) Text(stringResource(R.string.replacing_meter))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !pending) { Text(stringResource(R.string.cancel)) }
                if (unavailable == null || invalidStartingValue) {
                    Button(
                        onClick = { onConfirm(startingValue) },
                        enabled = !pending && loadState == ReadingLoadState.READY,
                    ) { Text(stringResource(R.string.confirm_replacement)) }
                }
            }
        }
    }
}

private fun replacementErrorResource(error: ReplacementError): Int = when (error) {
    ReplacementError.ALREADY_REPLACED -> R.string.replacement_already_replaced
    ReplacementError.OBSOLETE_TARGET -> R.string.replacement_obsolete
    ReplacementError.STALE_DATE -> R.string.replacement_stale_date
    ReplacementError.NOT_LATEST -> R.string.replacement_not_latest
    ReplacementError.INVALID_STARTING_READING -> R.string.replacement_starting_value_invalid
}
