package com.trackgas.app

import com.trackgas.app.data.toEntity
import com.trackgas.app.data.toReading
import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryProjectionTest {
    @Test fun reconstructsEstimatesFromTimestampedCsvAndStoredReadingsWithoutExportingThem() {
        val meter = "11111111-1111-4111-8111-111111111111"
        val first = observation("first", "2026-11-06T08:00", "1000.000", meter, "Europe/Berlin")
        val later = observation("later", "2026-11-07T23:00", "1013.000", meter, "Europe/Berlin")
        val tracker = TrackerData(listOf(later, first), listOf(MeterInstallation(meter)))
        val reloaded = tracker.copy(readings = tracker.readings.map { it.toEntity().toReading() })
        assertEquals(history24HourEstimates(tracker), history24HourEstimates(reloaded))
        val csv = readingCsv(tracker).toString(Charsets.UTF_8)
        val parsed = validateReadingCsv(csv, setOf(meter)) as CsvReadingValidation.Valid
        var id = 0
        val imported = (validateCsvImportCandidate(
            TrackerData(installations = tracker.installations), parsed.rows,
        ) { "imported-${id++}" } as CsvImportCandidateValidation.Valid).tracker
        assertEquals(0, BigDecimal("8").compareTo(history24HourEstimates(imported).values.single()))
        assertEquals(csv, readingCsv(imported).toString(Charsets.UTF_8))
        assertEquals("meter_id;timestamp;timezone;meter_value;note", csv.lineSequence().first())
    }

    @Test fun recomputesNeighboursAfterInsertionDeletionAndValueOrTimeCorrections() {
        val first = observation("first", "2026-11-06T08:00", "100")
        val middle = observation("middle", "2026-11-07T08:00", "103")
        val last = observation("last", "2026-11-08T08:00", "112")
        val original = TrackerData(listOf(last, first))
        val inserted = original.copy(readings = original.readings + middle)
        assertEquals(0, BigDecimal("6").compareTo(history24HourEstimates(original).getValue(last.id)))
        assertEquals(mapOf(middle.id to BigDecimal("3"), last.id to BigDecimal("9")),
            history24HourEstimates(inserted))
        val deleted = inserted.copy(readings = inserted.readings.filterNot { it.id == middle.id })
        assertEquals(history24HourEstimates(original), history24HourEstimates(deleted))
        val edited = inserted.copy(readings = inserted.readings.map {
            if (it.id == middle.id) it.copy(cubicMetres = BigDecimal("106")) else it
        })
        assertEquals(mapOf(middle.id to BigDecimal("6"), last.id to BigDecimal("6")),
            history24HourEstimates(edited))
        val timeEdited = original.copy(readings = original.readings.map {
            if (it.id == last.id) it.copy(observedAt = it.observedAt.plusSeconds(6 * 3600)) else it
        })
        assertNotEquals(history24HourEstimates(original), history24HourEstimates(timeEdited))
        assertTrue(history24HourEstimates(TrackerData(listOf(last))).isEmpty())
    }

    @Test fun pairsWithinInstallationsAndOmitsBaselinesIncludingOpeningConsumption() {
        val first = observation("first", "2026-11-06T08:00", "1000")
        val last = observation("last", "2026-11-07T23:00", "1013")
        val opening = observation("opening", "2026-11-07T23:30", "5", "new")
        val next = observation("next", "2026-11-08T23:30", "7", "new")
        val installations = listOf(
            MeterInstallation(ORIGINAL_INSTALLATION_ID),
            MeterInstallation("new", opening.date),
        )
        assertTrue(history24HourEstimates(TrackerData()).isEmpty())
        assertTrue(history24HourEstimates(TrackerData(listOf(first))).isEmpty())
        assertTrue(history24HourEstimates(TrackerData(listOf(opening), installations)).isEmpty())
        val tracker = TrackerData(listOf(next, last, opening, first), installations)
        val estimates = history24HourEstimates(tracker)
        assertEquals(setOf(last.id, next.id), estimates.keys)
        assertEquals(0, BigDecimal("8").compareTo(estimates.getValue(last.id)))
        assertEquals(0, BigDecimal("2").compareTo(estimates.getValue(next.id)))
        assertEquals(estimates, history24HourEstimates(tracker.copy(readings = tracker.readings.reversed())))
    }

    private fun observation(
        id: String,
        local: String,
        value: String,
        installationId: String = ORIGINAL_INSTALLATION_ID,
        zone: String = "UTC",
    ): com.trackgas.app.tracking.Reading {
        val time = LocalDateTime.parse(local).atZone(ZoneId.of(zone))
        return Reading(id, time.toLocalDate(), BigDecimal(value), installationId,
            observedAt = time.toInstant(), zone = time.zone)
    }

    @Test fun usesAbsoluteTimeAcrossDaylightSavingAndRecordingZones() {
        for ((start, end, difference) in listOf(
            Triple("2026-03-28T08:00", "2026-03-29T08:00", "23"),
            Triple("2026-10-24T08:00", "2026-10-25T08:00", "25"),
        )) {
            val first = observation("first", start, "0", zone = "Europe/Berlin")
            val later = observation("later", end, difference, zone = "Europe/Berlin")
            assertEquals(0, BigDecimal("24").compareTo(ReadingInterval(first, later).cubicMetresPer24Hours))
        }
        val first = observation("first", "2026-11-06T08:00", "0", zone = "Europe/Berlin")
        val later = observation("later", "2026-11-07T08:00", "25", zone = "UTC")
        assertEquals(0, BigDecimal("24").compareTo(ReadingInterval(first, later).cubicMetresPer24Hours))
    }

    @Test fun rejectsEqualOrReversedInstantsEvenWhenRecordedDatesIncrease() {
        val first = observation("first", "2026-11-06T23:30", "0", zone = "UTC")
        for (time in listOf("2026-11-07T00:00", "2026-11-07T00:30")) {
            val later = observation("later", time, "1", zone = "Europe/Berlin")
            assertThrows(IllegalArgumentException::class.java) { ReadingInterval(first, later) }
        }
    }

    @Test fun normalizesLongShortExactAndPartialHourIntervalsWithoutDisplayRounding() {
        val first = observation("first", "2026-11-06T08:00", "1000")
        for ((time, difference, expected) in listOf(
            Triple("2026-11-07T23:00", "13", "8"),
            Triple("2026-11-07T08:00", "13", "13"),
            Triple("2026-11-07T08:00", "0", "0"),
        )) {
            val later = observation("later", time, (first.cubicMetres + BigDecimal(difference)).toPlainString())
            assertEquals(0, BigDecimal(expected).compareTo(ReadingInterval(first, later).cubicMetresPer24Hours))
        }
        val evening = observation("evening", "2026-11-06T20:00", "1000")
        val short = observation("short", "2026-11-07T08:00", "1003")
        val partial = observation("partial", "2026-11-07T06:30", "1007")
        assertEquals(0, BigDecimal("6").compareTo(ReadingInterval(evening, short).cubicMetresPer24Hours))
        assertEquals(0, BigDecimal("16").compareTo(ReadingInterval(evening, partial).cubicMetresPer24Hours))
        val third = observation("third", "2026-11-09T08:00", "1001")
        assertEquals(BigDecimal.ONE.divide(BigDecimal("3"), MathContext.DECIMAL128),
            ReadingInterval(first, third).cubicMetresPer24Hours)
    }
}
