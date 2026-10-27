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
        validateReading(readings, id, date, input, today, locale)

    @Test fun rejectsInvalidValuesAndDatesWithoutMutation() {
        val original = readings.toList()
        assertEquals(ReadingError.VALUE_REQUIRED, validate(" ").errors.value)
        for (input in listOf("NaN", "Infinity", "1e2", "100x", "1,000.2", "100.2.3", ".5")) {
            assertEquals(input, ReadingError.INVALID_NUMBER, validate(input).errors.value)
        }
        assertEquals(ReadingError.NEGATIVE_VALUE, validate("-1").errors.value)
        assertEquals(ReadingError.DATE_REQUIRED, validate("110", null).errors.date)
        assertEquals(ReadingError.FUTURE_DATE, validate("120", today.plusDays(1)).errors.date)
        assertEquals(ReadingError.DUPLICATE_DATE, validate("120", today.minusDays(1)).errors.date)
        assertEquals(ReadingError.BELOW_PREVIOUS, validate("99").errors.value)
        assertEquals(ReadingError.ABOVE_NEXT, validate("125").errors.value)
        assertEquals(original, readings)
    }

    @Test fun acceptsLocaleDecimalsEqualValuesAndUnchangedEdit() {
        assertEquals(BigDecimal("110.125"), validate("110,125", locale = Locale.GERMANY).candidate?.cubicMetres)
        assertEquals(BigDecimal("110.125"), validate("110.125", locale = Locale.GERMANY).candidate?.cubicMetres)
        assertEquals(ReadingError.INVALID_NUMBER, validate("110,125").errors.value)
        assertTrue(validate("100").errors.isEmpty)
        assertTrue(validate("120").errors.isEmpty)
        assertEquals("b", validate("120", today.minusDays(1), "b").candidate?.id)
        assertEquals(ReadingError.BELOW_PREVIOUS, validate("99", today, "b").errors.value)
        assertEquals(ReadingError.ABOVE_NEXT, validate("125", today.minusDays(5), "a").errors.value)
    }

    @Test fun localizedEditTextCanBeParsedWithoutPrecisionLoss() {
        val value = BigDecimal("110.123456789")
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-EG"))) {
            val text = numberText(value, locale, raw = true)
            assertEquals(value, validate(text, locale = locale).candidate?.cubicMetres)
        }
    }

    @Test fun liveDateValidationMatchesSaveWithoutRequiringAMeterValue() {
        for (date in listOf(null, today.plusDays(1), today.minusDays(1), today.minusDays(2))) {
            assertEquals(
                validate("", date).errors.date,
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
}
