package com.trackgas.app.temperature

import com.trackgas.app.tracking.DateRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Splits uncovered calendar days into contiguous requests no longer than 366 days. */
internal fun missingRanges(range: DateRange, storedDates: Set<LocalDate>): List<DateRange> {
    val missingRuns = mutableListOf<DateRange>()
    var runStart: LocalDate? = null
    var runEnd: LocalDate? = null

    fun finishRun() {
        val start = runStart ?: return
        val end = requireNotNull(runEnd)
        var chunkStart = start
        while (true) {
            val remainingDays = ChronoUnit.DAYS.between(chunkStart, end)
            val chunkEnd = if (remainingDays >= MAX_CHUNK_DAYS) {
                chunkStart.plusDays(MAX_CHUNK_DAYS)
            } else {
                end
            }
            missingRuns += DateRange(chunkStart, chunkEnd)
            if (chunkEnd == end) break
            chunkStart = chunkEnd.plusDays(1)
        }
        runStart = null
        runEnd = null
    }

    var date = range.start
    while (true) {
        if (date in storedDates) {
            finishRun()
        } else {
            if (runStart == null) runStart = date
            runEnd = date
        }
        if (date == range.endInclusive) break
        date = date.plusDays(1)
    }
    finishRun()
    return missingRuns
}

private const val MAX_CHUNK_DAYS = 365L
