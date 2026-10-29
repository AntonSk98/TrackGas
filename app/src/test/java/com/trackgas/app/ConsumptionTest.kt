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

    private fun replacementTracker(baseline: String = "0", next: String? = "3"): TrackerData {
        val final = reading(28, "1105")
        val initial = reading(28, baseline).copy(id = "new-initial", installationId = "new")
        return TrackerData(
            listOf(reading(27, "1100"), final, initial) +
                listOfNotNull(next?.let { reading(29, it).copy(id = "new-next", installationId = "new") }),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", initial.date)),
            listOf(MeterReplacement("replacement", final.date, ORIGINAL_INSTALLATION_ID, "new", final.id, initial.id)),
        )
    }

    @Test fun replacementExampleAttributesFiveAndThreeToTheCorrectDaysWithExactTotalEight() {
        for ((baseline, next) in listOf("0" to "3", "10.0000" to "13.0000", "1200.0000" to "1203.0000")) {
            val source = replacementTracker(baseline, next)
            val shuffled = source.copy(readings = source.readings.reversed(), installations = source.installations.reversed())
            val intervals = readingIntervals(shuffled)
            assertEquals(2, intervals.size)
            assertEquals(listOf(28, 29), intervals.map { it.lastCoveredDate.dayOfMonth })
            assertTrue(intervals.all { it.earlier.installationId == it.later.installationId })
            for ((day, expected) in listOf(28 to "5", 29 to "3")) {
                val date = reading(day, "0").date
                val amount = allocate(intervals, DateRange(date, date))
                assertEquals(0, BigDecimal(expected).compareTo(amount.cubicMetres))
                assertEquals(1L, amount.coveredDays)
                assertFalse(amount.estimatedDistribution)
            }
            val total = allocate(intervals, DateRange(reading(27, "0").date, reading(29, "0").date))
            assertEquals(0, BigDecimal("8").compareTo(total.cubicMetres))
            assertEquals(2L, total.coveredDays)
            assertEquals(readingIntervals(source.readings), intervals)
            assertEquals(source.readings.reversed(), shuffled.readings)
            if (baseline.contains('.')) assertEquals(BigDecimal("3.0000"), intervals.last().cubicMetres)
        }
    }

    @Test fun newBaselineAloneNeverCreatesAnIntervalOrKnownZeroDay() {
        for (baseline in listOf("0", "12.5000")) {
            val intervals = readingIntervals(replacementTracker(baseline, null))
            assertEquals(1, intervals.size)
            assertEquals(BigDecimal("5"), intervals.single().cubicMetres)
            val after = reading(29, "0").date
            assertNull(allocate(intervals, DateRange(after, after)).cubicMetres)
        }
        assertTrue(readingIntervals(TrackerData()).isEmpty())
        assertTrue(readingIntervals(TrackerData(listOf(reading(27, "1100")))).isEmpty())
    }

    @Test fun multipleReplacementsMergeChronologicallyWithoutCrossMeterPairs() {
        val first = replacementTracker("10", "13")
        val secondFinal = first.readings.single { it.id == "new-next" }
        val secondInitial = reading(29, "100.0000").copy(id = "third-initial", installationId = "third")
        val last = reading(31, "104.1250").copy(id = "third-last", installationId = "third")
        val tracker = first.copy(
            readings = (first.readings + secondInitial + last).reversed(),
            installations = first.installations + MeterInstallation("third", secondInitial.date),
            replacements = (first.replacements + MeterReplacement(
                "second", secondFinal.date, "new", "third", secondFinal.id, secondInitial.id,
            )).reversed(),
        )
        val intervals = readingIntervals(tracker)
        assertEquals(listOf(28, 29, 31), intervals.map { it.lastCoveredDate.dayOfMonth })
        val total = allocate(intervals, DateRange(reading(27, "0").date, last.date))
        assertEquals(BigDecimal("12.1250"), total.cubicMetres)
        assertEquals(4L, total.coveredDays)
        assertTrue(total.estimatedDistribution)
        for (day in 30..31) {
            val date = reading(day, "0").date
            assertEquals(BigDecimal("2.0625"), allocate(intervals, DateRange(date, date)).cubicMetres)
        }
    }

    @Test fun mixedPairsOverlappingInstallationsAndInvalidTrackerStructureAreRejected() {
        val tracker = replacementTracker()
        val final = tracker.readings.single { it.id == "id-28" }
        val initial = tracker.readings.single { it.id == "new-initial" }
        assertThrows(IllegalArgumentException::class.java) { ReadingInterval(final, initial) }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingInterval(final, initial.copy(date = final.date.plusDays(1), cubicMetres = BigDecimal("1200")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readingIntervals(listOf(reading(27, "1100"), final, initial.copy(date = reading(27, "0").date),
                reading(29, "3").copy(id = "overlap", installationId = "new")))
        }
        for (invalid in listOf(
            tracker.copy(replacements = emptyList()),
            tracker.copy(installations = listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID))),
            tracker.copy(readings = tracker.readings.filterNot { it.id == initial.id }),
            tracker.copy(readings = tracker.readings + reading(30, "1106")),
        )) {
            assertThrows(IllegalArgumentException::class.java) { readingIntervals(invalid) }
            assertThrows(IllegalArgumentException::class.java) {
                dashboard(invalid, CalendarPeriod(PeriodType.MONTH, LocalDate.of(2026, 1, 1)))
            }
        }
    }
}
