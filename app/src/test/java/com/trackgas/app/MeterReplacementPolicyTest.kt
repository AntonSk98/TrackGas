package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MeterReplacementPolicyTest {
    private val date = LocalDate.of(2026, 10, 28)
    private val final = Reading("final", date, "1105.0000".toBigDecimal())
    private val tracker = TrackerData(
        listOf(final.copy(id = "previous", date = date.minusDays(1),
            observedAt = final.observedAt.minusSeconds(86400), cubicMetres = "1100".toBigDecimal()), final),
    )

    private fun replace(
        data: TrackerData = tracker,
        target: ReplacementTarget = replacementTarget(data, date),
        today: LocalDate = date,
        installationId: String = "new",
    ) = evaluateReplacement(data, ReplaceMeterIntent(target, today), installationId)

    @Test fun replacementCreatesAnEmptyCurrentInstallationWithOrWithoutPriorReadings() {
        val next = (replace() as ReplacementValidation.Accepted).tracker
        assertNull(readingCollectionViolation(next))
        assertEquals(tracker.readings, next.readings)
        assertEquals(tracker.installations + MeterInstallation("new", date), next.installations)
        assertEquals("new", next.currentInstallation.id)

        val empty = (replace(TrackerData()) as ReplacementValidation.Accepted).tracker
        assertTrue(empty.readings.isEmpty())
        assertEquals("new", empty.currentInstallation.id)
    }

    @Test fun staleCurrentReadingsAndDatesAreNeverSilentlyAccepted() {
        val target = replacementTarget(tracker, date)
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
            replace(first, replacementTarget(tracker, date)),
        )
        assertEquals(ReplacementValidation.Rejected(ReplacementError.ALREADY_REPLACED), replace(first))

        val tomorrow = date.plusDays(1)
        val second = evaluateReplacement(
            first,
            ReplaceMeterIntent(replacementTarget(first, tomorrow), tomorrow),
            "third",
        ) as ReplacementValidation.Accepted
        assertNull(readingCollectionViolation(second.tracker))
        assertEquals("third", second.tracker.currentInstallation.id)
        assertEquals(first.readings, second.tracker.readings)
    }

    @Test fun firstReadingAfterReplacementIsARealBaselineAndEveryReadingIsDeletable() {
        val next = (replace() as ReplacementValidation.Accepted).tracker
        val baseline = Reading("new-reading", date.plusDays(1), "3.2500".toBigDecimal(), next.currentInstallation.id)
        val withReading = next.copy(readings = next.readings + baseline)
        val intervals = readingIntervals(withReading)
        assertEquals(1, intervals.size)
        assertEquals(final.installationId, intervals.single().earlier.installationId)
        assertEquals(final.installationId, intervals.single().later.installationId)
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(withReading, final.id))
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(withReading, baseline.id))
        assertEquals(DeletionValidation.MISSING, evaluateDeletion(withReading, "missing"))
    }
}
