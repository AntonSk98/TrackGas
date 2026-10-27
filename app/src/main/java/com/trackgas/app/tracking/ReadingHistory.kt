package com.trackgas.app.tracking

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    items(readings, key = { it.id }) { reading ->
        val date = dateText(reading.date, locale)
        val editLabel = stringResource(R.string.edit_description, date)
        val deleteLabel = stringResource(R.string.delete_description, date)
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .combinedClickable(
                    onClickLabel = editLabel,
                    onLongClickLabel = deleteLabel,
                    onClick = { onEdit(reading) },
                    onLongClick = { onDelete(reading) },
                )
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(date, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.volume, numberText(reading.cubicMetres, locale, raw = true)),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        HorizontalDivider()
    }
}
