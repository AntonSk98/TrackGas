package com.trackgas.app.data

import com.trackgas.app.Reading
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingEntityMappingTest {
    @Test fun storedTimesMustAgreeWithDateAndBeMinutePrecise() {
        val entity = Reading("a", LocalDate.parse("2026-10-28"), BigDecimal.ONE).toEntity()
        assertThrows(StoredReadingDecodingException::class.java) { entity.copy(timezone = "Bad/Zone").toReading() }
        assertThrows(StoredReadingDecodingException::class.java) { entity.copy(observedAt = entity.observedAt + 1).toReading() }
        assertThrows(StoredReadingDecodingException::class.java) { entity.copy(date = "2026-10-27").toReading() }
    }
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
