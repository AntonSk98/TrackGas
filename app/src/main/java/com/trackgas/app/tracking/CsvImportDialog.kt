package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackgas.app.R

@Composable
internal fun CsvImportDialog(
    state: CsvImportState,
    loadState: ReadingLoadState,
    onRetryLoad: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: (String) -> Unit,
    onAcknowledge: (String) -> Unit,
) {
    when (state) {
        CsvImportState.Idle, is CsvImportState.Selecting -> Unit
        is CsvImportState.Cancelled -> LaunchedEffect(state.requestId) { onAcknowledge(state.requestId) }
        is CsvImportState.Reading -> ResponsiveAlertDialog(
            dismissEnabled = false,
            onDismissRequest = {},
            text = { Text(stringResource(R.string.csv_import_reading)) },
            confirmButton = {},
        )
        is CsvImportState.Applying -> ResponsiveAlertDialog(
            dismissEnabled = false,
            onDismissRequest = {},
            text = { Text(stringResource(R.string.csv_import_applying)) },
            confirmButton = {},
        )
        is CsvImportState.Preview -> ResponsiveAlertDialog(
            onDismissRequest = { onCancel(state.requestId) },
            title = { Text(stringResource(R.string.csv_import_preview_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.csv_import_replacement_warning))
                    if (state.preview.changes.isEmpty()) Text(stringResource(R.string.csv_import_no_changes))
                    if (loadState != ReadingLoadState.READY) ReadingLoadContent(loadState, onRetryLoad)
                }
            },
            confirmButton = {
                Button(onClick = onConfirm, enabled = loadState == ReadingLoadState.READY) {
                    Text(stringResource(R.string.csv_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onCancel(state.requestId) }) { Text(stringResource(R.string.cancel)) }
            },
        )
        is CsvImportState.Complete -> ResponsiveAlertDialog(
            onDismissRequest = { onAcknowledge(state.requestId) },
            text = { Text(stringResource(R.string.csv_import_complete, state.affectedMeters)) },
            confirmButton = {
                TextButton(onClick = { onAcknowledge(state.requestId) }) { Text(stringResource(R.string.ok)) }
            },
        )
        is CsvImportState.Failed -> ResponsiveAlertDialog(
            onDismissRequest = { onAcknowledge(state.requestId) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.stalePreview) Text(stringResource(R.string.csv_import_stale))
                    else Text(stringResource(csvImportErrorMessage(state.error.code)))
                    val row = state.error.row?.toString().orEmpty()
                    val field = state.error.field?.csvName.orEmpty()
                    val contextString = when {
                        row.isNotEmpty() && field.isNotEmpty() -> stringResource(R.string.csv_import_error_context, row, field)
                        row.isNotEmpty() -> stringResource(R.string.csv_import_error_row_context, row)
                        field.isNotEmpty() -> stringResource(R.string.csv_import_error_field_context, field)
                        else -> null
                    }
                    contextString?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAcknowledge(state.requestId) }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

private fun csvImportErrorMessage(code: CsvImportErrorCode): Int = when (code) {
    CsvImportErrorCode.CSV_HEADER_INVALID -> R.string.csv_import_error_header
    CsvImportErrorCode.CSV_ROW_INVALID -> R.string.csv_import_error_row
    CsvImportErrorCode.CSV_METER_ID_INVALID -> R.string.csv_import_error_meter_id
    CsvImportErrorCode.CSV_METER_UNKNOWN -> R.string.csv_import_error_meter_unknown
    CsvImportErrorCode.CSV_TIMESTAMP_INVALID -> R.string.csv_import_error_timestamp
    CsvImportErrorCode.CSV_TIMEZONE_INVALID -> R.string.csv_import_error_timezone
    CsvImportErrorCode.CSV_VALUE_INVALID -> R.string.csv_import_error_value
    CsvImportErrorCode.CSV_VALUE_NEGATIVE -> R.string.csv_import_error_negative
    CsvImportErrorCode.CSV_METER_HISTORY_INVALID -> R.string.csv_import_error_history
    CsvImportErrorCode.CSV_FILE_READ_FAILED -> R.string.csv_import_error_file
    CsvImportErrorCode.CSV_IMPORT_FAILED -> R.string.csv_import_error_generic
}
