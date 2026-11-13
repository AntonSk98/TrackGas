package com.trackgas.app

import com.trackgas.app.tracking.ReadingCsvParseResult
import com.trackgas.app.tracking.CsvImportErrorCode
import com.trackgas.app.tracking.CsvImportField
import com.trackgas.app.tracking.CsvReadingValidation
import com.trackgas.app.tracking.CsvImportCandidateValidation
import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.CsvReadingRow
import com.trackgas.app.tracking.parseReadingCsv
import com.trackgas.app.tracking.validateReadingCsv
import com.trackgas.app.tracking.validateCsvImportCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCsvParserTest {
    @Test fun offsetZoneRoundTripPreservesOverlapAndLocalDateAcrossUtcBoundary() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val header = "meter_id;timestamp;timezone;meter_value;note\n"
        for ((timestamp, zone) in listOf(
            "2026-10-25T02:30+02:00" to "Europe/Berlin",
            "2026-10-25T02:30+01:00" to "Europe/Berlin",
            "2026-11-06T00:15+09:00" to "Asia/Tokyo",
        )) {
            val csv = "$header$meter;$timestamp;$zone;123.4500;note\n"
            val previous = java.util.TimeZone.getDefault()
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"))
                val parsed = validateReadingCsv(csv, setOf(meter)) as CsvReadingValidation.Valid
                val row = parsed.rows.single()
                assertEquals(java.time.OffsetDateTime.parse(timestamp).toInstant(), row.observedAt)
                assertEquals(java.time.OffsetDateTime.parse(timestamp).toLocalDate(), row.date)
                val tracker = (validateCsvImportCandidate(
                    TrackerData(installations = listOf(MeterInstallation(meter))), parsed.rows,
                ) { "new" } as CsvImportCandidateValidation.Valid).tracker
                assertEquals(csv, com.trackgas.app.tracking.readingCsv(tracker).toString(Charsets.UTF_8))
            } finally { java.util.TimeZone.setDefault(previous) }
        }
    }

    @Test fun invalidOffsetsGapsSecondsFutureAndBothLegacyHeadersAreRejected() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val header = "meter_id;timestamp;timezone;meter_value;note\n"
        for (timestamp in listOf("2026-03-29T02:30+01:00", "2026-10-25T02:30+03:00",
            "2026-11-06T08:15:00+01:00", "2026-11-06T08:15", "2999-01-01T08:15+01:00")) {
            val error = (validateReadingCsv("$header$meter;$timestamp;Europe/Berlin;1;\n", setOf(meter)) as CsvReadingValidation.Invalid).error
            assertEquals(CsvImportErrorCode.CSV_TIMESTAMP_INVALID, error.code)
            assertEquals(CsvImportField.TIMESTAMP, error.field)
        }
        for (legacy in listOf("meter_id;date;meter_value\n", "meter_id;date;meter_value;note\n")) {
            assertEquals(CsvImportErrorCode.CSV_HEADER_INVALID,
                (validateReadingCsv(legacy, setOf(meter)) as CsvReadingValidation.Invalid).error.code)
        }
    }

    @Test fun importRetainsOmittedHistoryAndLastLocalDateRowWithAllFields() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val omitted = "22222222-2222-4222-8222-222222222222"
        val unknown = "33333333-3333-4333-8333-333333333333"
        val csv = "meter_id;timestamp;timezone;meter_value;note\n" +
            "$meter;2026-11-06T00:15+09:00;Asia/Tokyo;1;first\n" +
            "$meter;2026-11-06T20:15+09:00;Asia/Tokyo;2.00;last\n" +
            "$unknown;2026-11-07T08:00Z;UTC;3;new meter\n"
        val parsed = (validateReadingCsv(csv, setOf(meter, omitted)) as CsvReadingValidation.Valid).rows
        assertEquals(2, parsed.size)
        val importedMeterRow = parsed.single { it.installationId == meter }
        assertEquals("last", importedMeterRow.note)
        assertEquals(java.time.Instant.parse("2026-11-06T11:15:00Z"), importedMeterRow.observedAt)
        val retained = Reading("retained", java.time.LocalDate.parse("2026-10-01"), "10".toBigDecimal(), omitted)
        val base = TrackerData(listOf(retained), listOf(MeterInstallation(meter), MeterInstallation(omitted)))
        val imported = (validateCsvImportCandidate(base, parsed) { "new" } as CsvImportCandidateValidation.Valid).tracker
        assertEquals(retained, imported.readings.first { it.installationId == omitted })
        assertEquals("2.00", imported.readings.first { it.installationId == meter }.cubicMetres.toPlainString())
        assertEquals(unknown, imported.installations.single { it.id == unknown }.id)
        assertEquals("new meter", imported.readings.single { it.installationId == unknown }.note)
        assertEquals(listOf(retained), base.readings)
        assertEquals(listOf(MeterInstallation(meter), MeterInstallation(omitted)), base.installations)
    }
    @Test fun parsesCurrentFiveColumnExample() {
        val text =
            "meter_id;timestamp;timezone;meter_value;note\n" +
                "11111111-1111-4111-8111-111111111111;2026-10-28T08:00Z;UTC;1.20;\n"
        val result = parseReadingCsv(text)
        assertTrue(result is ReadingCsvParseResult.Parsed)
        val records = (result as ReadingCsvParseResult.Parsed).records
        assertTrue(records.size > 1)
        assertEquals(1, records.first().physicalRow)
        assertEquals(listOf("meter_id", "timestamp", "timezone", "meter_value", "note"), records.first().fields)
        assertEquals(5, records.first().fields.size)
        assertTrue(records.last().physicalRow > 1)
        assertEquals(records.first().fields.size, records.last().fields.size)
    }

    @Test fun parsesWriterQuotedFieldsAndTracksPhysicalRows() {
        val text = "meter_id;date;meter_value;note\r\nm;2026-10-28;1.20;\"quoted;\"\"note\r\ny\"\r\n"
        val result = parseReadingCsv(text) as ReadingCsvParseResult.Parsed
        assertEquals(2, result.records.size)
        assertEquals(1, result.records[0].physicalRow)
        assertEquals(2, result.records[1].physicalRow)
        assertEquals(listOf("m", "2026-10-28", "1.20", "quoted;\"note\r\ny"), result.records[1].fields)
    }

    @Test fun rejectsMalformedQuotingAndAcceptsFinalRecordWithoutNewline() {
        assertEquals(ReadingCsvParseResult.Malformed(2), parseReadingCsv("a;b\n\"bad;field\n"))
        assertEquals(
            listOf("a", "b"),
            (parseReadingCsv("a;b") as ReadingCsvParseResult.Parsed).records.single().fields,
        )
    }

    @Test fun reportsStableValidationCodesWithRowAndFieldContext() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val unknown = "22222222-2222-4222-8222-222222222222"
        val known = setOf(meter)
        fun error(csv: String) = (validateReadingCsv(csv, known) as CsvReadingValidation.Invalid).error

        assertEquals(CsvImportErrorCode.CSV_HEADER_INVALID, error("meter;date;value\n").code)
        val header = "meter_id;timestamp;timezone;meter_value;note\n"
        assertEquals(CsvImportErrorCode.CSV_ROW_INVALID, error(header + "short\n").code)
        assertEquals(CsvImportErrorCode.CSV_METER_ID_INVALID, error(header + "not-a-uuid;2026-10-28T08:00Z;UTC;1;\n").code)
        val unknownRow = (validateReadingCsv(
            header + "$unknown;2026-10-28T08:00Z;UTC;1;\n",
            known,
        ) as CsvReadingValidation.Valid).rows.single()
        assertEquals(unknown, unknownRow.installationId)
        assertEquals(CsvImportErrorCode.CSV_TIMESTAMP_INVALID, error(header + "$meter;not-a-date;UTC;1;\n").code)
        assertEquals(CsvImportErrorCode.CSV_TIMEZONE_INVALID, error(header + "$meter;2026-10-28T08:00Z;Not/AZone;1;\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_INVALID, error(header + "$meter;2026-10-28T08:00Z;UTC;one;\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_NEGATIVE, error(header + "$meter;2026-10-28T08:00Z;UTC;-1;\n").code)
        val contextual = error(header + "$meter;2026-10-28T08:00Z;UTC;1;\n$meter;bad;UTC;2;\n")
        assertEquals(3, contextual.row)
        assertEquals(CsvImportField.TIMESTAMP, contextual.field)
    }

    @Test fun rejectsThreeColumnFilesAndTreatsEmptyFourColumnNotesAsAbsent() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val legacy = validateReadingCsv(
            "meter_id;date;meter_value\n$meter;2026-10-28;1.2500\n",
            setOf(meter),
        ) as CsvReadingValidation.Invalid
        assertEquals(CsvImportErrorCode.CSV_HEADER_INVALID, legacy.error.code)

        val current = validateReadingCsv(
            "meter_id;timestamp;timezone;meter_value;note\n$meter;2026-10-28T08:00Z;UTC;1.2500;\n",
            setOf(meter),
        ) as CsvReadingValidation.Valid
        assertNull(current.rows.single().note)
        assertEquals("1.2500", current.rows.single().cubicMetres.toPlainString())
    }

    @Test fun importsQuotedNotesWithDelimitersQuotesAndLineBreaks() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val note = "checked; pressure \"ok\"\r\nnext"
        val csv = "meter_id;timestamp;timezone;meter_value;note\n" +
            "$meter;2026-10-28T08:00Z;UTC;1.2500;\"checked; pressure \"\"ok\"\"\r\nnext\"\n"

        val parsed = validateReadingCsv(csv, setOf(meter)) as CsvReadingValidation.Valid
        assertEquals(note, parsed.rows.single().note)
    }

    @Test fun deduplicatesByMeterAndDateKeepingTheLastRowAndPreservingOtherMeters() {
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val csv = "meter_id;timestamp;timezone;meter_value;note\n" +
            "$first;2026-10-28T08:00Z;UTC;1.0;\n" +
            "$second;2026-10-28T08:00Z;UTC;5;\n" +
            "$first;2026-10-28T20:00Z;UTC;2.00;\n"
        val rows = (validateReadingCsv(csv, setOf(first, second)) as CsvReadingValidation.Valid).rows
        assertEquals(listOf(first, second), rows.map { it.installationId })
        assertEquals("2.00", rows.first().cubicMetres.toPlainString())
        assertEquals("5", rows.last().cubicMetres.toPlainString())
    }

    @Test fun candidateValidationRejectsDecreasingMeterHistoryWithStableCode() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val base = TrackerData(
            readings = listOf(Reading("existing", java.time.LocalDate.parse("2026-10-01"), "50".toBigDecimal(), meter)),
            installations = listOf(MeterInstallation(meter)),
        )
        val csvRows = listOf(
            testCsvRow(meter, java.time.LocalDate.parse("2026-10-01"), "100".toBigDecimal(), 2),
            testCsvRow(meter, java.time.LocalDate.parse("2026-10-02"), "90".toBigDecimal(), 3),
        )
        var generated = 0
        val result = validateCsvImportCandidate(base, csvRows) { "new-${generated++}" }
        assertTrue(result is CsvImportCandidateValidation.Invalid)
        val error = (result as CsvImportCandidateValidation.Invalid).error
        assertEquals(CsvImportErrorCode.CSV_METER_HISTORY_INVALID, error.code)
        assertEquals(3, error.row)
        assertEquals(CsvImportField.METER_VALUE, error.field)
    }

    @Test fun importCandidateKeepsOptionalNoteOnItsReading() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val base = TrackerData(installations = listOf(MeterInstallation(meter)))
        val row = testCsvRow(meter, java.time.LocalDate.parse("2026-10-28"), "1.2500".toBigDecimal(), 2, "read monthly")

        val result = validateCsvImportCandidate(base, listOf(row)) { "imported" } as CsvImportCandidateValidation.Valid
        assertEquals("read monthly", result.tracker.readings.single().note)
        assertEquals("1.2500", result.tracker.readings.single().cubicMetres.toPlainString())
    }

    @Test fun candidateValidationRejectsOverlappingIndependentMeterCoverage() {
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val base = TrackerData(installations = listOf(MeterInstallation(first), MeterInstallation(second)))
        val start = java.time.LocalDate.parse("2026-10-01")
        val rows = listOf(
            testCsvRow(first, start, "100".toBigDecimal(), 2),
            testCsvRow(first, start.plusDays(2), "110".toBigDecimal(), 3),
            testCsvRow(second, start, "200".toBigDecimal(), 4),
            testCsvRow(second, start.plusDays(2), "205".toBigDecimal(), 5),
        )
        var generated = 0
        val result = validateCsvImportCandidate(base, rows) { "new-${generated++}" }
        assertTrue(result is CsvImportCandidateValidation.Invalid)
        val error = (result as CsvImportCandidateValidation.Invalid).error
        assertEquals(CsvImportErrorCode.CSV_METER_HISTORY_INVALID, error.code)
        assertEquals(3, error.row)
        assertEquals(CsvImportField.TIMESTAMP, error.field)
    }

    @Test fun importingAnOlderKnownMeterKeepsTheNewEmptyInstallationCurrent() {
        val olderMeter = "11111111-1111-4111-8111-111111111111"
        val currentMeter = "22222222-2222-4222-8222-222222222222"
        val start = java.time.LocalDate.parse("2026-10-26")
        val base = TrackerData(
            installations = listOf(
                MeterInstallation(olderMeter),
                MeterInstallation(currentMeter, start.plusDays(2)),
            ),
        )
        val rows = listOf(
            testCsvRow(olderMeter, start, "100".toBigDecimal(), 2),
            testCsvRow(olderMeter, start.plusDays(1), "105".toBigDecimal(), 3),
        )

        var id = 0
        val result = validateCsvImportCandidate(base, rows) { "imported-${id++}" }
        assertTrue(result is CsvImportCandidateValidation.Valid)
        val imported = (result as CsvImportCandidateValidation.Valid).tracker
        assertEquals(currentMeter, imported.currentInstallation.id)
        assertTrue(imported.readings.none { it.installationId == currentMeter })
        assertEquals(rows.map { it.installationId }.toSet(), imported.readings.map { it.installationId }.toSet())
    }
}
