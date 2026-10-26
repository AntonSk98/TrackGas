package com.trackgas.app.tracking

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds Dashboard's period controls, total, and covered date/month rows.
 *
 * [dashboard] already omits uncovered buckets. Calculation provenance and coverage
 * metadata intentionally remain hidden; this renderer shows dates and values only.
 * Period events are delegated to the host without deriving consumption during rendering.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.dashboardItems(
    period: CalendarPeriod,
    dashboard: Dashboard,
    locale: Locale,
    onSelectPeriod: (PeriodType) -> Unit,
    onNavigate: (Long) -> Unit,
) {
    item(key = "period-types") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodType.entries.forEach { type ->
                FilterChip(
                    selected = period.type == type,
                    onClick = { onSelectPeriod(type) },
                    label = { Text(stringResource(when (type) {
                        PeriodType.WEEK -> R.string.week
                        PeriodType.MONTH -> R.string.month
                        PeriodType.YEAR -> R.string.year
                    })) },
                )
            }
        }
    }
    item(key = "period-range") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.period_range, dateText(period.range.start, locale), dateText(period.range.endInclusive, locale)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onNavigate(-1) }) { Text(stringResource(R.string.previous_period)) }
                TextButton(onClick = { onNavigate(1) }) { Text(stringResource(R.string.next_period)) }
            }
        }
    }
    if (dashboard.total.cubicMetres == null) {
        item(key = "empty-consumption") { Text(stringResource(R.string.no_consumption)) }
    } else {
        item(key = "consumption-summary") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.consumption_total),
                    style = MaterialTheme.typography.titleMedium,
                )
                ConsumptionValue(dashboard.total, locale)
            }
        }
        items(dashboard.buckets, key = { "bucket-${it.range.start}" }) { bucket ->
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val label = if (period.type == PeriodType.YEAR) {
                    val pattern = DateFormat.getBestDateTimePattern(locale, stringResource(R.string.month_date_skeleton))
                    bucket.range.start.format(DateTimeFormatter.ofPattern(pattern, locale))
                } else dateText(bucket.range.start, locale)
                Text(label, style = MaterialTheme.typography.titleSmall)
                ConsumptionValue(bucket.amount, locale)
                HorizontalDivider()
            }
        }
    }
}

/** Renders covered consumption only; null would indicate a projection/caller invariant violation. */
@Composable
private fun ConsumptionValue(amount: ConsumptionAmount, locale: Locale) {
    val value = requireNotNull(amount.cubicMetres) { "Consumption content must have covered usage" }
    Text(stringResource(R.string.volume, numberText(value, locale)))
}
