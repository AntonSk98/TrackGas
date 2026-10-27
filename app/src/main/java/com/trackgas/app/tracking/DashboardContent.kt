package com.trackgas.app.tracking

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds Dashboard's period controls, total, and covered date/month rows.
 *
 * [dashboard] already omits uncovered buckets. Calculation provenance and coverage
 * metadata intentionally remain hidden; chart and rows share the same covered values.
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
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodType.entries.forEach { type ->
                FilterChip(
                    modifier = Modifier.heightIn(min = 48.dp),
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
    item(key = "local-notice") { LocalStorageNotice() }
    item(key = "consumption-summary") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val total = dashboard.total.cubicMetres
                if (total == null) {
                    Text(
                        stringResource(R.string.no_consumption),
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(
                        stringResource(R.string.consumption_total),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.volume, numberText(total, locale)),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ConsumptionChart(period, dashboard, locale, Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = { onNavigate(-1) }) {
                        Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.previous_period))
                    }
                    Text(
                        periodHeadingText(period, locale),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = { onNavigate(1) }) {
                        Icon(painterResource(R.drawable.ic_next), stringResource(R.string.next_period))
                    }
                }
            }
        }
    }
    if (dashboard.total.cubicMetres != null) {
        items(dashboard.buckets, key = { "bucket-${it.range.start}" }) { bucket ->
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val label = bucketText(bucket.range.start, period.type, locale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConsumptionValue(bucket.amount, locale)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun periodHeadingText(period: CalendarPeriod, locale: Locale): String {
    fun format(date: LocalDate, skeleton: String): String =
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))

    return when (period.type) {
        PeriodType.WEEK -> {
            val end = period.range.endInclusive
            stringResource(
                R.string.period_range,
                format(period.start, if (period.start.year == end.year) "MMMd" else "yMMMd"),
                format(end, "yMMMd"),
            )
        }
        PeriodType.MONTH -> format(period.start, stringResource(R.string.month_date_skeleton))
        PeriodType.YEAR -> format(period.start, "y")
    }
}

/** Renders covered consumption only; null would indicate a projection/caller invariant violation. */
@Composable
private fun ConsumptionValue(amount: ConsumptionAmount, locale: Locale) {
    val value = requireNotNull(amount.cubicMetres) { "Consumption content must have covered usage" }
    Text(stringResource(R.string.volume, numberText(value, locale)))
}
