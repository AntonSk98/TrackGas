package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ReadingValidationTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val readings = listOf(
        Reading("a", today.minusDays(4), BigDecimal("100")),
        Reading("b", today.minusDays(1), BigDecimal("120")),
    )
    private fun validate(input: String, date: LocalDate? = today.minusDays(2), id: String = "new", locale: Locale = Locale.US) =
        if (id == "new") evaluateSave(readings, SaveReadingIntent.Add(date, parseMeter(input, locale), today), id)
        else evaluateSave(readings, SaveReadingIntent.Edit(id, parseMeter(input, locale), today))

    @Test fun rejectsInvalidValuesAndDatesWithoutMutation() {
        val original = readings.toList()
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.VALUE_REQUIRED)), validate(" "))
        for (input in listOf("NaN", "Infinity", "1e2", "100x", "1,000.2", "100.2.3", ".5")) {
            assertEquals(input, ReadingValidation.Rejected(ReadingErrors(value = ReadingError.INVALID_NUMBER)), validate(input))
        }
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.NEGATIVE_VALUE)), validate("-1"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(date = ReadingError.DATE_REQUIRED)), validate("110", null))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(date = ReadingError.FUTURE_DATE)), validate("120", today.plusDays(1)))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(date = ReadingError.DUPLICATE_DATE)), validate("120", today.minusDays(1)))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), validate("99"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.ABOVE_NEXT)), validate("125"))
        assertEquals(original, readings)
    }

    @Test fun acceptsLocaleDecimalsEqualValuesAndUnchangedEdit() {
        val expected = ReadingValidation.Accepted(Reading("new", today.minusDays(2), BigDecimal("110.125")))
        assertEquals(expected, validate("110,125", locale = Locale.GERMANY))
        assertEquals(expected, validate("110.125", locale = Locale.GERMANY))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.INVALID_NUMBER)), validate("110,125"))
        assertTrue(validate("100") is ReadingValidation.Accepted)
        assertTrue(validate("120") is ReadingValidation.Accepted)
        assertEquals(ReadingValidation.Accepted(readings[1]), validate("120", today.minusDays(1), "b"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), validate("99", today, "b"))
        assertEquals(ReadingValidation.Rejected(ReadingErrors(value = ReadingError.ABOVE_NEXT)), validate("125", today.minusDays(5), "a"))
    }

    @Test fun localizedEditTextCanBeParsedWithoutPrecisionLoss() {
        val value = BigDecimal("110.123456789")
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-EG"))) {
            val text = numberText(value, locale, raw = true)
            assertEquals(ReadingValidation.Accepted(Reading("new", today.minusDays(2), value)), validate(text, locale = locale))
        }
    }

    @Test fun liveDateValidationMatchesSaveWithoutRequiringAMeterValue() {
        for (date in listOf(null, today.plusDays(1), today.minusDays(1), today.minusDays(2))) {
            assertEquals(
                (validate("", date) as ReadingValidation.Rejected).errors.date,
                validateReadingDate(readings, null, date, today),
            )
        }
        assertEquals(ReadingError.DUPLICATE_DATE, validateReadingDate(readings, null, readings[1].date, today))
        assertNull(validateReadingDate(readings, null, today, today))
    }

    @Test fun liveDateValidationExcludesOnlyTheReadingBeingEdited() {
        assertNull(validateReadingDate(readings, "b", readings[1].date, today))
        assertEquals(ReadingError.DUPLICATE_DATE, validateReadingDate(readings, "b", readings[0].date, today))
        assertNull(validateReadingDate(readings, "b", today, today))
        assertEquals(ReadingError.DATE_REQUIRED, validateReadingDate(readings, "b", null, today))
        assertEquals(ReadingError.FUTURE_DATE, validateReadingDate(readings, "b", today.plusDays(1), today))
    }

    @Test fun liveDateFeedbackTracksChangesToTheAuthoritativeReadings() {
        val date = readings[1].date
        assertNull(validateReadingDate(emptyList(), null, date, today))
        assertEquals(ReadingError.DUPLICATE_DATE, validateReadingDate(readings, null, date, today))
        assertNull(validateReadingDate(readings.filterNot { it.date == date }, null, date, today))
    }

    @Test fun validationVariantsCannotPairAcceptedCandidatesWithRejectionErrors() {
        val accepted = ReadingValidation.Accepted(readings[0])
        assertSame(readings[0], accepted.candidate)
        assertThrows(IllegalArgumentException::class.java) { ReadingValidation.Rejected(ReadingErrors()) }
        val rejected = ReadingValidation.Rejected(ReadingErrors(value = ReadingError.INVALID_NUMBER))
        assertThrows(IllegalArgumentException::class.java) { rejected.copy(errors = ReadingErrors()) }
    }
}
