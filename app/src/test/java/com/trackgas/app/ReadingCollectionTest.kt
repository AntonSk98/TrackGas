package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingCollectionTest {
    private val first = Reading("first", LocalDate.parse("2026-10-01"), "100.0100".toBigDecimal())
    private val middle = Reading("middle", first.date.plusDays(1), "100.01000".toBigDecimal())
    private val last = Reading("last", first.date.plusDays(2), "120.1250".toBigDecimal())

    @Test fun acceptsEmptyBaselineAndUnorderedEqualValuesWithoutChangingInputOrScale() {
        assertNull(readingCollectionViolation(emptyList()))
        assertNull(readingCollectionViolation(listOf(first)))
        assertTrue(readingIntervals(emptyList()).isEmpty())
        assertTrue(readingIntervals(listOf(first)).isEmpty())

        val readings = listOf(last, first, middle)
        val before = readings.toList()
        assertNull(readingCollectionViolation(readings))
        val intervals = readingIntervals(readings)
        assertEquals(before, readings)
        assertSame(first, intervals[0].earlier)
        assertSame(middle, intervals[0].later)
        assertSame(last, intervals[1].later)
        assertEquals(0, intervals[0].cubicMetres.signum())
        assertEquals(listOf(4, 4, 5), readings.map { it.cubicMetres.scale() })
    }

    @Test fun rejectsDuplicateIdentitiesEvenWhenNotChronologicalNeighbours() {
        val readings = listOf(last.copy(id = first.id), middle, first)
        assertEquals(ReadingCollectionViolation.DUPLICATE_IDENTITY, readingCollectionViolation(readings))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(readings) }
    }

    @Test fun rejectsDuplicateDatesAndDecreasingCumulativeValuesWithinOneInstallation() {
        val duplicateDates = listOf(last, middle.copy(date = first.date, observedAt = first.observedAt), first)
        assertEquals(ReadingCollectionViolation.DUPLICATE_DATE, readingCollectionViolation(duplicateDates))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(duplicateDates) }

        val decreasing = listOf(last, middle.copy(cubicMetres = "99.0000".toBigDecimal()), first)
        assertEquals(ReadingCollectionViolation.DECREASING_VALUE, readingCollectionViolation(decreasing))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(decreasing) }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingInterval(first, middle.copy(cubicMetres = "99".toBigDecimal()))
        }
    }

    @Test fun independentInstallationsAllowLowerValuesAndNeverCreateCrossMeterIntervals() {
        val oldLast = last
        val newFirst = Reading("new-first", oldLast.date.plusDays(1), "0.0000".toBigDecimal(), "new")
        val newLast = Reading("new-last", oldLast.date.plusDays(2), "3".toBigDecimal(), "new")
        val tracker = TrackerData(
            listOf(newLast, oldLast, newFirst, first),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", newFirst.date)),
        )
        assertNull(readingCollectionViolation(tracker))
        val intervals = readingIntervals(tracker)
        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.earlier.installationId == it.later.installationId })
        assertEquals(listOf(oldLast.id, newLast.id), intervals.map { it.later.id })
        assertEquals("new", tracker.currentInstallation.id)
    }

    @Test fun rejectsMissingInstallationsAndOverlappingMeterCoverage() {
        val readings = listOf(first, last)
        val tracker = TrackerData(
            readings = readings,
            installations = listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID)),
        )
        assertNull(readingCollectionViolation(tracker))
        val unknownMeter = tracker.copy(readings = readings + first.copy(id = "orphan", installationId = "missing"))
        assertEquals(ReadingCollectionViolation.INVALID_INSTALLATION, readingCollectionViolation(unknownMeter))

        val overlapping = TrackerData(
            readings = readings + listOf(
                Reading("other-first", first.date, "5".toBigDecimal(), "other"),
                Reading("other-last", last.date, "8".toBigDecimal(), "other"),
            ),
            installations = listOf(
                MeterInstallation(ORIGINAL_INSTALLATION_ID),
                MeterInstallation("other", first.date),
            ),
        )
        assertEquals(ReadingCollectionViolation.OVERLAPPING_COVERAGE, readingCollectionViolation(overlapping))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(overlapping) }
    }

    @Test fun newEmptyInstallationBecomesCurrentWithoutAReadingButLaterDatedDataWins() {
        val newMeter = MeterInstallation("new", last.date.plusDays(1))
        val tracker = TrackerData(listOf(first, last), listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), newMeter))
        assertEquals(newMeter, tracker.currentInstallation)
        val futureReading = last.copy(id = "future", date = newMeter.startDate!!.plusDays(1),
            observedAt = last.observedAt.plusSeconds(2 * 86400))
        assertEquals(ORIGINAL_INSTALLATION_ID, tracker.copy(readings = tracker.readings + futureReading).currentInstallation.id)
    }
}
