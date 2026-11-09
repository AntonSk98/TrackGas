package com.trackgas.app.temperature

import com.trackgas.app.tracking.CalendarPeriod
import com.trackgas.app.tracking.Reading
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Adapts interval temperatures to calendar consumption buckets and computes their means. */
object TemperatureAggregation {
    /**
     * Reuses each interval mean on the dates receiving that interval's gas consumption.
     * Consumption excludes the earlier reading's date and includes the later one:
     * readings on November 6 and 9 contribute to November 7, 8, and 9.
     *
     * These repeated values are a chart approximation, not separately measured daily weather.
     * Missing means and means for obsolete endpoints or another location are omitted.
     */
    fun temperaturesByConsumptionDate(
        readings: List<Reading>,
        intervalMeans: List<IntervalTemperatureEntity>,
        location: MeterLocation?,
    ): Map<LocalDate, BigDecimal> {
        val locationFingerprint = location?.fingerprint() ?: return emptyMap()
        return buildMap {
            for (interval in readingIntervals(readings)) {
                val cachedMean = intervalMeans.firstOrNull {
                    it.location == locationFingerprint && interval.matches(it)
                } ?: continue
                val celsius = BigDecimal(cachedMean.celsius)
                var consumptionDate = interval.earlier.date.plusDays(1)
                while (consumptionDate <= interval.later.date) {
                    put(consumptionDate, celsius)
                    consumptionDate = consumptionDate.plusDays(1)
                }
            }
        }
    }

    fun mean(range: ClosedRange<LocalDate>, temperatures: Map<LocalDate, BigDecimal>): BigDecimal? {
        val values = temperatures.asSequence()
            .filter { (date, _) -> date in range }
            .map { it.value }
            .toList()
        if (values.isEmpty()) return null
        val total = values.fold(BigDecimal.ZERO, BigDecimal::add)
        return total.divide(BigDecimal.valueOf(values.size.toLong()), MathContext.DECIMAL128)
    }

    fun mean(period: CalendarPeriod, temperatures: Map<LocalDate, BigDecimal>): BigDecimal? =
        mean(period.start..period.range.endInclusive, temperatures)
}
