package com.trackgas.app.tracking

internal enum class ReadingCollectionViolation { DUPLICATE_IDENTITY, DUPLICATE_DATE, DECREASING_VALUE }

/** Checks relationships only; each [Reading] already enforces its intrinsic invariants. */
internal fun readingCollectionViolation(readings: List<Reading>): ReadingCollectionViolation? {
    if (readings.map { it.id }.toSet().size != readings.size) {
        return ReadingCollectionViolation.DUPLICATE_IDENTITY
    }
    val chronological = readings.sortedBy { it.date }
    for (index in 1 until chronological.size) {
        val earlier = chronological[index - 1]
        val later = chronological[index]
        if (earlier.date == later.date) return ReadingCollectionViolation.DUPLICATE_DATE
        if (earlier.cubicMetres > later.cubicMetres) return ReadingCollectionViolation.DECREASING_VALUE
    }
    return null
}
