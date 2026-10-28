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
}
