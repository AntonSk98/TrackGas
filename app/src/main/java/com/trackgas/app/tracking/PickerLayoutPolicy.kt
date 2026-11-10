package com.trackgas.app.tracking

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal enum class TimePickerPresentation { VERTICAL, HORIZONTAL, INPUT }

/** Dimensions are the surface's inner dp bounds, after content padding, not the window. */
internal fun timePickerPresentation(
    widthDp: Float,
    heightDp: Float,
    fontScale: Float,
): TimePickerPresentation = when {
    // Material's clock selectors have fixed dimensions; enlarged digits must not be clipped.
    fontScale > 1.3f -> TimePickerPresentation.INPUT
    widthDp >= 256f && heightDp >= 384f -> TimePickerPresentation.VERTICAL
    widthDp >= 496f && heightDp >= 256f -> TimePickerPresentation.HORIZONTAL
    else -> TimePickerPresentation.INPUT
}

private val timePickerInputFormat =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

internal fun parseTimePickerInput(input: String): LocalTime? = try {
    LocalTime.parse(input, timePickerInputFormat)
} catch (_: DateTimeParseException) {
    null
}

internal fun formatTimePickerInput(time: LocalTime): String = time.format(timePickerInputFormat)

/** Recomposition/recreation alone must not replace an incomplete or invalid draft. */
internal fun timePickerInputAfterSelection(
    input: String,
    previousSelection: String,
    selection: LocalTime,
): String {
    val selectedInput = formatTimePickerInput(selection)
    return if (selectedInput == previousSelection) input else selectedInput
}
