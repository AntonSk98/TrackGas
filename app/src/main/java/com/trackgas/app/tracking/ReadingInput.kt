package com.trackgas.app.tracking

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

sealed interface ParsedMeter {
    data class Value(val cubicMetres: BigDecimal) : ParsedMeter
    data object Required : ParsedMeter
    data object Invalid : ParsedMeter
}

/** Consumes all trimmed input without floating-point conversion or scale normalization. */
fun parseMeter(input: String, locale: Locale): ParsedMeter {
    val text = input.trim()
    if (text.isEmpty()) return ParsedMeter.Required
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = text.map { character ->
        when {
            character == separator -> '.'
            character.isDigit() -> '0' + Character.digit(character, 10)
            else -> character
        }
    }.joinToString("")
    val value = if (Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(normalized)) normalized.toBigDecimalOrNull() else null
    return value?.let(ParsedMeter::Value) ?: ParsedMeter.Invalid
}

sealed interface SaveReadingIntent {
    val meter: ParsedMeter
    val today: LocalDate
    data class Add(val date: LocalDate?, override val meter: ParsedMeter, override val today: LocalDate) : SaveReadingIntent
    data class Edit(val id: String, override val meter: ParsedMeter, override val today: LocalDate) : SaveReadingIntent
}
