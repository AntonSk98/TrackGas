package com.trackgas.app

import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.readingCsv
import com.trackgas.app.tracking.writeReadingCsv
import com.trackgas.app.tracking.validateReadingCsv
import com.trackgas.app.tracking.CsvReadingValidation
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingCsvWriterTest {
    @Test fun succeedsOnlyAfterClosingCompleteOutput() {
        var closed = false
        val stream = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        assertTrue(writeReadingCsv(TrackerData()) { stream })
        assertTrue(closed)
        assertEquals("meter_id;date;meter_value;note\n", stream.toString("UTF-8"))
    }

    @Test fun exportsExactMeterPrecisionAndQuotesNotesForRoundTrip() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val note = "checked; pressure \"ok\"\r\nnext"
        val tracker = TrackerData(
            readings = listOf(Reading("reading", LocalDate.parse("2026-10-28"), "123.4500".toBigDecimal(), meter, note)),
            installations = listOf(MeterInstallation(meter)),
        )
        val csv = String(readingCsv(tracker), Charsets.UTF_8)

        assertEquals(
            "meter_id;date;meter_value;note\n$meter;2026-10-28;123.4500;\"checked; pressure \"\"ok\"\"\r\nnext\"\n",
            csv,
        )
        val parsed = validateReadingCsv(csv, setOf(meter)) as CsvReadingValidation.Valid
        assertEquals("123.4500", parsed.rows.single().cubicMetres.toPlainString())
        assertEquals(note, parsed.rows.single().note)
    }

    @Test fun exportsMissingNoteAsAnEmptyFourthField() {
        val csv = String(readingCsv(TrackerData()), Charsets.UTF_8)
        assertEquals("meter_id;date;meter_value;note\n", csv)
    }

    @Test fun openWriteAndCloseFailuresAreReported() {
        assertFalse(writeReadingCsv(TrackerData()) { null })
        assertFalse(writeReadingCsv(TrackerData()) { throw IOException() })
        assertFalse(writeReadingCsv(TrackerData()) { throw SecurityException() })
        assertFalse(writeReadingCsv(TrackerData()) { throw IllegalArgumentException() })
        var closed = false
        assertFalse(writeReadingCsv(TrackerData()) {
            object : OutputStream() {
                override fun write(value: Int) { throw IOException() }
                override fun close() { closed = true }
            }
        })
        assertTrue(closed)
        assertFalse(writeReadingCsv(TrackerData()) {
            object : ByteArrayOutputStream() {
                override fun close() { throw IOException() }
            }
        })
    }
}
