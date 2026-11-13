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
        testAdd(date, parseMeter(value, Locale.US), at)

    @Test fun missingEditHasPrecedenceAndIndependentAddErrorsAreCombined() {
        assertEquals(
            ReadingValidation.Rejected(ReadingErrors(value = ReadingError.MISSING_READING)),
            evaluateSave(listOf(first), testEdit("missing", ParsedMeter.Invalid, today.minusYears(1))),
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
        val edited = (evaluateSave(rows, testEdit(last.id, parseMeter("108.1250", Locale.US), today)) as ReadingValidation.Accepted).candidate
        assertEquals(last.id, edited.id)
        assertEquals(last.date, edited.date)
        assertEquals("108.1250", edited.cubicMetres.toString())
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), evaluateSave(rows, testEdit(last.id, parseMeter("99", Locale.US), today)))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.ABOVE_NEXT)), evaluateSave(rows, testEdit(first.id, parseMeter("125", Locale.US), today)))
        assertEquals(rows, listOf(last, first))
    }

    @Test fun submissionDateRatherThanOpeningDateControlsFutureValidation() {
        val date = LocalDate.parse("2026-11-01")
        assertEquals(ReadingValidation.Rejected(ReadingErrors(date = ReadingError.FUTURE_DATE)), evaluateSave(emptyList(), add(date, "100", date.minusDays(1)), "new"))
        assertEquals(
            ReadingValidation.Accepted(
                Reading("new", date, "100".toBigDecimal(), "new"),
                MeterInstallation("new", date),
            ),
            evaluateSave(emptyList(), add(date, "100", date), "new"),
        )
    }

    @Test fun editSavesAndClearsOptionalNotesWithoutChangingReadingIdentityOrDate() {
        val saved = last.copy(note = "Old context")
        val tracker = TrackerData(listOf(saved, first))
        val updated = (evaluateSave(
            tracker,
            testEdit(saved.id, parseMeter("112.5", Locale.US), today, "Boiler service"),
        ) as ReadingValidation.Accepted).candidate
        assertEquals(saved.id, updated.id)
        assertEquals(saved.date, updated.date)
        assertEquals("Boiler service", updated.note)

        val cleared = (evaluateSave(
            tracker,
            testEdit(saved.id, parseMeter("112.5", Locale.US), today, "  "),
        ) as ReadingValidation.Accepted).candidate
        assertNull(cleared.note)
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
        val edit = (evaluateSave(inserted, testEdit(middle.id, parseMeter("1008", Locale.US), today)) as ReadingValidation.Accepted).candidate
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

    @Test fun addResolvesInstallationFromCurrentChainWhileEditPreservesMembership() {
        val final = last.copy(date = today.minusDays(1), observedAt = today.minusDays(1).atTime(8, 0).toInstant(java.time.ZoneOffset.UTC))
        val next = Reading("next", today, "3".toBigDecimal(), "new")
        val following = Reading("following", today.plusDays(2), "5".toBigDecimal(), "new")
        val tracker = TrackerData(
            listOf(first, final, next, following),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", final.date)),
        )
        val historical = (evaluateSave(tracker, add(first.date.plusDays(1), "105"), "historical") as ReadingValidation.Accepted).candidate
        assertEquals(ORIGINAL_INSTALLATION_ID, historical.installationId)
        val replacementDay = (evaluateSave(tracker, add(final.date, "0"), "new-meter-reading") as ReadingValidation.Accepted).candidate
        assertEquals("new", replacementDay.installationId)
        val edit = (evaluateSave(tracker, testEdit(next.id, parseMeter("2.0000", Locale.US), today)) as ReadingValidation.Accepted).candidate
        assertEquals(next.copy(cubicMetres = "2.0000".toBigDecimal()), edit)
        assertEquals(ReadingError.ABOVE_NEXT,
            (evaluateSave(tracker, testEdit(next.id, parseMeter("6", Locale.US), today)) as ReadingValidation.Rejected).errors.value)
        assertEquals(ReadingError.BELOW_PREVIOUS,
            (evaluateSave(tracker, add(today.plusDays(1), "2", today.plusDays(1)), "lower") as ReadingValidation.Rejected).errors.value)
        assertEquals(ReadingError.ABOVE_NEXT,
            (evaluateSave(tracker, add(first.date.plusDays(1), "200"), "high") as ReadingValidation.Rejected).errors.value)
    }
}
