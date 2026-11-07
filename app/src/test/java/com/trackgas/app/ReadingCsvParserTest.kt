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
import com.trackgas.app.tracking.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCsvParserTest {
    @Test fun parsesCurrentFourColumnExample() {
        val text =
            "meter_id;date;meter_value;note\n" +
                "11111111-1111-4111-8111-111111111111;2026-10-28;1.20;\n"
        val result = parseReadingCsv(text)
        assertTrue(result is ReadingCsvParseResult.Parsed)
        val records = (result as ReadingCsvParseResult.Parsed).records
        assertTrue(records.size > 1)
        assertEquals(1, records.first().physicalRow)
        assertEquals(listOf("meter_id", "date", "meter_value", "note"), records.first().fields)
        assertEquals(4, records.first().fields.size)
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
        val header = "meter_id;date;meter_value;note\n"
        assertEquals(CsvImportErrorCode.CSV_ROW_INVALID, error(header + "short\n").code)
        assertEquals(CsvImportErrorCode.CSV_METER_ID_INVALID, error(header + "not-a-uuid;2026-10-28;1;\n").code)
        assertEquals(CsvImportErrorCode.CSV_METER_UNKNOWN, error(header + "$unknown;2026-10-28;1;\n").code)
        assertEquals(CsvImportErrorCode.CSV_DATE_INVALID, error(header + "$meter;not-a-date;1;\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_INVALID, error(header + "$meter;2026-10-28;one;\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_NEGATIVE, error(header + "$meter;2026-10-28;-1;\n").code)
        val contextual = error(header + "$meter;2026-10-28;1;\n$meter;bad;2;\n")
        assertEquals(3, contextual.row)
        assertEquals(CsvImportField.DATE, contextual.field)
    }

    @Test fun rejectsThreeColumnFilesAndTreatsEmptyFourColumnNotesAsAbsent() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val legacy = validateReadingCsv(
            "meter_id;date;meter_value\n$meter;2026-10-28;1.2500\n",
            setOf(meter),
        ) as CsvReadingValidation.Invalid
        assertEquals(CsvImportErrorCode.CSV_HEADER_INVALID, legacy.error.code)

        val current = validateReadingCsv(
            "meter_id;date;meter_value;note\n$meter;2026-10-28;1.2500;\n",
            setOf(meter),
        ) as CsvReadingValidation.Valid
        assertNull(current.rows.single().note)
        assertEquals("1.2500", current.rows.single().cubicMetres.toPlainString())
    }

    @Test fun importsQuotedNotesWithDelimitersQuotesAndLineBreaks() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val note = "checked; pressure \"ok\"\r\nnext"
        val csv = "meter_id;date;meter_value;note\n" +
            "$meter;2026-10-28;1.2500;\"checked; pressure \"\"ok\"\"\r\nnext\"\n"

        val parsed = validateReadingCsv(csv, setOf(meter)) as CsvReadingValidation.Valid
        assertEquals(note, parsed.rows.single().note)
    }

    @Test fun deduplicatesByMeterAndDateKeepingTheLastRowAndPreservingOtherMeters() {
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val csv = "meter_id;date;meter_value;note\n" +
            "$first;2026-10-28;1.0;\n" +
            "$second;2026-10-28;5;\n" +
            "$first;2026-10-28;2.00;\n"
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
            CsvReadingRow(meter, java.time.LocalDate.parse("2026-10-01"), "100".toBigDecimal(), 2),
            CsvReadingRow(meter, java.time.LocalDate.parse("2026-10-02"), "90".toBigDecimal(), 3),
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
        val row = CsvReadingRow(meter, java.time.LocalDate.parse("2026-10-28"), "1.2500".toBigDecimal(), 2, "read monthly")

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
            CsvReadingRow(first, start, "100".toBigDecimal(), 2),
            CsvReadingRow(first, start.plusDays(2), "110".toBigDecimal(), 3),
            CsvReadingRow(second, start, "200".toBigDecimal(), 4),
            CsvReadingRow(second, start.plusDays(2), "205".toBigDecimal(), 5),
        )
        var generated = 0
        val result = validateCsvImportCandidate(base, rows) { "new-${generated++}" }
        assertTrue(result is CsvImportCandidateValidation.Invalid)
        val error = (result as CsvImportCandidateValidation.Invalid).error
        assertEquals(CsvImportErrorCode.CSV_METER_HISTORY_INVALID, error.code)
        assertEquals(3, error.row)
        assertEquals(CsvImportField.DATE, error.field)
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
            CsvReadingRow(olderMeter, start, "100".toBigDecimal(), 2),
            CsvReadingRow(olderMeter, start.plusDays(1), "105".toBigDecimal(), 3),
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
