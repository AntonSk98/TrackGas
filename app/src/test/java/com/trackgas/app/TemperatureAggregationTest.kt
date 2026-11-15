package com.trackgas.app

import com.trackgas.app.temperature.DailyTemperatureEntity
import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.TemperatureAggregation
import com.trackgas.app.temperature.fingerprint
import com.trackgas.app.tracking.numberText
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers period means and daily-versus-monthly temperature bucket aggregation. */
class TemperatureAggregationTest {
    @Test fun replacementStartingValueDoesNotCreateConsumptionOrTemperature() {
        val date = LocalDate.parse("2026-10-28")
        val location = com.trackgas.app.temperature.MeterLocation("Berlin", 52.52, 13.405)
        val opening = Reading("opening", date, BigDecimal("12"), "replacement")
        val tracker = com.trackgas.app.tracking.TrackerData(listOf(opening),
            listOf(com.trackgas.app.tracking.MeterInstallation("replacement", date)))
        val period = com.trackgas.app.tracking.CalendarPeriod.containing(com.trackgas.app.tracking.PeriodType.MONTH, date)
        assertNull(com.trackgas.app.tracking.dashboard(tracker, period).total.cubicMetres)
        assertTrue(com.trackgas.app.tracking.dashboard(tracker, period).buckets.isEmpty())
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

    @Test fun dailyProjectionUsesCoveredDatesAndCurrentLocationOnly() {
        val date = LocalDate.parse("2026-10-01")
        val location = MeterLocation("Berlin", 52.52, 13.405)
        val readings = listOf(
            Reading("a", date, BigDecimal.ZERO),
            Reading("b", date.plusDays(3), BigDecimal("3")),
            Reading("c", date.plusDays(4), BigDecimal("4")),
        )
        val dailyMeans = listOf(
            DailyTemperatureEntity(date.toString(), location.fingerprint(), "100"),
            DailyTemperatureEntity(date.plusDays(1).toString(), location.fingerprint(), "4"),
            DailyTemperatureEntity(date.plusDays(2).toString(), location.fingerprint(), "6"),
            DailyTemperatureEntity(date.plusDays(3).toString(), location.fingerprint(), "8"),
            DailyTemperatureEntity(date.plusDays(4).toString(), location.fingerprint(), "10"),
            DailyTemperatureEntity(date.plusDays(5).toString(), location.fingerprint(), "100"),
            DailyTemperatureEntity(date.plusDays(2).toString(), "other-location", "100"),
        )

        val projection = TemperatureAggregation.dailyTemperaturesByConsumptionDate(readings, dailyMeans, location)
        assertEquals(4, projection.size)
        assertFalse(projection.containsKey(date))
        assertFalse(projection.containsKey(date.plusDays(5)))
        assertEquals(BigDecimal("7"), TemperatureAggregation.mean(date.plusDays(1)..date.plusDays(4), projection))
        assertTrue(TemperatureAggregation.dailyTemperaturesByConsumptionDate(readings, dailyMeans, null).isEmpty())
        assertTrue(
            TemperatureAggregation.dailyTemperaturesByConsumptionDate(
                readings,
                dailyMeans,
                location.copy(latitude = 50.0),
            ).isEmpty(),
        )
        val incomplete = TemperatureAggregation.dailyTemperaturesByConsumptionDate(
            readings,
            dailyMeans.filterNot { it.date == date.plusDays(2).toString() },
            location,
        )
        assertFalse(incomplete.containsKey(date.plusDays(2)))
        val expectedIncompleteMean = BigDecimal("22").divide(BigDecimal("3"), MathContext.DECIMAL128)
        val actualIncompleteMean = requireNotNull(
            TemperatureAggregation.mean(date.plusDays(1)..date.plusDays(4), incomplete),
        )
        assertEquals(0, expectedIncompleteMean.compareTo(actualIncompleteMean))
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
