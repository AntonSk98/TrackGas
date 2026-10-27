package com.trackgas.app.tracking

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Full date context for history and the reading form. */
internal fun dateText(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

internal fun bucketText(
    date: LocalDate,
    type: PeriodType,
    locale: Locale,
    padDay: Boolean = false,
): String = when (type) {
    PeriodType.WEEK -> date.format(DateTimeFormatter.ofPattern("EEE", locale))
    PeriodType.MONTH -> NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = if (padDay) 2 else 1
    }.format(date.dayOfMonth)
    PeriodType.YEAR -> date.format(DateTimeFormatter.ofPattern("LLL", locale))
}

/**
 * Formats decimals without grouping so raw meter values can be reused in the editor.
 *
 * [raw] preserves entered fractional precision; calculated usage is rounded HALF_UP to
 * at most three decimal places for display only. The underlying value is never modified.
 */
internal fun numberText(value: BigDecimal, locale: Locale, raw: Boolean = false): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = if (raw) maxOf(0, value.scale()) else 3
        if (raw) minimumFractionDigits = maxOf(0, value.scale())
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
