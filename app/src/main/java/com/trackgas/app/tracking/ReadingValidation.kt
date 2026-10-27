package com.trackgas.app.tracking

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/** Domain error codes mapped to localized field messages by [ReadingForm]. */
enum class ReadingError { DATE_REQUIRED, FUTURE_DATE, DUPLICATE_DATE, VALUE_REQUIRED, INVALID_NUMBER, NEGATIVE_VALUE, BELOW_PREVIOUS, ABOVE_NEXT, MISSING_READING }

/** Independent date/value errors; both fields may be invalid in the same submission. */
data class ReadingErrors(
    val date: ReadingError? = null,
    val value: ReadingError? = null,
) {
    val isEmpty: Boolean get() = date == null && value == null
}

/** A candidate is supplied only when all field and chronological checks pass. */
data class ReadingValidation(val candidate: Reading?, val errors: ReadingErrors)

/** Shared by live date selection and final save; an edit cannot conflict with itself. */
fun validateReadingDate(readings: List<Reading>, id: String?, date: LocalDate?, today: LocalDate): ReadingError? =
    when {
        date == null -> ReadingError.DATE_REQUIRED
        date > today -> ReadingError.FUTURE_DATE
        readings.any { it.id != id && it.date == date } -> ReadingError.DUPLICATE_DATE
        else -> null
    }

/**
 * Validates an Add/Edit draft without modifying [readings].
 *
 * Accepts localized decimal digits, the locale's decimal separator, and an ordinary
 * decimal point. Parsing consumes the entire trimmed input and rejects grouping,
 * exponents, malformed numbers, and negative values without floating-point conversion.
 *
 * The candidate must be dated no later than [today], have a unique date, and fit
 * between both chronological neighbours' cumulative values. Equal values are valid.
 * An existing reading matching [id] is excluded so an edit cannot conflict with itself.
 *
 * @param id New identity for Add, or the original reading's identity for Edit.
 * @return The validated candidate, or field errors with no candidate on failure.
 */
fun validateReading(
    readings: List<Reading>,
    id: String,
    date: LocalDate?,
    input: String,
    today: LocalDate,
    locale: Locale,
): ReadingValidation {
    val others = readings.filterNot { it.id == id }
    val dateError = validateReadingDate(readings, id, date, today)
    val text = input.trim()
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = text.map { character ->
        when {
            character == separator -> '.'
            character.isDigit() -> '0' + Character.digit(character, 10)
            else -> character
        }
    }.joinToString("")
    val value = if (Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(normalized)) normalized.toBigDecimalOrNull() else null
    val valueError = when {
        text.isEmpty() -> ReadingError.VALUE_REQUIRED
        value == null -> ReadingError.INVALID_NUMBER
        value.signum() < 0 -> ReadingError.NEGATIVE_VALUE
        date != null && others.filter { it.date < date }.maxByOrNull { it.date }?.let { value < it.cubicMetres } == true ->
            ReadingError.BELOW_PREVIOUS
        date != null && others.filter { it.date > date }.minByOrNull { it.date }?.let { value > it.cubicMetres } == true ->
            ReadingError.ABOVE_NEXT
        else -> null
    }
    val errors = ReadingErrors(dateError, valueError)
    return ReadingValidation(
        if (errors.isEmpty && date != null && value != null) Reading(id, date, value) else null,
        errors,
    )
}
