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

    @Test fun rejectsDuplicateDatesAndDecreasingCumulativeValues() {
        val duplicateDates = listOf(last, middle.copy(date = first.date), first)
        assertEquals(ReadingCollectionViolation.DUPLICATE_DATE, readingCollectionViolation(duplicateDates))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(duplicateDates) }

        val decreasing = listOf(last, middle.copy(cubicMetres = "99.0000".toBigDecimal()), first)
        assertEquals(ReadingCollectionViolation.DECREASING_VALUE, readingCollectionViolation(decreasing))
        assertThrows(IllegalArgumentException::class.java) { readingIntervals(decreasing) }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingInterval(first, middle.copy(cubicMetres = "99".toBigDecimal()))
        }
    }

    private fun replaced(): TrackerData {
        val baseline = Reading("initial", last.date, "0.0000".toBigDecimal(), "new")
        return TrackerData(
            listOf(last, first, baseline),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", last.date)),
            listOf(MeterReplacement("boundary", last.date, ORIGINAL_INSTALLATION_ID, "new", last.id, baseline.id)),
        )
    }

    @Test fun validReplacementAllowsLowerValueAndSameDateOnlyAcrossInstallations() {
        val tracker = replaced()
        assertNull(readingCollectionViolation(tracker))
        assertNull(readingCollectionViolation(tracker.copy(readings = tracker.readings +
            Reading("later", last.date.plusDays(2), "3".toBigDecimal(), "new"))))
        assertEquals(ReadingCollectionViolation.DUPLICATE_DATE, readingCollectionViolation(
            tracker.copy(readings = tracker.readings + last.copy(id = "duplicate")),
        ))
    }

    @Test fun invalidReferencesEndpointsChainsAndLifetimesAreRejected() {
        val tracker = replaced()
        val boundary = tracker.replacements.single()
        val cases = listOf(
            tracker.copy(installations = emptyList()),
            tracker.copy(installations = tracker.installations + MeterInstallation("disconnected")),
            tracker.copy(replacements = emptyList()),
            tracker.copy(replacements = listOf(boundary.copy(newInstallationId = ORIGINAL_INSTALLATION_ID))),
            tracker.copy(replacements = listOf(boundary.copy(finalReadingId = "missing"))),
            tracker.copy(replacements = listOf(boundary.copy(initialReadingId = first.id))),
            tracker.copy(replacements = listOf(boundary.copy(date = last.date.plusDays(1)))),
            tracker.copy(readings = tracker.readings + last.copy(id = "past-end", date = last.date.plusDays(1))),
            tracker.copy(readings = tracker.readings + Reading("before-start", first.date, "0".toBigDecimal(), "new")),
            tracker.copy(readings = tracker.readings + first.copy(id = "orphan", installationId = "missing")),
            tracker.copy(readings = tracker.readings + Reading("decrease", last.date.plusDays(1), "0".toBigDecimal(), "new"),
                replacements = tracker.replacements,
            ).let { data -> data.copy(readings = data.readings.map { if (it.id == "initial") it.copy(cubicMetres = "1".toBigDecimal()) else it }) },
        )
        cases.forEach { assertNotNull(readingCollectionViolation(it)) }
    }

    @Test fun chainHasExactlyOneTerminalAndStrictlyIncreasingBoundaries() {
        val tracker = replaced()
        val final = Reading("second-final", last.date.plusDays(1), "2".toBigDecimal(), "new")
        val initial = Reading("second-initial", final.date, "10".toBigDecimal(), "third")
        val second = MeterReplacement("second", final.date, "new", "third", final.id, initial.id)
        val chain = tracker.copy(
            readings = tracker.readings + final + initial,
            installations = tracker.installations + MeterInstallation("third", final.date),
            replacements = tracker.replacements + second,
        )
        assertNull(readingCollectionViolation(chain))
        assertEquals("third", chain.currentInstallation.id)
        assertNotNull(readingCollectionViolation(chain.copy(replacements = chain.replacements + second.copy(id = "fork"))))
        assertNotNull(readingCollectionViolation(chain.copy(replacements = listOf(chain.replacements[0], second.copy(date = last.date)))))
    }
}
