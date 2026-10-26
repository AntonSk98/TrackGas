package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class PickerDatesTest {
    @Test fun utcDatesRoundTripIndependentlyOfDeviceTimezone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Pacific/Kiritimati", "America/Los_Angeles", "Europe/Berlin")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (text in listOf("2024-02-29", "2026-03-29", "2026-10-25", "2026-12-31")) {
                    val date = LocalDate.parse(text)
                    assertEquals(date, pickerDate(date.toPickerMillis()))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun localizedValuesRetainRawPrecisionAndOnlyUsageRounds() {
        val value = BigDecimal("120.123456789")
        assertEquals("120,123456789", numberText(value, Locale.GERMANY, raw = true))
        assertEquals("120.123456789", numberText(value, Locale.US, raw = true))
        assertEquals("120.123", numberText(value, Locale.US))
    }
}
