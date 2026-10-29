package com.trackgas.app

import com.trackgas.app.tracking.CalendarPeriod
import com.trackgas.app.tracking.ConsumptionAmount
import com.trackgas.app.tracking.ConsumptionBucket
import com.trackgas.app.tracking.Dashboard
import com.trackgas.app.tracking.DateRange
import com.trackgas.app.tracking.PeriodType
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.MeterReplacement
import com.trackgas.app.tracking.ORIGINAL_INSTALLATION_ID
import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.chartAmountText
import com.trackgas.app.tracking.chartAxisText
import com.trackgas.app.tracking.chartProjection
import com.trackgas.app.tracking.dashboard
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ChartProjectionTest {
    private val october = CalendarPeriod(PeriodType.MONTH, LocalDate.parse("2026-10-01"))
    private val year = CalendarPeriod(PeriodType.YEAR, LocalDate.parse("2026-01-01"))

    private fun reading(date: String, value: String) = Reading(date, LocalDate.parse(date), BigDecimal(value))

    private fun bucket(date: String, value: String): ConsumptionBucket {
        val day = LocalDate.parse(date)
        return ConsumptionBucket(DateRange(day, day), ConsumptionAmount(BigDecimal(value), 1, 1, false))
    }

    private fun snapshot(vararg buckets: ConsumptionBucket): Dashboard = Dashboard(
        ConsumptionAmount(BigDecimal("987.654321"), buckets.size.toLong(), 31, false),
        buckets.toList(),
    )

    @Test fun emptyBaselineAndOutsidePeriodHaveNoMarksOrInventedScale() {
        for (readings in listOf(
            emptyList(),
            listOf(reading("2026-10-01", "1000")),
            listOf(reading("2026-09-01", "1000"), reading("2026-09-02", "1004")),
        )) {
            val projected = chartProjection(october, dashboard(readings, october))
            assertEquals(31, projected.positionCount)
            assertTrue(projected.entries.isEmpty())
            assertEquals(BigDecimal.ZERO, projected.scale)
            assertEquals(0, projected.cubicMetresAt(1.0).signum())
        }
    }

    @Test fun singleCoveredDayRetainsItsPositionAndOriginalDecimal() {
        val source = bucket("2026-10-20", "12.340000")
        val data = snapshot(source)
        val projected = chartProjection(october, data)
        val entry = projected.entries.single()
        assertSame(source.amount.cubicMetres, entry.cubicMetres)
        assertSame(source.range, entry.range)
        assertEquals(19, entry.offset)
        assertEquals(1.0, entry.normalizedY, 0.0)
        assertFalse(entry.isZero)
        assertEquals(BigDecimal("12.340000"), projected.scale)
        assertEquals(BigDecimal("987.654321"), data.total.cubicMetres)
    }

    @Test fun zerosStayCoveredButMissingDaysHaveNoEntries() {
        val projected = chartProjection(
            october,
            snapshot(bucket("2026-10-31", "0.000"), bucket("2026-10-02", "0")),
        )
        assertEquals(listOf(1, 30), projected.entries.map { it.offset })
        assertTrue(projected.entries.all { it.isZero && it.normalizedY == 0.0 })
        assertEquals(0, projected.scale.signum())
        assertEquals(0, projected.cubicMetresAt(0.5).signum())
        assertEquals(31, projected.positionCount)
    }

    @Test fun unknownBucketDoesNotBecomeAZeroMark() {
        val missing = ConsumptionBucket(
            DateRange(october.start, october.start),
            ConsumptionAmount(null, 0, 1, false),
        )
        val data = Dashboard(
            ConsumptionAmount(BigDecimal.ZERO, 1, 31, false),
            listOf(missing, bucket("2026-10-02", "0")),
        )
        assertEquals(listOf(1), chartProjection(october, data).entries.map { it.offset })
    }

    @Test fun weekOffsetsUseCalendarDaysAcrossMonthBoundary() {
        val week = CalendarPeriod(PeriodType.WEEK, LocalDate.parse("2026-09-28"))
        val data = snapshot(bucket("2026-10-04", "2"), bucket("2026-09-29", "1"))
        val projected = chartProjection(week, data)
        assertEquals(7, projected.positionCount)
        assertEquals(listOf(1, 6), projected.entries.map { it.offset })
        assertEquals(listOf(0.5, 1.0), projected.entries.map { it.normalizedY })
    }

    @Test fun octoberExampleUsesDailyOrMonthlyAuthoritativeBuckets() {
        val readings = listOf(reading("2026-10-01", "1000"), reading("2026-10-04", "1012"))
        val monthData = dashboard(readings, october)
        val daily = chartProjection(october, monthData)
        assertEquals(listOf(1, 2, 3), daily.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("4"), BigDecimal("4"), BigDecimal("4")), daily.entries.map { it.cubicMetres })
        val yearData = dashboard(readings, year)
        val monthly = chartProjection(year, yearData)
        assertEquals(12, monthly.positionCount)
        assertEquals(9, monthly.entries.single().offset)
        assertEquals(BigDecimal("12"), monthly.entries.single().cubicMetres)
        assertEquals(monthData.total.cubicMetres, yearData.total.cubicMetres)
    }

    @Test fun crossMonthAllocationKeepsActualMonthOffsetsAndExactValues() {
        val readings = listOf(reading("2026-01-30", "1000"), reading("2026-03-01", "1030"))
        val data = dashboard(readings, year)
        val projected = chartProjection(year, data)
        assertEquals(listOf(0, 1, 2), projected.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("1"), BigDecimal("28"), BigDecimal("1")), projected.entries.map { it.cubicMetres })
        data.buckets.zip(projected.entries).forEach { (source, entry) ->
            assertSame(source.amount.cubicMetres, entry.cubicMetres)
            assertEquals(source.range, entry.range)
        }
        assertEquals(BigDecimal("30"), data.total.cubicMetres)
    }

    @Test fun largeTinyAndMixedDecimalsOnlyNormalizeAtTheGraphicsBoundary() {
        for (values in listOf(
            listOf("1E+1000", "2E+1000"),
            listOf("1E-1000", "2E-1000"),
            listOf("1E-1000", "1E+1000"),
        )) {
            val data = snapshot(bucket("2026-10-02", values[0]), bucket("2026-10-31", values[1]))
            val projected = chartProjection(october, data)
            assertEquals(1.0, projected.entries.last().normalizedY, 0.0)
            projected.entries.forEachIndexed { index, entry ->
                assertTrue(entry.normalizedY.isFinite())
                assertTrue(entry.normalizedY > 0.0 && entry.normalizedY <= 1.0)
                assertFalse(entry.isZero)
                assertEquals(BigDecimal(values[index]), entry.cubicMetres)
                assertSame(data.buckets[index].amount.cubicMetres, entry.cubicMetres)
            }
            assertEquals(0, projected.scale.compareTo(projected.cubicMetresAt(1.0)))
        }
    }

    @Test fun allCalendarPositionsFitEvenForAFullMonth() {
        val data = dashboard(
            listOf(reading("2026-09-30", "0"), reading("2026-10-31", "31")),
            october,
        )
        val projected = chartProjection(october, data)
        assertEquals(31, projected.entries.size)
        assertEquals((0..30).toList(), projected.entries.map { it.offset })
    }

    @Test fun thirdsPreserveOriginalPrecisionWithoutReplacingIndependentTotal() {
        val data = dashboard(
            listOf(reading("2026-10-01", "0"), reading("2026-10-04", "1")),
            october,
        )
        val projected = chartProjection(october, data)
        assertEquals(BigDecimal.ONE, data.total.cubicMetres)
        data.buckets.zip(projected.entries).forEach { (source, entry) ->
            assertSame(source.amount.cubicMetres, entry.cubicMetres)
            assertEquals(BigDecimal("0.3333333333333333333333333333333333"), entry.cubicMetres)
        }
        assertNotEquals(data.total.cubicMetres, projected.entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.cubicMetres })
    }

    @Test fun mutationAndPeriodSnapshotsNeverRetainOldValues() {
        val readings = mutableListOf(reading("2026-10-01", "100"), reading("2026-10-04", "112"))
        val before = chartProjection(october, dashboard(readings, october))
        readings[1] = reading("2026-10-04", "106")
        val edited = chartProjection(october, dashboard(readings, october))
        assertEquals(BigDecimal("4"), before.entries.first().cubicMetres)
        assertEquals(BigDecimal("2"), edited.entries.first().cubicMetres)
        assertNotEquals(before, edited)
        assertEquals(before.entries.map { it.normalizedY }, edited.entries.map { it.normalizedY })
        val changedPeriod = chartProjection(year, dashboard(readings, year))
        assertNotEquals(edited, changedPeriod)
        assertEquals(9, changedPeriod.entries.single().offset)
        readings.removeAt(1)
        assertTrue(chartProjection(october, dashboard(readings, october)).entries.isEmpty())
        assertEquals(3, before.entries.size)
        readings += reading("2026-10-02", "100")
        assertTrue(chartProjection(october, dashboard(readings, october)).entries.single().isZero)
    }

    @Test fun projectionCopiesMutableBucketCollections() {
        val buckets = mutableListOf(bucket("2026-10-02", "1"))
        val data = Dashboard(ConsumptionAmount(BigDecimal.ONE, 1, 31, false), buckets)
        val first = chartProjection(october, data)
        buckets.clear()
        buckets += bucket("2026-10-31", "2")
        val second = chartProjection(october, data)
        assertEquals(1, first.entries.single().offset)
        assertEquals(BigDecimal.ONE, first.entries.single().cubicMetres)
        assertEquals(30, second.entries.single().offset)
        assertEquals(BigDecimal("2"), second.entries.single().cubicMetres)
    }

    @Test fun axisLabelsAreLocalizedAndExtremeMagnitudesStayCompact() {
        assertEquals("12,125", chartAmountText(BigDecimal("12.125"), Locale.GERMANY))
        assertEquals("0", chartAmountText(BigDecimal.ZERO, Locale.GERMANY))
        assertEquals("1,25E1000", chartAmountText(BigDecimal("1.25E+1000"), Locale.GERMANY))
        assertEquals("1,25E-1000", chartAmountText(BigDecimal("1.25E-1000"), Locale.GERMANY))
    }

    @Test fun zeroAxisMeasurementLabelsAreNeverEmpty() {
        val projected = chartProjection(october, snapshot(bucket("2026-10-02", "0")))
        // Vico measures minimum, midpoint, and maximum even when only one tick is drawn.
        listOf(0.0, 0.5, 1.0).forEach { value ->
            assertEquals("0", chartAxisText(projected, value, Locale.GERMANY))
        }
    }

    private fun replacementTracker(date: String, next: String? = "16", gap: Long = 3): TrackerData {
        val boundary = LocalDate.parse(date)
        val final = Reading("old-final", boundary, BigDecimal("1105"))
        val initial = Reading("new-initial", boundary, BigDecimal("10.0000"), "new")
        return TrackerData(
            listOf(Reading("old-first", boundary.minusDays(1), BigDecimal("1100")), final, initial) +
                listOfNotNull(next?.let { Reading("new-next", boundary.plusDays(gap), BigDecimal(it), "new") }),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", boundary)),
            listOf(MeterReplacement("replacement", boundary, ORIGINAL_INSTALLATION_ID, "new", final.id, initial.id)),
        )
    }

    private fun assertAuthoritativeBuckets(tracker: TrackerData, period: CalendarPeriod, expectedTotal: String) {
        val data = dashboard(tracker, period)
        val projected = chartProjection(period, data)
        assertEquals(BigDecimal(expectedTotal), data.total.cubicMetres)
        assertEquals(data.buckets.size, projected.entries.size)
        data.buckets.zip(projected.entries).forEach { (bucket, entry) ->
            assertEquals(bucket.range, entry.range)
            assertSame(bucket.amount.cubicMetres, entry.cubicMetres)
            assertEquals(bucket.amount.cubicMetres!!.signum() == 0, entry.isZero)
        }
    }

    @Test fun replacementBarsFollowCoveredBucketsAcrossWeekMonthAndYearBoundaries() {
        for ((type, date) in listOf(
            PeriodType.WEEK to "2026-11-01",
            PeriodType.MONTH to "2026-10-31",
            PeriodType.YEAR to "2026-12-31",
        )) {
            val tracker = replacementTracker(date)
            val boundary = LocalDate.parse(date)
            val oldPeriod = CalendarPeriod.containing(type, boundary)
            val newPeriod = CalendarPeriod.containing(type, boundary.plusDays(1))
            assertAuthoritativeBuckets(tracker, oldPeriod, "5")
            assertAuthoritativeBuckets(tracker, newPeriod, "6.0000")
            val old = chartProjection(oldPeriod, dashboard(tracker, oldPeriod))
            val new = chartProjection(newPeriod, dashboard(tracker, newPeriod))
            assertEquals(old.positionCount - 1, old.entries.single().offset)
            assertEquals(if (type == PeriodType.YEAR) listOf(0) else listOf(0, 1, 2), new.entries.map { it.offset })
            assertTrue(new.entries.all { !it.isZero })
        }
    }

    @Test fun agreedReplacementExampleProducesOnlyFiveAndThreeBarsAndTotalEight() {
        val source = replacementTracker("2026-10-28", "13.0000", 1)
        val data = dashboard(source, october)
        val chart = chartProjection(october, data)
        assertEquals(listOf(27, 28), chart.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3.0000")), chart.entries.map { it.cubicMetres })
        assertAuthoritativeBuckets(source, october, "8.0000")
        assertAuthoritativeBuckets(source, year, "8.0000")
    }

    @Test fun baselineOnlyHasNoNewMarkButEqualSubsequentReadingHasKnownZeroMark() {
        val source = replacementTracker("2026-10-28", null)
        val before = chartProjection(october, dashboard(source, october))
        assertEquals(listOf(27), before.entries.map { it.offset })
        val initial = source.readings.single { it.id == "new-initial" }
        val tracker = source.copy(readings = source.readings + initial.copy(id = "zero-next", date = initial.date.plusDays(1)))
        val after = chartProjection(october, dashboard(tracker, october))
        assertEquals(listOf(27, 28), after.entries.map { it.offset })
        assertTrue(after.entries.last().isZero)
        assertEquals(0.0, after.entries.last().normalizedY, 0.0)
        assertFalse(after.entries.first().isZero)
        assertAuthoritativeBuckets(tracker, october, "5.0000")
    }

    @Test fun replacementThirdsKeepIndependentExactTotalsAndOriginalBucketPrecision() {
        val source = replacementTracker("2026-10-04", "11.0000")
        val tracker = source.copy(readings = source.readings.map {
            when (it.id) {
                "old-first" -> it.copy(date = LocalDate.parse("2026-10-01"))
                "old-final" -> it.copy(cubicMetres = BigDecimal("1101.0000"))
                else -> it
            }
        })
        assertAuthoritativeBuckets(tracker, october, "2.0000")
        assertAuthoritativeBuckets(tracker, year, "2.0000")
        val data = dashboard(tracker, october)
        val projected = chartProjection(october, data)
        assertEquals((1..6).toList(), projected.entries.map { it.offset })
        assertTrue(projected.entries.all {
            it.cubicMetres == BigDecimal("0.3333333333333333333333333333333333")
        })
        assertNotEquals(data.total.cubicMetres, projected.entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.cubicMetres })
    }

    @Test fun boundaryCorrectionsAndOrdinaryDeletionRefreshMarksWithoutJoiningMeters() {
        val source = replacementTracker("2026-10-28", "13.0000", 1)
        val before = chartProjection(october, dashboard(source, october))
        val corrected = source.copy(readings = source.readings.map {
            when (it.id) {
                "old-final" -> it.copy(cubicMetres = BigDecimal("1106"))
                "new-initial" -> it.copy(cubicMetres = BigDecimal("11.0000"))
                else -> it
            }
        })
        val edited = chartProjection(october, dashboard(corrected, october))
        assertEquals(listOf(BigDecimal("6"), BigDecimal("2.0000")), edited.entries.map { it.cubicMetres })
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3.0000")), before.entries.map { it.cubicMetres })
        assertAuthoritativeBuckets(corrected, october, "8.0000")
        val deleted = corrected.copy(readings = corrected.readings.filterNot { it.id == "new-next" })
        assertEquals(listOf(27), chartProjection(october, dashboard(deleted, october)).entries.map { it.offset })
        assertAuthoritativeBuckets(deleted, october, "6")
        for (endpoint in listOf("old-final", "new-initial")) {
            assertThrows(IllegalArgumentException::class.java) {
                chartProjection(october, dashboard(source.copy(readings = source.readings.filterNot { it.id == endpoint }), october))
            }
        }
    }
}
