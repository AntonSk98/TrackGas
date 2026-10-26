package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
) {
    item(key = "history-heading") {
        Text(
            stringResource(R.string.reading_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (readings.isEmpty()) {
        item(key = "empty-history") { Text(stringResource(R.string.no_readings)) }
    }
    items(readings, key = { it.id }) { reading ->
        val date = dateText(reading.date, locale)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.volume, numberText(reading.cubicMetres, locale, raw = true)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val editDescription = stringResource(R.string.edit_description, date)
                TextButton(
                    onClick = { onEdit(reading) },
                    modifier = Modifier.semantics { contentDescription = editDescription },
                ) { Text(stringResource(R.string.edit)) }
                val deleteDescription = stringResource(R.string.delete_description, date)
                TextButton(
                    onClick = { onDelete(reading) },
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                ) { Text(stringResource(R.string.delete)) }
            }
            HorizontalDivider()
        }
    }
}
