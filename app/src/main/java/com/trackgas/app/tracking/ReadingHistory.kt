package com.trackgas.app.tracking

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.util.Locale

/**
 * Builds the complete reading list in the supplied newest-first order.
 *
 * History has no dependency on the selected dashboard period. Callbacks ask the screen
 * coordinator to open the shared editor or deletion confirmation, never mutate data.
 * Raw meter precision is retained in both the display and editor.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.historyItems(
    readings: List<Reading>,
    locale: Locale,
    onEdit: (Reading) -> Unit,
    onDelete: (Reading) -> Unit,
    tracker: TrackerData = TrackerData(readings),
) {
    val installations = tracker.installations.sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
    val meterNumbers = installations.mapIndexed { index, installation -> installation.id to index + 1 }.toMap()
    val ordered = readings.sortedWith(compareByDescending<Reading> { it.date }.thenByDescending { meterNumbers[it.installationId] })
    item(key = "history-heading") {
        Text(
            stringResource(R.string.reading_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (readings.isEmpty()) {
        item(key = "empty-history") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    stringResource(R.string.no_readings),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    items(ordered, key = { it.id }) { reading ->
        val date = readingDateTimeText(reading, locale)
        val meter = stringResource(R.string.meter_number, meterNumbers.getValue(reading.installationId))
        val value = stringResource(R.string.volume, numberText(reading.cubicMetres, locale, raw = true))
        val identity = stringResource(
            R.string.reading_identity, date, value, meter,
        )
        val editLabel = if (installations.size == 1) stringResource(R.string.edit_description, date)
            else stringResource(R.string.edit_meter_description, identity)
        val deleteLabel = if (installations.size == 1) stringResource(R.string.delete_description, date)
            else stringResource(R.string.delete_meter_description, identity)
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .combinedClickable(
                    onClickLabel = editLabel,
                    onLongClickLabel = deleteLabel,
                    onClick = { onEdit(reading) },
                    onLongClick = { onDelete(reading) },
                )
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(date, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                if (installations.size > 1) {
                    Text(meter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            reading.note?.takeUnless { it.isBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}
