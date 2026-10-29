package com.trackgas.app

import com.trackgas.app.tracking.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MeterReplacementPolicyTest {
    private val date = LocalDate.of(2026, 10, 28)
    private val final = Reading("final", date, "1105.0000".toBigDecimal())
    private val tracker = TrackerData(listOf(final.copy(id = "previous", date = date.minusDays(1), cubicMetres = "1100".toBigDecimal()), final))
    private fun replace(data: TrackerData = tracker, target: ReplacementTarget = replacementTarget(data, date), today: LocalDate = date) =
        evaluateReplacement(data, ReplaceMeterIntent(target, today), "new", "initial", "replacement")

    @Test fun requiresFinalAndAutomaticallyCreatesZeroWithoutAMeterValue() {
        assertEquals(ReplacementValidation.Rejected(ReplacementError.MISSING_FINAL), replace(TrackerData()))
        val next = (replace() as ReplacementValidation.Accepted).tracker
        assertNull(readingCollectionViolation(next))
        assertEquals("0", next.readings.last().cubicMetres.toPlainString())
        assertEquals(tracker.readings, next.readings.dropLast(1))
        assertEquals("new", next.currentInstallation.id)
        val tomorrow = next.copy(readings = next.readings + Reading("next", date.plusDays(1), "5".toBigDecimal(), "new"))
        assertEquals("5".toBigDecimal(), dashboard(tomorrow, CalendarPeriod.containing(PeriodType.MONTH, date))
            .buckets.single { it.range.start == date.plusDays(1) }.amount.cubicMetres)
    }

    @Test fun staleFinalIdentityValueScaleAndDateAreNeverRetargeted() {
        val target = replacementTarget(tracker, date)
        for (changed in listOf(
            tracker.copy(readings = tracker.readings.filterNot { it.id == final.id }),
            tracker.copy(readings = tracker.readings.map { if (it.id == final.id) it.copy(cubicMetres = "1106".toBigDecimal()) else it }),
            tracker.copy(readings = tracker.readings.map { if (it.id == final.id) it.copy(cubicMetres = "1105.00000".toBigDecimal()) else it }),
        )) assertEquals(ReplacementValidation.Rejected(ReplacementError.OBSOLETE_TARGET), replace(changed, target = target))
        assertEquals(ReplacementValidation.Rejected(ReplacementError.STALE_DATE), replace(today = date.plusDays(1)))
        assertEquals(ReplacementValidation.Rejected(ReplacementError.NOT_LATEST),
            replace(tracker.copy(readings = tracker.readings + final.copy(id = "future", date = date.plusDays(1)))))
    }

    @Test fun competingAndRepeatedSameDayReplacementAreRejectedButNextDayIsAllowed() {
        val target = replacementTarget(tracker, date)
        val next = (replace() as ReplacementValidation.Accepted).tracker
        assertEquals(ReplacementValidation.Rejected(ReplacementError.OBSOLETE_TARGET), replace(next, target = target))
        assertEquals(ReplacementValidation.Rejected(ReplacementError.ALREADY_REPLACED), replace(next))
        val tomorrow = date.plusDays(1)
        val withFinal = next.copy(readings = next.readings + Reading("next-final", tomorrow, "3".toBigDecimal(), "new"))
        val twice = evaluateReplacement(withFinal, ReplaceMeterIntent(replacementTarget(withFinal, tomorrow), tomorrow), "third", "third-initial", "second")
        assertTrue(twice is ReplacementValidation.Accepted)
        assertNull(readingCollectionViolation((twice as ReplacementValidation.Accepted).tracker))
        assertEquals("0".toBigDecimal(), twice.tracker.readings.last().cubicMetres)
    }

    @Test fun onlyOrdinaryReadingsCanBeDeletedIncludingAnOldTargetThatBecameFinal() {
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(tracker, final.id))
        val next = (replace() as ReplacementValidation.Accepted).tracker
        assertEquals(DeletionValidation.PROTECTED, evaluateDeletion(next, final.id))
        assertEquals(DeletionValidation.PROTECTED, evaluateDeletion(next, "initial"))
        assertEquals(DeletionValidation.ALLOWED, evaluateDeletion(next, "previous"))
        assertEquals(DeletionValidation.MISSING, evaluateDeletion(next, "missing"))
    }

    @Test fun subsequentReplacementPreservesExistingNonzeroBaselineAndSkippedDaysUseZero() {
        val replaced = (replace() as ReplacementValidation.Accepted).tracker
        val futureDate = date.plusDays(3)
        val skipped = replaced.copy(readings = replaced.readings + Reading("next-final", futureDate, "6".toBigDecimal(), "new"))
        val interval = readingIntervals(skipped).last()
        for (day in 1L..3L) {
            val coveredDate = date.plusDays(day)
            assertEquals(0, "2".toBigDecimal().compareTo(allocate(listOf(interval), DateRange(coveredDate, coveredDate)).cubicMetres))
        }
        val corrected = skipped.copy(readings = skipped.readings.map {
            if (it.id == "initial") it.copy(cubicMetres = "1.2500".toBigDecimal()) else it
        })
        val next = (evaluateReplacement(corrected,
            ReplaceMeterIntent(replacementTarget(corrected, futureDate), futureDate),
            "third", "third-initial", "second",
        ) as ReplacementValidation.Accepted).tracker
        assertEquals("1.2500", next.readings.single { it.id == "initial" }.cubicMetres.toPlainString())
        assertEquals(corrected.readings, next.readings.dropLast(1))
        assertEquals("0".toBigDecimal(), next.readings.last().cubicMetres)
    }
}
