package com.trackgas.app.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import com.trackgas.app.temperature.MeterLocation

/** Lets the user search for a place and pick a suggestion as the meter location. */
@Composable
internal fun MeterLocationDialog(
    state: MeterLocationDialog.Editing,
    onQueryChange: (String) -> Unit,
    onSelect: (MeterLocation) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val searching = state.lookup is LocationLookupState.Resolving
    val error = (state.lookup as? LocationLookupState.Error)?.reason
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meter_location_title)) },
        text = {
            Column {
                if (state.requestedAfterAdd) {
                    Text(stringResource(R.string.meter_location_prompt), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(stringResource(R.string.meter_location_input_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        when {
                            searching -> Text(stringResource(R.string.meter_location_resolving))
                            error == LocationLookupError.NOT_FOUND -> Text(stringResource(R.string.meter_location_not_found))
                            error == LocationLookupError.UNAVAILABLE -> Text(stringResource(R.string.meter_location_unavailable))
                        }
                    },
                )
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    state.suggestions.forEach { place ->
                        HorizontalDivider()
                        Text(
                            place.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onSelect(place) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = state.selected != null, onClick = onSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (state.requestedAfterAdd) R.string.meter_location_later else R.string.cancel))
            }
        },
    )
}
