package com.trackgas.app.tracking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Encodes a calendar date as UTC midnight for Material's date picker.
 *
 * The picker uses UTC dates, not local instants; using the device timezone here
 * would shift the selected day in some timezones.
 */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Decodes the picker's UTC date without converting it through the device timezone. */
fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
