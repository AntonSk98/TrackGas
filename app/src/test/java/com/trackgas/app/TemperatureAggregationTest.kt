package com.trackgas.app

import com.trackgas.app.temperature.TemperatureAggregation
import com.trackgas.app.tracking.numberText
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers period means and daily-versus-monthly temperature bucket aggregation. */
class TemperatureAggregationTest {
    @Test fun replacementOpeningConsumptionDoesNotInventTemperature() {
        val date = LocalDate.parse("2026-10-28")
        val location = com.trackgas.app.temperature.MeterLocation("Berlin", 52.52, 13.405)
        val opening = Reading("opening", date, BigDecimal("12"), "replacement")
        val tracker = com.trackgas.app.tracking.TrackerData(listOf(opening),
            listOf(com.trackgas.app.tracking.MeterInstallation("replacement", date)))
        val period = com.trackgas.app.tracking.CalendarPeriod.containing(com.trackgas.app.tracking.PeriodType.MONTH, date)
        assertEquals(BigDecimal("12"), com.trackgas.app.tracking.dashboard(tracker, period).total.cubicMetres)
        assertTrue(com.trackgas.app.temperature.readingIntervals(tracker.readings).isEmpty())
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(tracker.readings, emptyList(), location).isEmpty())
    }

    @Test fun intervalProjectionWeightsCoveredDatesAndRejectsStaleInputs() {
        val date = LocalDate.parse("2026-10-01")
        val location = com.trackgas.app.temperature.MeterLocation("Berlin", 52.52, 13.405)
        val readings = listOf(
            Reading("a", date, BigDecimal.ZERO),
            Reading("b", date.plusDays(3), BigDecimal("3")),
            Reading("c", date.plusDays(4), BigDecimal("4")),
        )
        val intervals = com.trackgas.app.temperature.readingIntervals(readings)
        val means = listOf(intervals[0].row(location, "4"), intervals[1].row(location, "8"))
        val projection = TemperatureAggregation.temperaturesByConsumptionDate(readings, means, location)
        assertEquals(4, projection.size)
        assertFalse(projection.containsKey(date))
        assertEquals(0, BigDecimal("5").compareTo(TemperatureAggregation.mean(date..date.plusDays(4), projection)))
        assertEquals(BigDecimal("4"), TemperatureAggregation.mean(date.plusDays(2)..date.plusDays(2), projection))
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(readings, means, location.copy(latitude = 50.0)).isEmpty())
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(readings, means, null).isEmpty())
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(listOf(readings.first(), readings.last()), means, location).isEmpty())
        val changed = readings.map { if (it.id == "b") it.copy(observedAt = it.observedAt.plusSeconds(60)) else it }
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(changed, means, location).isEmpty())
        val missing = TemperatureAggregation.temperaturesByConsumptionDate(readings, means.take(1), location)
        assertFalse(missing.containsKey(date.plusDays(4)))
        assertEquals(BigDecimal("4"), TemperatureAggregation.mean(date..date.plusDays(4), missing))
    }
    private val temperatures = mapOf(
        date("2026-10-01") to BigDecimal("4"),
        date("2026-10-02") to BigDecimal("6"),
        date("2026-10-03") to BigDecimal("8"),
    )

    @Test
    fun periodAndBucketMeansIgnoreDatesWithoutTemperature() {
        assertEquals(
            BigDecimal("6"),
            TemperatureAggregation.mean(date("2026-10-01")..date("2026-10-04"), temperatures),
        )
        assertNull(TemperatureAggregation.mean(date("2026-10-04")..date("2026-10-05"), temperatures))
        assertNull(TemperatureAggregation.mean(date("2026-10-04")..date("2026-10-05"), emptyMap()))
    }

    @Test
    fun displayedMeansUseLocalizedDecimals() {
        val mean = TemperatureAggregation.mean(date("2026-10-01")..date("2026-10-02"), temperatures)
        assertEquals("5", numberText(requireNotNull(mean), Locale.US))
        assertEquals("5", numberText(requireNotNull(mean), Locale.GERMANY))
        val fractionalMean = TemperatureAggregation.mean(
            date("2026-10-01")..date("2026-10-02"),
            mapOf(
                date("2026-10-01") to BigDecimal("4"),
                date("2026-10-02") to BigDecimal("7"),
            ),
        )
        assertEquals("5.5", numberText(requireNotNull(fractionalMean), Locale.US))
        assertEquals("5,5", numberText(requireNotNull(fractionalMean), Locale.GERMANY))
    }

    private fun date(value: String) = LocalDate.parse(value)
}
