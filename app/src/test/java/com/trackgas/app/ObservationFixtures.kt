package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Explicit test-only observations; production never infers times for historical dates. */
fun Reading(
    id: String,
    date: LocalDate,
    cubicMetres: BigDecimal,
    installationId: String = ORIGINAL_INSTALLATION_ID,
    note: String? = null,
    observedAt: Instant = date.atTime(8, 0).toInstant(ZoneOffset.UTC),
    zone: ZoneId = ZoneOffset.UTC,
): com.trackgas.app.tracking.Reading =
    com.trackgas.app.tracking.Reading(id, date, cubicMetres, installationId, note, observedAt, zone)

fun testAdd(date: LocalDate?, meter: ParsedMeter, today: LocalDate) =
    SaveReadingIntent.Add(date, meter, today, "08:00", ZoneOffset.UTC,
        today.atTime(23, 59).toInstant(ZoneOffset.UTC))

fun testEdit(id: String, meter: ParsedMeter, today: LocalDate, note: String? = null) =
    SaveReadingIntent.Edit(id, meter, today, note, "08:00",
        today.atTime(23, 59).toInstant(ZoneOffset.UTC))

fun TrackerViewModel.save(date: LocalDate?, input: String, locale: Locale, note: String? = null) =
    save(date, input, locale, note, "08:00", ZoneOffset.UTC)

fun testCsvRow(
    installationId: String, date: LocalDate, cubicMetres: BigDecimal, physicalRow: Int, note: String? = null,
) = CsvReadingRow(installationId, date, cubicMetres, physicalRow, note,
    date.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

fun testAddTarget(date: LocalDate) = ReadingTarget.Add(date, date.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
