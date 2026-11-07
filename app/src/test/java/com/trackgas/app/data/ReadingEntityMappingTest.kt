package com.trackgas.app.data

import com.trackgas.app.tracking.Reading
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingEntityMappingTest {
    @Test fun noteSurvivesPersistenceMappingAndBlankNotesAreAbsent() {
        val reading = Reading(
            id = "reading",
            date = LocalDate.parse("2026-10-28"),
            cubicMetres = BigDecimal("123.4500"),
            note = "Kitchen meter",
        )
        val persisted = reading.toEntity()

        assertEquals("Kitchen meter", persisted.note)
        assertEquals(reading, persisted.toReading())
        assertNull(reading.copy(note = "  \n").toEntity().note)
        assertNull(persisted.copy(note = " ").toReading().note)
    }
}
