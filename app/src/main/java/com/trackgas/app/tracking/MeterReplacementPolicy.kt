package com.trackgas.app.tracking

import java.time.LocalDate

data class ReplacementTarget(
    val date: LocalDate,
    val installationId: String,
    val expectedReadings: List<Reading>,
)

data class ReplaceMeterIntent(val target: ReplacementTarget, val today: LocalDate)

enum class ReplacementError { ALREADY_REPLACED, OBSOLETE_TARGET, STALE_DATE, NOT_LATEST }

sealed interface ReplacementValidation {
    data class Accepted(val tracker: TrackerData) : ReplacementValidation
    data class Rejected(val error: ReplacementError) : ReplacementValidation
}

fun replacementTarget(tracker: TrackerData, date: LocalDate): ReplacementTarget {
    val current = tracker.currentInstallation.id
    return ReplacementTarget(
        date,
        current,
        tracker.readings.filter { it.installationId == current }.sortedBy { it.id },
    )
}

/** Revalidates the current installation and its readings without creating boundary readings. */
fun evaluateReplacement(
    tracker: TrackerData,
    intent: ReplaceMeterIntent,
    installationId: String,
): ReplacementValidation {
    val target = intent.target
    replacementTargetError(tracker, target, intent.today)?.let { return ReplacementValidation.Rejected(it) }
    val next = tracker.copy(
        installations = tracker.installations + MeterInstallation(installationId, target.date),
    )
    check(readingCollectionViolation(next) == null) { "Replacement must produce a valid tracker" }
    return ReplacementValidation.Accepted(next)
}

fun replacementTargetError(tracker: TrackerData, target: ReplacementTarget, today: LocalDate): ReplacementError? =
    when {
        target.date != today -> ReplacementError.STALE_DATE
        target.installationId != tracker.currentInstallation.id -> ReplacementError.OBSOLETE_TARGET
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
