package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DashboardTest {
    private fun reading(date: String, value: String) = Reading(date, LocalDate.parse(date), BigDecimal(value))
    private fun period(type: PeriodType, start: String) = CalendarPeriod(type, LocalDate.parse(start))

    @Test fun noCoverageHasNeitherSeriesNorTotal() {
        val week = period(PeriodType.WEEK, "2026-10-26")
        for (readings in listOf(emptyList(), listOf(reading("2026-10-01", "100")), listOf(reading("2026-10-01", "100"), reading("2026-10-02", "104")))) {
            val data = dashboard(readings, week)
            assertNull(data.total.cubicMetres)
            assertTrue(data.buckets.isEmpty())
        }
    }

    @Test fun knownZeroIsDifferentFromUnknownAndCoverageIsPartial() {
        val data = dashboard(
            listOf(reading("2026-10-01", "100"), reading("2026-10-02", "100")),
            period(PeriodType.WEEK, "2026-09-28"),
        )
        assertEquals(BigDecimal.ZERO, data.total.cubicMetres)
        assertEquals(1L, data.total.coveredDays)
        assertFalse(data.total.fullyCovered)
        assertFalse(data.total.estimatedDistribution)
        assertEquals(7L, data.total.totalDays)
        assertEquals(LocalDate.parse("2026-10-02"), data.buckets.single().range.start)
        assertEquals(BigDecimal.ZERO, data.buckets.single().amount.cubicMetres)
    }

    @Test fun crossMonthUsesOutsideReadingsAndYearTotalRetainsExactDelta() {
        val readings = listOf(reading("2026-01-30", "1000"), reading("2026-03-01", "1030"))
        val february = dashboard(readings, period(PeriodType.MONTH, "2026-02-01"))
        assertEquals(0, BigDecimal("28").compareTo(february.total.cubicMetres))
        assertTrue(february.total.fullyCovered)
        assertTrue(february.total.estimatedDistribution)
        val year = dashboard(readings, period(PeriodType.YEAR, "2026-01-01"))
        assertEquals(BigDecimal("30"), year.total.cubicMetres)
        assertEquals(30L, year.total.coveredDays)
        assertEquals(listOf(1, 2, 3), year.buckets.map { it.range.start.monthValue })
        assertEquals(0, BigDecimal("28").compareTo(year.buckets[1].amount.cubicMetres))
        assertEquals(0, year.total.cubicMetres!!.compareTo(year.buckets.mapNotNull { it.amount.cubicMetres }.fold(BigDecimal.ZERO, BigDecimal::add)))
    }

    @Test fun completeThirdsAggregateDirectlyInsteadOfSummingRoundedRows() {
        val readings = listOf(reading("2026-10-01", "0"), reading("2026-10-04", "1"))
        val data = dashboard(readings, period(PeriodType.MONTH, "2026-10-01"))
        assertEquals(BigDecimal.ONE, data.total.cubicMetres)
        assertEquals(3L, data.total.coveredDays)
        assertEquals(3, data.buckets.size)
        assertEquals(31L, data.total.totalDays)
    }

    @Test fun compactViewsRetainEstimatedDaysAndFullPeriodCoverageWithoutMutatingReadings() {
        val readings = listOf(reading("2026-10-04", "112"), reading("2026-10-01", "100"))
        val original = readings.toList()
        for (type in listOf(PeriodType.WEEK, PeriodType.MONTH)) {
            val selected = period(type, if (type == PeriodType.WEEK) "2026-09-28" else "2026-10-01")
            val data = dashboard(readings, selected)
            assertEquals(listOf(2, 3, 4), data.buckets.map { it.range.start.dayOfMonth })
            assertEquals(BigDecimal("12"), data.total.cubicMetres)
            assertEquals(3L, data.total.coveredDays)
            assertEquals(if (type == PeriodType.WEEK) 7L else 31L, data.total.totalDays)
            assertFalse(data.total.fullyCovered)
            assertTrue(data.total.estimatedDistribution)
            data.buckets.forEach {
                assertEquals(0, BigDecimal("4").compareTo(it.amount.cubicMetres))
                assertTrue(it.amount.estimatedDistribution)
            }
        }
        val year = dashboard(readings, period(PeriodType.YEAR, "2026-01-01"))
        assertEquals(LocalDate.parse("2026-10-01"), year.buckets.single().range.start)
        assertEquals(BigDecimal("12"), year.buckets.single().amount.cubicMetres)
        assertEquals(3L, year.buckets.single().amount.coveredDays)
        assertEquals(31L, year.buckets.single().amount.totalDays)
        assertEquals(365L, year.total.totalDays)
        assertEquals(original, readings)
    }

    @Test fun knownZeroRemainsVisibleInMonthlyAndYearlyViews() {
        val readings = listOf(reading("2026-10-01", "100"), reading("2026-10-02", "100"))
        for (selected in listOf(period(PeriodType.MONTH, "2026-10-01"), period(PeriodType.YEAR, "2026-01-01"))) {
            val data = dashboard(readings, selected)
            assertEquals(BigDecimal.ZERO, data.buckets.single().amount.cubicMetres)
            assertEquals(1L, data.total.coveredDays)
            assertFalse(data.total.estimatedDistribution)
        }
    }

    private fun replacementTracker(date: String, baseline: String = "10", next: String? = "16", gap: Long = 3): TrackerData {
        val boundary = LocalDate.parse(date)
        val first = Reading("old-first", boundary.minusDays(1), BigDecimal("1100"))
        val final = Reading("old-final", boundary, BigDecimal("1105"))
        val initial = Reading("new-initial", boundary, BigDecimal(baseline), "new")
        return TrackerData(
            listOf(first, final, initial) +
                listOfNotNull(next?.let { Reading("new-next", boundary.plusDays(gap), BigDecimal(it), "new") }),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", boundary)),
            listOf(MeterReplacement("replacement", boundary, ORIGINAL_INSTALLATION_ID, "new", final.id, initial.id)),
        )
    }

    @Test fun replacementAcrossWeekMonthAndYearBoundariesKeepsBothSidesIndependent() {
        for ((type, boundaryText) in listOf(
            PeriodType.WEEK to "2026-11-01",
            PeriodType.MONTH to "2026-10-31",
            PeriodType.YEAR to "2026-12-31",
        )) {
            val boundary = LocalDate.parse(boundaryText)
            val tracker = replacementTracker(boundaryText)
            val old = dashboard(tracker, CalendarPeriod.containing(type, boundary))
            val new = dashboard(tracker, CalendarPeriod.containing(type, boundary.plusDays(1)))
            assertEquals(BigDecimal("5"), old.total.cubicMetres)
            assertEquals(BigDecimal("6"), new.total.cubicMetres)
            assertEquals(1L, old.total.coveredDays)
            assertEquals(3L, new.total.coveredDays)
            assertFalse(old.total.estimatedDistribution)
            assertTrue(new.total.estimatedDistribution)
            assertFalse(old.total.fullyCovered)
            assertFalse(new.total.fullyCovered)
            assertEquals(boundary, if (type == PeriodType.YEAR) old.buckets.single().range.endInclusive else old.buckets.single().range.start)
            if (type != PeriodType.YEAR) {
                assertEquals((1L..3L).map(boundary::plusDays), new.buckets.map { it.range.start })
                assertTrue(new.buckets.all { it.amount.cubicMetres!!.compareTo(BigDecimal("2")) == 0 })
            } else {
                assertEquals(1, new.buckets.single().range.start.monthValue)
                assertEquals(BigDecimal("6"), new.buckets.single().amount.cubicMetres)
            }
        }
    }

    @Test fun replacementWithSkippedDaysRetainsExactWholeIntervalsInsteadOfSummingRoundedBuckets() {
        val base = replacementTracker("2026-10-04", "10.0000", "11.0000")
        val tracker = base.copy(readings = base.readings.map {
            when (it.id) {
                "old-first" -> it.copy(date = LocalDate.parse("2026-10-01"))
                "old-final" -> it.copy(cubicMetres = BigDecimal("1101.0000"))
                else -> it
            }
        })
        val month = dashboard(tracker, period(PeriodType.MONTH, "2026-10-01"))
        assertEquals(BigDecimal("2.0000"), month.total.cubicMetres)
        assertEquals(6L, month.total.coveredDays)
        assertEquals((2..7).toList(), month.buckets.map { it.range.start.dayOfMonth })
        assertTrue(month.buckets.all { it.amount.estimatedDistribution })
        assertEquals(BigDecimal("0.3333333333333333333333333333333333"), month.buckets.first().amount.cubicMetres)
        assertNotEquals(month.total.cubicMetres, month.buckets.fold(BigDecimal.ZERO) { sum, bucket -> sum + bucket.amount.cubicMetres!! })
        val year = dashboard(tracker, period(PeriodType.YEAR, "2026-01-01"))
        assertEquals(month.total.cubicMetres, year.total.cubicMetres)
        assertEquals(month.total.cubicMetres, year.buckets.single().amount.cubicMetres)
    }

    @Test fun replacementBaselineIsUnknownUntilLaterReadingEstablishesEvenZeroCoverage() {
        val source = replacementTracker("2026-10-28", "12.5000", null)
        val selected = period(PeriodType.WEEK, "2026-10-26")
        val before = dashboard(source, selected)
        assertEquals(listOf(28), before.buckets.map { it.range.start.dayOfMonth })
        assertEquals(BigDecimal("5"), before.total.cubicMetres)
        val initial = source.readings.single { it.id == "new-initial" }
        val after = dashboard(source.copy(readings = source.readings + initial.copy(
            id = "zero-next", date = initial.date.plusDays(1),
        )), selected)
        assertEquals(listOf(28, 29), after.buckets.map { it.range.start.dayOfMonth })
        assertEquals(0, after.buckets.last().amount.cubicMetres!!.signum())
        assertEquals(2L, after.total.coveredDays)
        assertFalse(after.total.estimatedDistribution)
        assertEquals(0, before.total.cubicMetres!!.compareTo(after.total.cubicMetres))
    }

    @Test fun endpointValueCorrectionsAndOrdinaryDeletionsRecalculateOnlyTheirOwnInstallation() {
        val source = replacementTracker("2026-10-28", "0", "3", 1)
        val selected = period(PeriodType.MONTH, "2026-10-01")
        fun project(readings: List<Reading>) = dashboard(source.copy(readings = readings), selected)
        val before = project(source.readings)
        val corrected = source.readings.map {
            when (it.id) {
                "old-final" -> it.copy(cubicMetres = BigDecimal("1106"))
                "new-initial" -> it.copy(cubicMetres = BigDecimal.ONE)
                else -> it
            }
        }
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3")), before.buckets.map { it.amount.cubicMetres })
        assertEquals(listOf(BigDecimal("6"), BigDecimal("2")), project(corrected).buckets.map { it.amount.cubicMetres })
        val deletedNext = project(corrected.filterNot { it.id == "new-next" })
        assertEquals(BigDecimal("6"), deletedNext.total.cubicMetres)
        assertEquals(listOf(28), deletedNext.buckets.map { it.range.start.dayOfMonth })
        assertEquals(BigDecimal("3"), project(source.readings.filterNot { it.id == "old-first" }).total.cubicMetres)
        for (endpoint in listOf("old-final", "new-initial")) {
            assertThrows(IllegalArgumentException::class.java) {
                project(source.readings.filterNot { it.id == endpoint })
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            project(source.readings.map { if (it.id == "new-initial") it.copy(cubicMetres = BigDecimal("4")) else it })
        }
    }
}
