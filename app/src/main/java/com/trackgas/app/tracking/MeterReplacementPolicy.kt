package com.trackgas.app.tracking

import java.math.BigDecimal
import java.time.LocalDate

data class ReplacementTarget(
    val date: LocalDate,
    val installationId: String,
    val finalReading: Reading?,
)

data class ReplaceMeterIntent(val target: ReplacementTarget, val today: LocalDate)

enum class ReplacementError { MISSING_FINAL, ALREADY_REPLACED, OBSOLETE_TARGET, STALE_DATE, NOT_LATEST }

sealed interface ReplacementValidation {
    data class Accepted(val tracker: TrackerData) : ReplacementValidation
    data class Rejected(val error: ReplacementError) : ReplacementValidation
}

fun replacementTarget(tracker: TrackerData, date: LocalDate): ReplacementTarget {
    val current = tracker.currentInstallation.id
    return ReplacementTarget(date, current, tracker.readings.singleOrNull { it.installationId == current && it.date == date })
}

/** Revalidates the displayed final value and target, never silently retargeting a stale confirmation. */
fun evaluateReplacement(
    tracker: TrackerData,
    intent: ReplaceMeterIntent,
    installationId: String,
    readingId: String,
    replacementId: String,
): ReplacementValidation {
    val target = intent.target
    replacementTargetError(tracker, target, intent.today)?.let { return ReplacementValidation.Rejected(it) }
    val initial = Reading(readingId, target.date, BigDecimal.ZERO, installationId)
    val next = tracker.copy(
        readings = tracker.readings + initial,
        installations = tracker.installations + MeterInstallation(installationId, target.date),
        replacements = tracker.replacements + MeterReplacement(
            replacementId, target.date, target.installationId, installationId, checkNotNull(target.finalReading).id, readingId,
        ),
    )
    check(readingCollectionViolation(next) == null) { "Replacement must produce a valid tracker" }
    return ReplacementValidation.Accepted(next)
}

fun replacementTargetError(tracker: TrackerData, target: ReplacementTarget, today: LocalDate): ReplacementError? =
    when {
        target.date != today -> ReplacementError.STALE_DATE
        target.installationId != tracker.currentInstallation.id -> ReplacementError.OBSOLETE_TARGET
        tracker.replacements.any { it.date == target.date } -> ReplacementError.ALREADY_REPLACED
        tracker.replacements.any { it.date > target.date } -> ReplacementError.NOT_LATEST
        target.finalReading != null && tracker.readings.firstOrNull { it.id == target.finalReading.id } != target.finalReading ->
            ReplacementError.OBSOLETE_TARGET
        tracker.readings.none { it.installationId == target.installationId && it.date == target.date } ->
            ReplacementError.MISSING_FINAL
        target.finalReading == null || target.finalReading.installationId != target.installationId ||
            target.finalReading.date != target.date -> ReplacementError.OBSOLETE_TARGET
        tracker.readings.any { it.installationId == target.installationId && it.date > target.date } ->
            ReplacementError.NOT_LATEST
        else -> null
    }

enum class DeletionValidation { ALLOWED, MISSING, PROTECTED }

fun evaluateDeletion(tracker: TrackerData, id: String): DeletionValidation = when {
    tracker.readings.none { it.id == id } -> DeletionValidation.MISSING
    tracker.isBoundaryReading(id) -> DeletionValidation.PROTECTED
    else -> DeletionValidation.ALLOWED
}
