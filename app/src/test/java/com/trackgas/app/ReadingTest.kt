package com.trackgas.app

import com.trackgas.app.tracking.Reading
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
}
