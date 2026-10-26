package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarPeriodTest {
    private fun current(type: PeriodType, date: String, zone: String = "Europe/Berlin"): CalendarPeriod {
        val day = LocalDate.parse(date)
        val zoneId = ZoneId.of(zone)
        return CalendarPeriod.current(type, Clock.fixed(day.atTime(12, 0).atZone(zoneId).toInstant(), zoneId))
    }

    @Test fun mondaySundayAndYearTransitions() {
        val monday = current(PeriodType.WEEK, "2026-10-26")
        assertEquals(monday, current(PeriodType.WEEK, "2026-11-01"))
        assertEquals(LocalDate.parse("2026-11-01"), monday.range.endInclusive)
        assertEquals(LocalDate.parse("2026-10-19"), monday.navigate(-1).start)
        assertEquals(LocalDate.parse("2026-11-02"), monday.navigate(1).start)
        assertEquals(LocalDate.parse("2025-12-29"), current(PeriodType.WEEK, "2026-01-01").start)
        assertEquals(LocalDate.parse("2027-01-01"), current(PeriodType.YEAR, "2026-10-26").navigate(1).start)
    }

    @Test fun calendarMonthsLeapYearsAndBuckets() {
        assertEquals(28L, current(PeriodType.MONTH, "2026-02-10").range.days)
        assertEquals(29, current(PeriodType.MONTH, "2024-02-10").buckets().size)
        assertEquals(31, current(PeriodType.MONTH, "2026-01-31").buckets().size)
        assertEquals(LocalDate.parse("2026-02-01"), current(PeriodType.MONTH, "2026-01-31").navigate(1).start)
        assertEquals(366L, current(PeriodType.YEAR, "2024-12-31").range.days)
        assertEquals(12, current(PeriodType.YEAR, "2026-12-31").buckets().size)
        assertEquals(LocalDate.parse("2026-10-01"), current(PeriodType.MONTH, "2026-10-26").start)
    }

    @Test fun daylightSavingStillUsesSevenCalendarDays() {
        for (date in listOf("2026-03-29", "2026-10-25")) {
            val week = current(PeriodType.WEEK, date)
            assertEquals(7L, week.range.days)
            assertEquals(7, week.buckets().size)
        }
    }

    @Test fun constructorsAndCopiesRequireAlignedStarts() {
        val date = LocalDate.parse("2026-10-27")
        for (type in PeriodType.entries) {
            assertThrows(IllegalArgumentException::class.java) { CalendarPeriod(type, date) }
            val normalized = CalendarPeriod.containing(type, date)
            assertTrue(date in normalized.range.start..normalized.range.endInclusive)
            assertEquals(normalized, normalized.navigate(-1).navigate(1))
            assertThrows(IllegalArgumentException::class.java) { normalized.copy(start = date) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarPeriod(PeriodType.YEAR, LocalDate.parse("2026-10-01"))
        }
    }
}
