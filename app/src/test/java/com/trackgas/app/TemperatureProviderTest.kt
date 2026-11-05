package com.trackgas.app

import com.trackgas.app.temperature.OpenMeteoTemperatureProvider
import com.trackgas.app.temperature.missingRanges
import com.trackgas.app.tracking.DateRange
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers archive-response parsing and missing-day request batching. */
class TemperatureProviderTest {
    @Test
    fun missingRangesSplitAtStoredDaysAndKeepOnlyMissingIntervals() {
        val start = date("2026-10-01")
        val range = DateRange(start, date("2026-10-10"))

        assertEquals(
            listOf(
                DateRange(date("2026-10-02"), date("2026-10-04")),
                DateRange(date("2026-10-07"), date("2026-10-09")),
            ),
            missingRanges(range, setOf(date("2026-10-01"), date("2026-10-05"), date("2026-10-06"), date("2026-10-10"))),
        )
    }

    @Test
    fun largeBackfillForManyReadingsCoversEveryDayInBoundedChunks() {
        val readings = generateSequence(date("2024-01-01")) { previous -> previous.plusDays(6) }
            .take(150)
            .toList()
        assertEquals(150, readings.size)
        val range = DateRange(readings.first(), readings.last())

        val chunks = missingRanges(range, emptySet())
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.days <= 366 })
        assertEquals(range.days, chunks.sumOf { it.days })
        assertEquals(range.start, chunks.first().start)
        assertEquals(range.endInclusive, chunks.last().endInclusive)
        assertTrue(chunks.zipWithNext().all { (left, right) -> left.endInclusive.plusDays(1) == right.start })
    }

    @Test
    fun fullyStoredRangeHasNoRequests() {
        val dates = (0L..4L).map { date("2026-10-01").plusDays(it) }.toSet()
        assertEquals(emptyList<DateRange>(), missingRanges(DateRange(dates.first(), dates.last()), dates))
    }

    @Test
    fun parserSkipsNullDaysAndKeepsDecimalValues() {
        val result = OpenMeteoTemperatureProvider.parseResponse(
            """{"daily":{"time":["2026-10-01","2026-10-02","2026-10-03"],"temperature_2m_mean":[4.0,null,8.25]}}""",
        )

        assertEquals(
            mapOf(date("2026-10-01") to BigDecimal("4.0"), date("2026-10-03") to BigDecimal("8.25")),
            result,
        )
    }

    @Test
    fun parserRejectsMalformedOrMisalignedData() {
        assertThrows(Exception::class.java) { OpenMeteoTemperatureProvider.parseResponse("not json") }
        assertThrows(IllegalArgumentException::class.java) {
            OpenMeteoTemperatureProvider.parseResponse(
                """{"daily":{"time":["2026-10-01"],"temperature_2m_mean":[]}}""",
            )
        }
    }

    private fun date(value: String) = LocalDate.parse(value)
}
