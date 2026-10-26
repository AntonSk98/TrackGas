package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ConsumptionTest {
    private fun reading(day: Int, value: String) =
        Reading("id-$day", LocalDate.of(2026, 10, day), BigDecimal(value))

    @Test fun emptyAndBaselineHaveNoIntervals() {
        assertTrue(readingIntervals(emptyList()).isEmpty())
        assertTrue(readingIntervals(listOf(reading(1, "1000"))).isEmpty())
    }

    @Test fun chronologicalIntervalsRetainSourcesAndExcludeFirstDate() {
        val earlier = reading(1, "1000")
        val later = reading(2, "1004.125")
        val interval = readingIntervals(listOf(later, earlier)).single()
        assertEquals(earlier, interval.earlier)
        assertEquals(later, interval.later)
        assertEquals(later.date, interval.firstCoveredDate)
        assertEquals(later.date, interval.lastCoveredDate)
        assertEquals(1L, interval.days)
        assertEquals(BigDecimal("4.125"), interval.cubicMetres)
        assertFalse(interval.estimatedDistribution)
        assertEquals(BigDecimal.ZERO, readingIntervals(listOf(reading(1, "100"), reading(2, "100"))).single().cubicMetres)
    }

    @Test fun middleReadingReplacesOldIntervalWithoutChangingSources() {
        val readings = listOf(reading(4, "1012"), reading(1, "1000"), reading(2, "1003"))
        val original = readings.toList()
        val intervals = readingIntervals(readings)
        assertEquals(listOf(BigDecimal("3"), BigDecimal("9")), intervals.map { it.cubicMetres })
        assertEquals(readings[2].id, intervals[0].later.id)
        assertEquals(intervals[0].later.id, intervals[1].earlier.id)
        assertEquals(original, readings)
    }

    @Test fun skippedDaysHaveUniformEstimatedAllocationButNoOuterCoverage() {
        val readings = listOf(reading(1, "1000"), reading(4, "1012"))
        val intervals = readingIntervals(readings)
        for (day in 2..4) {
            val result = allocate(intervals, DateRange(reading(day, "0").date, reading(day, "0").date))
            assertEquals(0, BigDecimal("4").compareTo(result.cubicMetres))
            assertEquals(1L, result.coveredDays)
            assertTrue(result.estimatedDistribution)
        }
        for (day in listOf(1, 5)) {
            assertNull(allocate(intervals, DateRange(reading(day, "0").date, reading(day, "0").date)).cubicMetres)
        }
        assertEquals(2, readings.size)
    }

    @Test fun thirdsRetainExactWholeIntervalAndLongGapsNeedNoDailyExpansion() {
        val thirds = readingIntervals(listOf(reading(1, "0"), reading(4, "1")))
        assertEquals(BigDecimal.ONE, allocate(thirds, DateRange(reading(2, "0").date, reading(4, "0").date)).cubicMetres)
        val daily = allocate(thirds, DateRange(reading(2, "0").date, reading(2, "0").date)).cubicMetres!!
        assertEquals("0.333", daily.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString())
        val start = Reading("start", LocalDate.of(1900, 1, 1), BigDecimal.ZERO)
        val end = Reading("end", LocalDate.of(2026, 1, 1), BigDecimal("1000"))
        val result = allocate(readingIntervals(listOf(start, end)), DateRange(start.date.plusDays(1), end.date))
        assertEquals(BigDecimal("1000"), result.cubicMetres)
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(start.date, end.date), result.coveredDays)
    }

    @Test fun invalidIntervalEndpointsAndSourceCollectionsAreRejected() {
        val first = reading(1, "100")
        val last = reading(4, "112")
        for (invalid in listOf(
            first.copy(id = last.id),
            first.copy(date = last.date),
            first.copy(date = last.date.plusDays(1)),
            first.copy(cubicMetres = BigDecimal("113")),
        )) {
            assertThrows(IllegalArgumentException::class.java) { ReadingInterval(invalid, last) }
            assertThrows(IllegalArgumentException::class.java) { ReadingInterval(first, last).copy(earlier = invalid) }
        }
        for (invalid in listOf(
            listOf(first, last.copy(id = first.id)),
            listOf(first, last, reading(6, "120").copy(id = first.id)),
            listOf(first, last.copy(date = first.date)),
            listOf(first, last.copy(cubicMetres = BigDecimal("99"))),
        )) {
            assertThrows(IllegalArgumentException::class.java) { readingIntervals(invalid) }
        }
    }

    @Test fun allocationRejectsOverlappingAndUnorderedIntervalsButAllowsUnknownGaps() {
        val first = ReadingInterval(reading(1, "100"), reading(3, "102"))
        val adjacent = ReadingInterval(reading(3, "102"), reading(4, "103"))
        val separated = ReadingInterval(reading(5, "104"), reading(6, "105"))
        val overlap = ReadingInterval(reading(2, "101"), reading(4, "103"))
        val range = DateRange(reading(1, "0").date, reading(6, "0").date)
        for (invalid in listOf(listOf(first, first), listOf(first, overlap), listOf(adjacent, first))) {
            assertThrows(IllegalArgumentException::class.java) { allocate(invalid, range) }
            assertThrows(IllegalArgumentException::class.java) {
                allocate(invalid, DateRange(range.start.minusYears(1), range.endInclusive.minusYears(1)))
            }
        }
        val contiguous = allocate(listOf(first, adjacent), range)
        assertEquals(3L, contiguous.coveredDays)
        assertEquals(BigDecimal("3"), contiguous.cubicMetres)
        val withGap = allocate(listOf(first, separated), range)
        assertEquals(3L, withGap.coveredDays)
        assertEquals(BigDecimal("3"), withGap.cubicMetres)
    }

    @Test fun amountMetadataAndRangesCannotRepresentContradictoryCoverage() {
        val knownZero = ConsumptionAmount(BigDecimal.ZERO, 1, 7, false)
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(totalDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = -1) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = 8) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(cubicMetres = null) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(cubicMetres = BigDecimal("-1")) }
        assertThrows(IllegalArgumentException::class.java) { ConsumptionAmount(null, 0, 7, true) }
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01"))
        }
        assertNull(ConsumptionAmount(null, 0, 7, false).cubicMetres)
    }
}
