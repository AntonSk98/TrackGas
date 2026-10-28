package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Realistic rule/projection assertions relocated out of the ViewModel's former policy fake. */
class ReadingPolicyTest {
    private val today = LocalDate.parse("2026-10-26")
    private val first = Reading("first", today.minusDays(25), "100".toBigDecimal())
    private val last = Reading("last", today.minusDays(22), "112".toBigDecimal())
    private fun add(date: LocalDate?, value: String, at: LocalDate = today) =
        SaveReadingIntent.Add(date, parseMeter(value, Locale.US), at)

    @Test fun missingEditHasPrecedenceAndIndependentAddErrorsAreCombined() {
        assertEquals(
            ReadingValidation.Rejected(ReadingErrors(value = ReadingError.MISSING_READING)),
            evaluateSave(listOf(first), SaveReadingIntent.Edit("missing", ParsedMeter.Invalid, today.minusYears(1))),
        )
        assertEquals(ReadingValidation.Rejected(ReadingErrors(ReadingError.DATE_REQUIRED, ReadingError.INVALID_NUMBER)), evaluateSave(emptyList(), add(null, "bad"), "new"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(ReadingError.FUTURE_DATE, ReadingError.NEGATIVE_VALUE)), evaluateSave(emptyList(), add(today.plusDays(1), "-1"), "new"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(ReadingError.DUPLICATE_DATE, ReadingError.VALUE_REQUIRED)), evaluateSave(listOf(first), add(first.date, ""), "new"))
    }

    @Test fun bothNeighboursEqualitySelfExclusionAndImmutableEditIdentityDate() {
        val rows = listOf(last, first)
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), evaluateSave(rows, add(first.date.plusDays(1), "99"), "middle"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.ABOVE_NEXT)), evaluateSave(rows, add(first.date.plusDays(1), "120"), "middle"))
        for (value in listOf("100", "112")) assertTrue(evaluateSave(rows, add(first.date.plusDays(1), value), "middle") is ReadingValidation.Accepted)
        val edited = (evaluateSave(rows, SaveReadingIntent.Edit(last.id, parseMeter("108.1250", Locale.US), today)) as ReadingValidation.Accepted).candidate
        assertEquals(last.id, edited.id)
        assertEquals(last.date, edited.date)
        assertEquals("108.1250", edited.cubicMetres.toString())
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), evaluateSave(rows, SaveReadingIntent.Edit(last.id, parseMeter("99", Locale.US), today)))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.ABOVE_NEXT)), evaluateSave(rows, SaveReadingIntent.Edit(first.id, parseMeter("125", Locale.US), today)))
        assertEquals(rows, listOf(last, first))
    }

    @Test fun submissionDateRatherThanOpeningDateControlsFutureValidation() {
        val date = LocalDate.parse("2026-11-01")
        assertEquals(ReadingValidation.Rejected(ReadingErrors(date = ReadingError.FUTURE_DATE)), evaluateSave(emptyList(), add(date, "100", date.minusDays(1)), "new"))
        assertEquals(ReadingValidation.Accepted(Reading("new", date, "100".toBigDecimal())), evaluateSave(emptyList(), add(date, "100", date), "new"))
    }

    @Test fun chronologicalInsertEditAndDeletionKeepExactIntervalProjection() {
        val baseline = first.copy(cubicMetres = "1000".toBigDecimal())
        val end = last.copy(cubicMetres = "1012".toBigDecimal())
        val period = CalendarPeriod.containing(PeriodType.MONTH, today)
        assertTrue(dashboard(listOf(baseline), period).buckets.isEmpty())
        assertEquals("12".toBigDecimal(), dashboard(listOf(end, baseline), period).total.cubicMetres)
        val middle = (evaluateSave(listOf(end, baseline), add(baseline.date.plusDays(1), "1003"), "middle") as ReadingValidation.Accepted).candidate
        val inserted = listOf(end, middle, baseline)
        val projection = dashboard(inserted, period)
        assertEquals(0, "3".toBigDecimal().compareTo(projection.buckets[0].amount.cubicMetres))
        assertEquals(0, "4.5".toBigDecimal().compareTo(projection.buckets[1].amount.cubicMetres))
        val edit = (evaluateSave(inserted, SaveReadingIntent.Edit(middle.id, parseMeter("1008", Locale.US), today)) as ReadingValidation.Accepted).candidate
        assertEquals(middle.id, edit.id)
        val changed = dashboard(listOf(end, edit, baseline), period)
        assertEquals(0, "8".toBigDecimal().compareTo(changed.buckets[0].amount.cubicMetres))
        assertEquals(0, "2".toBigDecimal().compareTo(changed.buckets[2].amount.cubicMetres))
        val deleted = inserted.filterNot { it.id == middle.id }
        assertEquals(listOf(end, baseline), deleted)
        assertEquals(0, "4".toBigDecimal().compareTo(dashboard(deleted, period).buckets[0].amount.cubicMetres))
        assertNull(dashboard(listOf(baseline), period).total.cubicMetres)
        assertTrue(dashboard(listOf(baseline), period).buckets.isEmpty())
        assertNull(dashboard(emptyList(), period).total.cubicMetres)
    }
}
