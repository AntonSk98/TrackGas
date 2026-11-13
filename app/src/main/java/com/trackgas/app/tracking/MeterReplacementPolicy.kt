package com.trackgas.app.tracking

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime

data class ReplacementTarget(
    val date: LocalDate,
    val installationId: String,
    val expectedReadings: List<Reading>,
)

data class ReplaceMeterIntent(
    val target: ReplacementTarget,
    val today: LocalDate,
    val startingValue: ParsedMeter = ParsedMeter.Required,
    val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: Instant = Instant.now(),
)

enum class ReplacementError { ALREADY_REPLACED, OBSOLETE_TARGET, STALE_DATE, NOT_LATEST, INVALID_STARTING_READING }

sealed interface ReplacementValidation {
    data class Accepted(val tracker: TrackerData) : ReplacementValidation
    data class Rejected(val error: ReplacementError) : ReplacementValidation
}

fun replacementTarget(tracker: TrackerData, date: LocalDate): ReplacementTarget? {
    val current = tracker.currentInstallation ?: return null
    return ReplacementTarget(
        date,
        current.id,
        tracker.readings.filter { it.installationId == current.id }.sortedBy { it.id },
    )
}

/** Revalidates the current meter and commits its replacement with the supplied starting reading. */
fun evaluateReplacement(
    tracker: TrackerData,
    intent: ReplaceMeterIntent,
    installationId: String,
): ReplacementValidation {
    val target = intent.target
    replacementTargetError(tracker, target, intent.today)?.let { return ReplacementValidation.Rejected(it) }
    val installation = MeterInstallation(installationId, target.date)
    val reading = evaluateCandidate(
        tracker,
        "$installationId-start",
        target.date,
        intent.startingValue,
        intent.today,
        installationId,
        time = intent.time,
        zone = intent.zone,
        now = intent.now,
        newInstallation = installation,
    )
    if (reading !is ReadingValidation.Accepted) {
        return ReplacementValidation.Rejected(ReplacementError.INVALID_STARTING_READING)
    }
    return ReplacementValidation.Accepted(appendStartedMeter(tracker, installation, reading.candidate))
}

fun replacementTargetError(tracker: TrackerData, target: ReplacementTarget, today: LocalDate): ReplacementError? =
    when {
        target.date != today -> ReplacementError.STALE_DATE
        tracker.currentInstallation?.id != target.installationId -> ReplacementError.OBSOLETE_TARGET
        tracker.installations.any { it.startDate == target.date } -> ReplacementError.ALREADY_REPLACED
        tracker.readings.filter { it.installationId == target.installationId }.sortedBy { it.id } != target.expectedReadings ->
            ReplacementError.OBSOLETE_TARGET
        tracker.readings.any { it.installationId == target.installationId && it.date > target.date } ->
            ReplacementError.NOT_LATEST
        else -> null
    }

enum class DeletionValidation { ALLOWED, MISSING }

fun evaluateDeletion(tracker: TrackerData, id: String): DeletionValidation = when {
    tracker.readings.none { it.id == id } -> DeletionValidation.MISSING
    else -> DeletionValidation.ALLOWED
}
