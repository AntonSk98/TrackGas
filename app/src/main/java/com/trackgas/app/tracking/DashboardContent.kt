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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import com.trackgas.app.temperature.TemperatureAggregation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds Dashboard's period controls, total, and covered weekly, daily, or monthly rows.
 *
 * [dashboard] already omits uncovered buckets. Calculation provenance and coverage
 * metadata intentionally remain hidden; totals and rows stay absolute in either chart mode.
 * Period events are delegated to the host without deriving consumption during rendering.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.dashboardItems(
    period: CalendarPeriod,
    dashboard: Dashboard,
    temperatures: Map<LocalDate, java.math.BigDecimal>,
    locale: Locale,
    chartMode: ConsumptionChartMode,
    dailyRates: Map<LocalDate, java.math.BigDecimal>,
    onSelectChartMode: (ConsumptionChartMode) -> Unit,
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
    item(key = "consumption-summary") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(chartMode.titleResource),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        val switchLabel = stringResource(R.string.chart_daily_title)
                        val selectedMode = stringResource(chartMode.labelResource)
                        Switch(
                            checked = chartMode == ConsumptionChartMode.DAILY_ESTIMATE,
                            onCheckedChange = { daily ->
                                onSelectChartMode(
                                    if (daily) ConsumptionChartMode.DAILY_ESTIMATE else ConsumptionChartMode.ABSOLUTE,
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = switchLabel
                                stateDescription = selectedMode
                            },
                        )
                    }
                    ConsumptionChart(
                        period,
                        dashboard,
                        temperatures,
                        locale,
                        TemperatureAggregation.mean(period, temperatures),
                        chartMode,
                        dailyRates,
                        Modifier.fillMaxWidth(),
                    )
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
        val rows = if (period.type == PeriodType.MONTH) weeklyRows(period, dashboard.buckets) else dashboard.buckets
        items(rows, key = {
            if (period.type == PeriodType.MONTH) "week-${it.range.start}" else "bucket-${it.range.start}"
        }) { bucket ->
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val label = if (period.type == PeriodType.MONTH) {
                    weeklyRowText(bucket.range, locale)
                } else {
                    bucketText(bucket.range.start, period.type, locale)
                }
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
                    Column(horizontalAlignment = Alignment.End) {
                        ConsumptionValue(bucket.amount, locale)
                        TemperatureAggregation.mean(bucket.range.start..bucket.range.endInclusive, temperatures)?.let { mean ->
                            Text(
                                stringResource(R.string.temperature, numberText(mean, locale)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun weeklyRowText(range: DateRange, locale: Locale): String {
    fun format(date: LocalDate): String {
        val skeleton = if (date.year == range.start.year) "MMMd" else "yMMMd"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    val start = format(range.start)
    if (range.start == range.endInclusive) return start
    return stringResource(R.string.period_range, start, format(range.endInclusive))
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
