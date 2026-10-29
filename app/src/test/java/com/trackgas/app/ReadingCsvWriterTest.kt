package com.trackgas.app

import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.writeReadingCsv
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
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
        assertEquals("meter_id;date;meter_value\n", stream.toString("UTF-8"))
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
