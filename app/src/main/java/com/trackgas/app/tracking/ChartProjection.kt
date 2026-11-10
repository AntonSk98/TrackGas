package com.trackgas.app.tracking

import java.math.BigDecimal
import java.math.MathContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import com.trackgas.app.temperature.TemperatureAggregation

internal data class ChartEntry(
    val range: DateRange,
    val offset: Int,
    val value: BigDecimal,
    val normalizedY: Double,
) {
    val isZero: Boolean get() = value.signum() == 0
}

internal data class ChartProjection(
    val period: CalendarPeriod,
    val positionCount: Int,
    val scale: BigDecimal,
    val entries: List<ChartEntry>,
    val temperatures: List<TemperatureChartEntry> = emptyList(),
    val mode: ConsumptionChartMode = ConsumptionChartMode.ABSOLUTE,
) {
    fun amountAt(normalizedY: Double): BigDecimal {
        require(normalizedY.isFinite() && normalizedY in 0.0..1.0)
        return scale.multiply(BigDecimal.valueOf(normalizedY))
    }
}

internal data class TemperatureChartEntry(
    val offset: Int,
    val celsius: BigDecimal,
)

internal fun ChartProjection.temperatureOnly(): ChartProjection =
    copy(scale = BigDecimal.ZERO, entries = emptyList(), mode = ConsumptionChartMode.ABSOLUTE)

/** Groups covered daily Month buckets into clipped Monday–Sunday display rows. */
internal fun weeklyRows(period: CalendarPeriod, buckets: List<ConsumptionBucket>): List<ConsumptionBucket> {
    require(period.type == PeriodType.MONTH) { "Weekly rows require a monthly period" }
    val coveredBuckets = buckets.filter { it.amount.cubicMetres != null }
    coveredBuckets.forEach { bucket ->
        require(bucket.range.start == bucket.range.endInclusive && bucket.range.start in period.range.start..period.range.endInclusive) {
            "Weekly rows require daily buckets inside the selected month"
        }
        require(bucket.amount.coveredDays == 1L && bucket.amount.totalDays == 1L) {
            "Weekly rows require covered daily amounts"
        }
    }
    require(coveredBuckets.map { it.range.start }.distinct().size == coveredBuckets.size) {
        "Weekly rows cannot contain duplicate daily buckets"
    }

    return coveredBuckets.groupBy {
        it.range.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }.toSortedMap().map { (monday, days) ->
        val range = DateRange(maxOf(period.start, monday), minOf(period.range.endInclusive, monday.plusDays(6)))
        val amount = days.fold(BigDecimal.ZERO) { sum, bucket ->
            sum.add(requireNotNull(bucket.amount.cubicMetres))
        }
        ConsumptionBucket(
            range,
            ConsumptionAmount(
                amount,
                days.size.toLong(),
                range.days,
                days.any { it.amount.estimatedDistribution },
            ),
        )
    }
}

/** Selects chart-only values without changing absolute accounting or temperature coverage. */
internal fun chartProjection(
    period: CalendarPeriod,
    dashboard: Dashboard,
    temperatures: Map<LocalDate, BigDecimal> = emptyMap(),
    mode: ConsumptionChartMode = ConsumptionChartMode.ABSOLUTE,
    dailyRates: Map<LocalDate, BigDecimal> = emptyMap(),
): ChartProjection {
    val buckets = dashboard.buckets.filter { it.amount.cubicMetres != null }.sortedBy { it.range.start }
    val values = when (mode) {
        ConsumptionChartMode.ABSOLUTE -> buckets.map { it.range to requireNotNull(it.amount.cubicMetres) }
        ConsumptionChartMode.DAILY_ESTIMATE -> period.buckets().mapNotNull { range ->
            val value = if (period.type == PeriodType.YEAR) {
                TemperatureAggregation.mean(range.start..range.endInclusive, dailyRates)
            } else {
                dailyRates[range.start]
            }
            value?.let { range to it }
        }
    }
    val scale = values.maxOfOrNull { it.second } ?: BigDecimal.ZERO
    val count = if (period.type == PeriodType.YEAR) 12 else period.range.days.toInt()
    fun offsetOf(range: DateRange): Int {
        val offset = (if (period.type == PeriodType.YEAR) ChronoUnit.MONTHS else ChronoUnit.DAYS)
            .between(period.start, range.start).toInt()
        require(offset in 0 until count && range.endInclusive <= period.range.endInclusive) {
            "Chart buckets must belong to the selected calendar period"
        }
        return offset
    }
    val entries = values.map { (range, value) ->
        ChartEntry(
            range = range,
            offset = offsetOf(range),
            value = value,
            normalizedY = if (value.signum() == 0) 0.0 else {
                // Decimal normalization precedes the lossy graphics conversion. A positive
                // amount that underflows Double is still not a genuine zero.
                value.divide(scale, MathContext.DECIMAL128).toDouble()
                    .coerceIn(Double.MIN_VALUE, 1.0)
            },
        )
    }
    // Keep absolute coverage even when opening-only consumption has no eligible gas rate.
    val temperatureEntries = buckets.mapNotNull { bucket ->
        val offset = offsetOf(bucket.range)
        val range = if (period.type == PeriodType.YEAR) {
            val start = period.start.plusMonths(offset.toLong())
            start..start.plusMonths(1).minusDays(1)
        } else {
            bucket.range.start..bucket.range.endInclusive
        }
        TemperatureAggregation.mean(range, temperatures)?.let { TemperatureChartEntry(offset, it) }
    }
    return ChartProjection(period, count, scale, entries, temperatureEntries, mode)
}
