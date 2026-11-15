package com.trackgas.app

import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.csvImportCandidate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingCsvInstallationStartDateTest {
    @Test fun newImportedInstallationsUseTheirEarliestReadingDate() {
        val oldDate = LocalDate.parse("2025-10-10")
        val currentDate = LocalDate.parse("2026-10-01")
        var id = 0
        val imported = csvImportCandidate(
            TrackerData(),
            listOf(
                testCsvRow("old", oldDate, "10".toBigDecimal(), 2),
                testCsvRow("old", oldDate.plusDays(1), "11".toBigDecimal(), 3),
                testCsvRow("current", currentDate, "1".toBigDecimal(), 4),
                testCsvRow("current", currentDate.plusDays(1), "2".toBigDecimal(), 5),
            ),
        ) { "reading-${id++}" }

        assertEquals(oldDate, imported.installations.single { it.id == "old" }.startDate)
        assertEquals(currentDate, imported.installations.single { it.id == "current" }.startDate)

        id = 0
        val legacy = csvImportCandidate(
            TrackerData(installations = listOf(MeterInstallation("old"), MeterInstallation("current"))),
            listOf(
                testCsvRow("old", oldDate, "10".toBigDecimal(), 2),
                testCsvRow("old", oldDate.plusDays(1), "11".toBigDecimal(), 3),
                testCsvRow("current", currentDate, "1".toBigDecimal(), 4),
                testCsvRow("current", currentDate.plusDays(1), "2".toBigDecimal(), 5),
            ),
        ) { "legacy-${id++}" }
        assertEquals(oldDate, legacy.installations.single { it.id == "old" }.startDate)
        assertEquals(currentDate, legacy.installations.single { it.id == "current" }.startDate)
    }
}
