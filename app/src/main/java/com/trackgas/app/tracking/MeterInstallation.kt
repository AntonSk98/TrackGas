package com.trackgas.app.tracking

import java.time.LocalDate

// Placeholder for list-only calculations and unloaded UI state, never a persisted installation.
const val ORIGINAL_INSTALLATION_ID = "original"

data class MeterInstallation(val id: String, val startDate: LocalDate? = null) {
    init { require(id.isNotBlank()) { "Installation identity must not be blank" } }
}

data class MeterReplacement(
    val id: String,
    val date: LocalDate,
    val oldInstallationId: String,
    val newInstallationId: String,
    val finalReadingId: String,
    val initialReadingId: String,
) {
    init {
        require(listOf(id, oldInstallationId, newInstallationId, finalReadingId, initialReadingId).all { it.isNotBlank() }) {
            "Replacement identities must not be blank"
        }
    }
}

/** A committed tracker owns stable lists; consumers must not mutate them after delivery. */
data class TrackerData(
    val readings: List<Reading> = emptyList(),
    val installations: List<MeterInstallation> = listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID)),
    val replacements: List<MeterReplacement> = emptyList(),
) {
    val currentInstallation: MeterInstallation
        get() {
            val latestReadingDate = readings.maxOfOrNull { it.date }
            val candidates = latestReadingDate?.let { latest ->
                installations.filter { meter -> readings.any { it.installationId == meter.id && it.date == latest } }
            }?.takeIf { it.isNotEmpty() } ?: installations
            return candidates.maxWithOrNull(
                compareBy<MeterInstallation> { it.startDate != null }
                    .thenBy { it.startDate }
                    .thenBy { it.id },
            ) ?: error("Tracker must contain an installation")
        }

    fun isBoundaryReading(id: String): Boolean =
        replacements.any { it.finalReadingId == id || it.initialReadingId == id }
}
