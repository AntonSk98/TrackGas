package com.trackgas.app

import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.meterStartingReadingIds
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingHistoryTest {
    @Test fun marksOnlyPersistedStartingReadingsAndNotLaterOrLegacyEntries() {
        val startDate = LocalDate.parse("2026-11-01")
        val laterDate = startDate.plusDays(1)
        val tracker = TrackerData(
            readings = listOf(
                Reading("start", startDate, BigDecimal("100"), "new-meter"),
                Reading("later", laterDate, BigDecimal("105"), "new-meter"),
                Reading("legacy", startDate, BigDecimal("50"), "legacy-meter"),
            ),
            installations = listOf(
                MeterInstallation("new-meter", startDate),
                MeterInstallation("legacy-meter"),
            ),
        )

        assertEquals(setOf("start"), meterStartingReadingIds(tracker))
        assertTrue(meterStartingReadingIds(tracker.copy(readings = tracker.readings - tracker.readings.first())).isEmpty())
    }
}
