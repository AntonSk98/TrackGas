package com.trackgas.app

import androidx.compose.runtime.saveable.SaverScope
import com.trackgas.app.tracking.*
import java.time.*
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ObservationTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun add(date: String, time: String, recordingZone: ZoneId = zone) =
        SaveReadingIntent.Add(LocalDate.parse(date), ParsedMeter.Value("100".toBigDecimal()),
            now.atZone(recordingZone).toLocalDate(), time, recordingZone, now)
    private fun error(intent: SaveReadingIntent.Add) =
        (evaluateSave(emptyList(), intent, "new") as ReadingValidation.Rejected).errors.time

    @Test fun missingInvalidGapAndFutureTimesAreRejected() {
        assertEquals(ReadingError.TIME_REQUIRED, error(add("2026-11-08", "")))
        for (time in listOf("8:15", "24:00", "12:00:00", "not time")) {
            assertEquals(ReadingError.INVALID_TIME, error(add("2026-11-08", time)))
        }
        assertEquals(ReadingError.INVALID_TIME, error(add("2026-03-29", "02:30")))
        assertEquals(ReadingError.FUTURE_TIME, error(add("2026-11-09", "13:01")))
    }

    @Test fun overlapUsesEarlierOffsetButUnchangedImportedLaterOffsetSurvivesEdit() {
        val candidate = (evaluateSave(emptyList(), add("2026-10-25", "02:30"), "a") as ReadingValidation.Accepted).candidate
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), candidate.observedAt)
        val imported = candidate.copy(observedAt = Instant.parse("2026-10-25T01:30:00Z"))
        val edit = SaveReadingIntent.Edit("a", ParsedMeter.Value("101".toBigDecimal()), LocalDate.parse("2026-11-09"),
            "note", "02:30", now)
        val updated = (evaluateSave(listOf(imported), edit) as ReadingValidation.Accepted).candidate
        assertEquals(imported.observedAt, updated.observedAt)
        assertEquals(imported.zone, updated.zone)
        assertEquals(imported.id, updated.id)
        assertEquals(imported.date, updated.date)
    }

    @Test fun differentZonesCannotReverseSameMeterInstantOrder() {
        val earlier = Reading("a", LocalDate.parse("2026-11-06"), "100".toBigDecimal(),
            observedAt = Instant.parse("2026-11-07T04:00:00Z"), zone = ZoneId.of("America/New_York"))
        val intent = add("2026-11-07", "01:00", ZoneId.of("Asia/Tokyo"))
        assertEquals(ReadingError.TIME_ORDER,
            (evaluateSave(listOf(earlier), intent, "b") as ReadingValidation.Rejected).errors.time)
        val reversed = Reading("b", LocalDate.parse("2026-11-07"), "100".toBigDecimal(),
            observedAt = Instant.parse("2026-11-06T16:00:00Z"), zone = ZoneId.of("Asia/Tokyo"))
        assertEquals(ReadingCollectionViolation.TIMESTAMP_ORDER, readingCollectionViolation(listOf(earlier, reversed)))
    }

    @Test fun constructorRejectsDateDriftAndSubMinutePrecision() {
        val reading = Reading("a", LocalDate.parse("2026-11-06"), "100".toBigDecimal())
        assertThrows(IllegalArgumentException::class.java) { reading.copy(date = reading.date.plusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { reading.copy(observedAt = reading.observedAt.plusSeconds(1)) }
    }

    @Test fun draftRestorationPreservesTimeZoneAndMatchingIdentity() {
        val draft = ReadingDraft("session", "dialog", "2026-11-06", "100", "", time = "20:15", timezone = zone.id,
            choosingTime = true)
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(ReadingDraft.saver) { scope.save(draft) }!!
        val restored = ReadingDraft.saver.restore(saved)!!
        assertEquals("20:15", restored.time)
        assertEquals(zone.id, restored.timezone)
        assertTrue(restored.choosingTime)
        assertTrue(restored.matches("session", "dialog"))
        assertFalse(restored.matches("other", "dialog"))
    }

    @Test fun historyFormattingIgnoresLaterDeviceTimezone() {
        val reading = Reading("a", LocalDate.parse("2026-11-06"), "100".toBigDecimal(),
            observedAt = Instant.parse("2026-11-06T07:15:00Z"), zone = zone)
        val original = TimeZone.getDefault()
        try {
            val before = readingDateTimeText(reading, Locale.GERMANY)
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(before, readingDateTimeText(reading, Locale.GERMANY))
            assertTrue(before.contains("08:15"))
        } finally { TimeZone.setDefault(original) }
    }
}
