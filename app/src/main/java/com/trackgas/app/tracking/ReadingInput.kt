package com.trackgas.app.tracking

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    val time: String
    val now: Instant
    data class Add(
        val date: LocalDate?, override val meter: ParsedMeter, override val today: LocalDate,
        override val time: String, val zone: ZoneId, override val now: Instant,
    ) : SaveReadingIntent
    data class Edit(
        val id: String,
        override val meter: ParsedMeter,
        override val today: LocalDate,
        val note: String? = null,
        override val time: String,
        override val now: Instant,
    ) : SaveReadingIntent
}

internal fun observationInstant(date: LocalDate, time: String, zone: ZoneId): Instant? {
    if (!Regex("[0-9]{2}:[0-9]{2}").matches(time)) return null
    val local = try { date.atTime(LocalTime.parse(time)) } catch (_: java.time.DateTimeException) { return null }
    val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
    return local.toInstant(offset)
}
