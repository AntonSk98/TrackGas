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
}
