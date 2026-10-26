package com.trackgas.app.tracking

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Shared locale-aware date label for dashboard rows, history, and the reading form. */
internal fun dateText(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

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
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
