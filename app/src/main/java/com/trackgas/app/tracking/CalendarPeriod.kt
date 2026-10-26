package com.trackgas.app.tracking

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodType { WEEK, MONTH, YEAR }

/**
 * Calendar selection shared by navigation and consumption projection.
 *
 * [start] must be Monday for a week, the first day of a month, or January 1 for a
 * year. Construction enforces this alignment; [containing] and [current] normalize
 * selections, and [navigate] preserves their alignment.
 * Boundaries use calendar dates rather than fixed-duration timestamp arithmetic.
 */
data class CalendarPeriod(val type: PeriodType, val start: LocalDate) {
    init {
        require(when (type) {
            PeriodType.WEEK -> start.dayOfWeek == DayOfWeek.MONDAY
            PeriodType.MONTH -> start.dayOfMonth == 1
            PeriodType.YEAR -> start.dayOfYear == 1
        }) { "Period start must align with its calendar unit" }
    }

    val range: DateRange get() = DateRange(start, when (type) {
        PeriodType.WEEK -> start.plusDays(6)
        PeriodType.MONTH -> start.plusMonths(1).minusDays(1)
        PeriodType.YEAR -> start.plusYears(1).minusDays(1)
    })

    /** Moves by calendar units of the selected type; negative offsets move backwards. */
    fun navigate(offset: Long): CalendarPeriod = copy(start = when (type) {
        PeriodType.WEEK -> start.plusWeeks(offset)
        PeriodType.MONTH -> start.plusMonths(offset)
        PeriodType.YEAR -> start.plusYears(offset)
    })

    /**
     * Lists all candidate daily buckets for weeks/months or twelve monthly buckets for years.
     * [dashboard] subsequently omits buckets with no source-interval coverage.
     */
    fun buckets(): List<DateRange> = when (type) {
        PeriodType.WEEK, PeriodType.MONTH -> (0 until range.days).map {
            val day = start.plusDays(it)
            DateRange(day, day)
        }
        PeriodType.YEAR -> (0L..11L).map {
            val month = start.plusMonths(it)
            DateRange(month, month.plusMonths(1).minusDays(1))
        }
    }

    companion object {
        /** Selects the period containing today in [clock]'s zone, independent of prior navigation. */
        fun current(type: PeriodType, clock: Clock = Clock.systemDefaultZone()): CalendarPeriod =
            containing(type, LocalDate.now(clock))

        /** Normalizes a date to its Monday, first day of the month, or January 1. */
        fun containing(type: PeriodType, date: LocalDate): CalendarPeriod =
            CalendarPeriod(type, when (type) {
                PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                PeriodType.MONTH -> date.withDayOfMonth(1)
                PeriodType.YEAR -> date.withDayOfYear(1)
            })
    }
}
