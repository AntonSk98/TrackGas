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
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCsvParserTest {
    @Test fun parsesAvailableMeterCsvFixture() {
        val fixture = sequenceOf(
            File("meter_readings.csv"), File("../meter_readings.csv"),
            File("trackgas-readings_import.csv"), File("../trackgas-readings_import.csv"),
        ).firstOrNull(File::isFile)
        val text = fixture?.readText(Charsets.UTF_8)
            ?: "meter_id;date;meter_value\n11111111-1111-4111-8111-111111111111;2026-10-28;1.20\n"
        val result = parseReadingCsv(text)
        assertTrue(result is ReadingCsvParseResult.Parsed)
        val records = (result as ReadingCsvParseResult.Parsed).records
        assertTrue(records.size > 1)
        assertEquals(1, records.first().physicalRow)
        assertEquals(listOf("meter_id", "date", "meter_value"), records.first().fields)
        assertTrue(records.last().physicalRow > 1)
        assertEquals(3, records.last().fields.size)
    }

    @Test fun parsesWriterQuotedFieldsAndTracksPhysicalRows() {
        val text = "meter_id;date;meter_value\r\n\"m;\"\"x\r\ny\";2026-10-28;1.20\r\n"
        val result = parseReadingCsv(text) as ReadingCsvParseResult.Parsed
        assertEquals(2, result.records.size)
        assertEquals(1, result.records[0].physicalRow)
        assertEquals(2, result.records[1].physicalRow)
        assertEquals(listOf("m;\"x\r\ny", "2026-10-28", "1.20"), result.records[1].fields)
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
        assertEquals(CsvImportErrorCode.CSV_ROW_INVALID, error("meter_id;date;meter_value\nshort\n").code)
        assertEquals(CsvImportErrorCode.CSV_METER_ID_INVALID, error("meter_id;date;meter_value\nnot-a-uuid;2026-10-28;1\n").code)
        assertEquals(CsvImportErrorCode.CSV_METER_UNKNOWN, error("meter_id;date;meter_value\n$unknown;2026-10-28;1\n").code)
        assertEquals(CsvImportErrorCode.CSV_DATE_INVALID, error("meter_id;date;meter_value\n$meter;not-a-date;1\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_INVALID, error("meter_id;date;meter_value\n$meter;2026-10-28;one\n").code)
        assertEquals(CsvImportErrorCode.CSV_VALUE_NEGATIVE, error("meter_id;date;meter_value\n$meter;2026-10-28;-1\n").code)
        val contextual = error("meter_id;date;meter_value\n$meter;2026-10-28;1\n$meter;bad;2\n")
        assertEquals(3, contextual.row)
        assertEquals(CsvImportField.DATE, contextual.field)
    }

    @Test fun deduplicatesByMeterAndDateKeepingTheLastRowAndPreservingOtherMeters() {
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val csv = "meter_id;date;meter_value\n" +
            "$first;2026-10-28;1.0\n" +
            "$second;2026-10-28;5\n" +
            "$first;2026-10-28;2.00\n"
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
}
