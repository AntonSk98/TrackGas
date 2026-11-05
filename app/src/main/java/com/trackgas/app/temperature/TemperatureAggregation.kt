package com.trackgas.app.temperature

import com.trackgas.app.tracking.CalendarPeriod
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Computes temperature means for chart buckets and selected periods, ignoring missing days. */
object TemperatureAggregation {
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
