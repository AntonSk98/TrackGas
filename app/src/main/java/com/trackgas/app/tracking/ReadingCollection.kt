package com.trackgas.app.tracking

internal enum class ReadingCollectionViolation {
    DUPLICATE_IDENTITY, DUPLICATE_DATE, DECREASING_VALUE, INVALID_INSTALLATION, OVERLAPPING_COVERAGE,
}

/** Checks relationships only; each [Reading] already enforces its intrinsic invariants. */
internal fun readingCollectionViolation(readings: List<Reading>): ReadingCollectionViolation? {
    if (readings.map { it.id }.toSet().size != readings.size) {
        return ReadingCollectionViolation.DUPLICATE_IDENTITY
    }
    for (installation in readings.groupBy { it.installationId }.values) {
        val chronological = installation.sortedBy { it.date }
        for (index in 1 until chronological.size) {
            val earlier = chronological[index - 1]
            val later = chronological[index]
            if (earlier.date == later.date) return ReadingCollectionViolation.DUPLICATE_DATE
            if (earlier.cubicMetres > later.cubicMetres) return ReadingCollectionViolation.DECREASING_VALUE
        }
    }
    return null
}

internal fun readingCollectionViolation(tracker: TrackerData): ReadingCollectionViolation? {
    readingCollectionViolation(tracker.readings)?.let { return it }
    val installations = tracker.installations.associateBy { it.id }
    if (installations.isEmpty() || installations.size != tracker.installations.size ||
        tracker.readings.any { it.installationId !in installations }
    ) return ReadingCollectionViolation.INVALID_INSTALLATION
    if (hasOverlappingReadingCoverage(tracker.readings)) return ReadingCollectionViolation.OVERLAPPING_COVERAGE
    return null
}

internal fun hasOverlappingReadingCoverage(readings: List<Reading>): Boolean {
    return overlappingReadingCoverage(readings) != null
}

internal fun overlappingReadingCoverage(readings: List<Reading>): Pair<ReadingInterval, ReadingInterval>? {
    val intervals = readings.groupBy { it.installationId }.values
        .flatMap { meterReadings ->
            meterReadings.sortedBy { it.date }.zipWithNext { earlier, later -> ReadingInterval(earlier, later) }
        }
        .sortedBy { it.firstCoveredDate }
    return intervals.zipWithNext().firstOrNull { (earlier, later) ->
        earlier.lastCoveredDate >= later.firstCoveredDate
    }?.let { it.first to it.second }
}
