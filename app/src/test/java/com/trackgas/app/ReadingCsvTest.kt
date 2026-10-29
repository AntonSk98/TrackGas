package com.trackgas.app

import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.tracking.readingCsv
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingCsvTest {
    @Test fun emptyTrackerContainsOnlyHeader() {
        assertEquals("meter_id;date;meter_value\n", readingCsv(TrackerData()).toString(Charsets.UTF_8))
    }

    @Test fun ordersAllReadingsByDateAndInstallationAndPreservesDecimals() {
        val date = LocalDate.of(2026, 10, 28)
        val tracker = TrackerData(
            readings = listOf(
                Reading("later", date.plusDays(1), "12.300".toBigDecimal(), "new"),
                Reading("initial", date, "0.00".toBigDecimal(), "new"),
                Reading("final", date, "1105.0000".toBigDecimal()),
                Reading("earlier", date.minusDays(1), "999".toBigDecimal()),
            ),
            installations = listOf(MeterInstallation("new", date), MeterInstallation("original")),
        )
        assertEquals(
            "meter_id;date;meter_value\n" +
                "original;2026-10-27;999\n" +
                "original;2026-10-28;1105.0000\n" +
                "new;2026-10-28;0.00\n" +
                "new;2026-10-29;12.300\n",
            readingCsv(tracker).toString(Charsets.UTF_8),
        )
    }

    @Test fun quotesSpecialCharactersAndEncodesUtf8() {
        val id = "m;\"\r\nä"
        val tracker = TrackerData(
            readings = listOf(Reading("r", LocalDate.of(2026, 10, 28), "1.20".toBigDecimal(), id)),
            installations = listOf(MeterInstallation(id)),
        )
        assertEquals(
            "meter_id;date;meter_value\n\"m;\"\"\r\nä\";2026-10-28;1.20\n",
            readingCsv(tracker).toString(Charsets.UTF_8),
        )
    }

    @Test fun localeDoesNotChangeValuesAndNoConsumptionRowsAreAdded() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val date = LocalDate.of(2026, 10, 28)
            val tracker = TrackerData(
                readings = listOf(
                    Reading("first", date, "123.450".toBigDecimal()),
                    Reading("second", date.plusDays(2), "125.750".toBigDecimal()),
                ),
            )
            assertEquals(
                "meter_id;date;meter_value\n" +
                    "original;2026-10-28;123.450\n" +
                    "original;2026-10-30;125.750\n",
                readingCsv(tracker).toString(Charsets.UTF_8),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun readmeExampleMatchesSerializedReadings() {
        val date = LocalDate.of(2026, 10, 28)
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val tracker = TrackerData(
            readings = listOf(
                Reading("earlier", date.minusDays(1), "1099.500".toBigDecimal(), first),
                Reading("final", date, "1105.000".toBigDecimal(), first),
                Reading("initial", date, "0".toBigDecimal(), second),
                Reading("later", date.plusDays(1), "3.250".toBigDecimal(), second),
            ),
            installations = listOf(MeterInstallation(first), MeterInstallation(second, date)),
        )
        assertEquals(
            "meter_id;date;meter_value\n" +
                "11111111-1111-4111-8111-111111111111;2026-10-27;1099.500\n" +
                "11111111-1111-4111-8111-111111111111;2026-10-28;1105.000\n" +
                "22222222-2222-4222-8222-222222222222;2026-10-28;0\n" +
                "22222222-2222-4222-8222-222222222222;2026-10-29;3.250\n",
            readingCsv(tracker).toString(Charsets.UTF_8),
        )
    }
}
