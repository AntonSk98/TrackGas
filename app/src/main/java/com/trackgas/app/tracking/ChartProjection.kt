package com.trackgas.app.tracking

import java.math.BigDecimal
import java.math.MathContext
import java.time.temporal.ChronoUnit

internal data class ChartEntry(
    val range: DateRange,
    val offset: Int,
    val cubicMetres: BigDecimal,
    val normalizedY: Double,
) {
    val isZero: Boolean get() = cubicMetres.signum() == 0
}

internal data class ChartProjection(
    val period: CalendarPeriod,
    val positionCount: Int,
    val scale: BigDecimal,
    val entries: List<ChartEntry>,
) {
    fun cubicMetresAt(normalizedY: Double): BigDecimal {
        require(normalizedY.isFinite() && normalizedY in 0.0..1.0)
        return scale.multiply(BigDecimal.valueOf(normalizedY))
    }
}

/** Copies covered buckets, never deriving consumption or a total from chart coordinates. */
internal fun chartProjection(period: CalendarPeriod, dashboard: Dashboard): ChartProjection {
    val buckets = dashboard.buckets.filter { it.amount.cubicMetres != null }.sortedBy { it.range.start }
    val scale = buckets.maxOfOrNull { requireNotNull(it.amount.cubicMetres) } ?: BigDecimal.ZERO
    val count = if (period.type == PeriodType.YEAR) 12 else period.range.days.toInt()
    val entries = buckets.map { bucket ->
        val value = requireNotNull(bucket.amount.cubicMetres)
        val offset = (if (period.type == PeriodType.YEAR) ChronoUnit.MONTHS else ChronoUnit.DAYS)
            .between(period.start, bucket.range.start).toInt()
        require(offset in 0 until count && bucket.range.endInclusive <= period.range.endInclusive) {
            "Chart buckets must belong to the selected calendar period"
        }
        ChartEntry(
            range = bucket.range,
            offset = offset,
            cubicMetres = value,
            normalizedY = if (value.signum() == 0) 0.0 else {
                // Decimal normalization precedes the lossy graphics conversion. A positive
                // amount that underflows Double is still not a genuine zero.
                value.divide(scale, MathContext.DECIMAL128).toDouble()
                    .coerceIn(Double.MIN_VALUE, 1.0)
            },
        )
    }
    return ChartProjection(period, count, scale, entries)
}
