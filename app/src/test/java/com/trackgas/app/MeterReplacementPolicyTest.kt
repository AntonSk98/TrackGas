package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MeterReplacementPolicyTest {
    private val date = LocalDate.of(2026, 10, 28)
    private val final = Reading("final", date, "1105.0000".toBigDecimal())
    private val zone = ZoneId.of("UTC")
    private val tracker = TrackerData(
        listOf(final.copy(id = "previous", date = date.minusDays(1),
            observedAt = final.observedAt.minusSeconds(86400), cubicMetres = "1100".toBigDecimal()), final),
    )

    private fun replace(
        data: TrackerData = tracker,
        target: ReplacementTarget = requireNotNull(replacementTarget(data, date)),
        today: LocalDate = date,
        installationId: String = "new",
        startingValue: String = "0",
    ) = evaluateReplacement(
        data,
        ReplaceMeterIntent(
            target, today, ParsedMeter.Value(startingValue.toBigDecimal()), "12:00", zone,
            target.date.atTime(12, 1).atZone(zone).toInstant(),
        ),
        installationId,
    )

    @Test fun replacementCreatesMeterAndItsStartingReadingTogether() {
        val next = (replace() as ReplacementValidation.Accepted).tracker
        assertNull(readingCollectionViolation(next))
        assertEquals(tracker.readings.size + 1, next.readings.size)
        assertEquals(tracker.installations + MeterInstallation("new", date), next.installations)
        assertEquals("new", next.currentInstallation?.id)
        val starting = next.readings.single { it.installationId == "new" }
        assertEquals(date, starting.date)
        assertEquals("0", starting.cubicMetres.toPlainString())

        assertNull(replacementTarget(TrackerData(), date))
    }

    @Test fun staleCurrentReadingsAndDatesAreNeverSilentlyAccepted() {
        val target = requireNotNull(replacementTarget(tracker, date))
        val changed = tracker.copy(readings = tracker.readings.map {
            if (it.id == final.id) it.copy(cubicMetres = "1106".toBigDecimal()) else it
        })
        assertEquals(ReplacementValidation.Rejected(ReplacementError.OBSOLETE_TARGET), replace(changed, target))
        assertEquals(
            ReplacementValidation.Rejected(ReplacementError.STALE_DATE),
            replace(today = date.plusDays(1)),
        )
        val laterReading = tracker.copy(readings = tracker.readings + final.copy(id = "future",
            date = date.plusDays(1), observedAt = final.observedAt.plusSeconds(86400)))
        assertEquals(ReplacementValidation.Rejected(ReplacementError.NOT_LATEST), replace(laterReading))
    }

    @Test fun replacementCanRepeatOnLaterDaysButNotTwiceOnTheSameDay() {
        val first = (replace() as ReplacementValidation.Accepted).tracker
        assertEquals(
            ReplacementValidation.Rejected(ReplacementError.OBSOLETE_TARGET),
            replace(first, requireNotNull(replacementTarget(tracker, date))),
        )
        assertEquals(ReplacementValidation.Rejected(ReplacementError.ALREADY_REPLACED), replace(first))

        val tomorrow = date.plusDays(1)
        val second = evaluateReplacement(
            first,
            ReplaceMeterIntent(
                requireNotNull(replacementTarget(first, tomorrow)), tomorrow,
                ParsedMeter.Value("7".toBigDecimal()), "12:00", zone,
                tomorrow.atTime(12, 1).atZone(zone).toInstant(),
            ),
            "third",
        ) as ReplacementValidation.Accepted
        assertNull(readingCollectionViolation(second.tracker))
        assertEquals("third", second.tracker.currentInstallation?.id)
        assertEquals(first.readings.size + 1, second.tracker.readings.size)
    }

    @Test fun replacementStartingReadingIsBaselineAndLaterReadingUsesItsValue() {
        val next = (replace() as ReplacementValidation.Accepted).tracker
        val baseline = next.readings.single { it.installationId == "new" }
        val later = baseline.copy(
            id = "new-reading",
            date = date.plusDays(1),
            cubicMetres = "3.2500".toBigDecimal(),
            observedAt = baseline.observedAt.plusSeconds(86400),
        )
        val withReading = next.copy(readings = next.readings + later)
        val intervals = readingIntervals(withReading)
        assertEquals(2, intervals.size)
        assertEquals("new", intervals.last().earlier.installationId)
        assertEquals("new", intervals.last().later.installationId)
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(withReading, final.id))
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(withReading, later.id))
        assertEquals(DeletionValidation.MISSING, evaluateDeletion(withReading, "missing"))
    }

    @Test fun replacementRejectsMissingOrInvalidStartingValues() {
        val target = requireNotNull(replacementTarget(tracker, date))
        val missing = evaluateReplacement(tracker, ReplaceMeterIntent(target, date), "new")
        assertEquals(ReplacementValidation.Rejected(ReplacementError.INVALID_STARTING_READING), missing)
        val negative = evaluateReplacement(
            tracker,
            ReplaceMeterIntent(target, date, ParsedMeter.Value("-1".toBigDecimal())),
            "new",
        )
        assertEquals(ReplacementValidation.Rejected(ReplacementError.INVALID_STARTING_READING), negative)
    }
}
