package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingTest {
    @Test fun modelRetainsIdentityDateAndPrecision() {
        val date = LocalDate.of(2026, 10, 26)
        val reading = Reading("original", date, BigDecimal("120.123456789"))
        assertEquals(date, reading.date)
        assertEquals(reading.id, reading.copy(cubicMetres = BigDecimal("121")).id)
        assertEquals(BigDecimal("120.123456789"), reading.cubicMetres)
    }

    @Test fun invalidIdentityAndNegativeValuesAreRejectedIncludingCopies() {
        val reading = Reading("valid", LocalDate.of(2026, 10, 26), BigDecimal.ZERO)
        assertThrows(IllegalArgumentException::class.java) { reading.copy(id = "") }
        assertThrows(IllegalArgumentException::class.java) { reading.copy(id = " \t") }
        assertThrows(IllegalArgumentException::class.java) { reading.copy(cubicMetres = BigDecimal("-0.001")) }
        assertEquals(BigDecimal.ZERO, reading.cubicMetres)
    }

    @Test fun fullSnapshotRetainsInstallationBoundariesAndExactScale() {
        val date = LocalDate.of(2026, 10, 28)
        val old = Reading("final", date, BigDecimal("1105.0000"))
        val initial = Reading("initial", date, BigDecimal("12.5000"), "new")
        val tracker = TrackerData(
            listOf(old, initial),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", date)),
            listOf(MeterReplacement("replacement", date, old.installationId, "new", old.id, initial.id)),
        )
        val snapshot = CommittedSnapshot(1, tracker)
        assertSame(tracker, snapshot.tracker)
        assertEquals(listOf("final", "initial"), snapshot.readings.map { it.id })
        assertEquals(listOf("1105.0000", "12.5000"), snapshot.readings.map { it.cubicMetres.toPlainString() })
        assertEquals("new", tracker.currentInstallation.id)
        assertEquals(initial.installationId, initial.copy(cubicMetres = BigDecimal("13")).installationId)
        assertThrows(IllegalArgumentException::class.java) { initial.copy(installationId = "") }
    }
}
