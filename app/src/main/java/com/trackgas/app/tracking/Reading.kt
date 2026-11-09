package com.trackgas.app.tracking

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * An actual cumulative meter observation, not a calculated daily consumption value.
 * Construction enforces intrinsic identity/value invariants; [evaluateSave] handles
 * date eligibility and chronology against other saved readings.
 *
 * @property id Stable identity preserved when the observation's date or value is edited.
 * @property date Recorded local date, retained for calendar allocation.
 * @property cubicMetres Raw cumulative meter value; decimal precision is retained as entered.
 */
data class Reading(
    val id: String,
    val date: LocalDate,
    val cubicMetres: BigDecimal,
    val installationId: String = ORIGINAL_INSTALLATION_ID,
    val note: String? = null,
    val observedAt: Instant,
    val zone: ZoneId,
) {
    val localTime get() = observedAt.atZone(zone).toLocalTime()
    init {
        require(observedAt == observedAt.truncatedTo(ChronoUnit.MINUTES))
        require(observedAt.atZone(zone).toLocalDate() == date)
        require(id.isNotBlank()) { "Reading identity must not be blank" }
        require(installationId.isNotBlank()) { "Installation identity must not be blank" }
        require(cubicMetres.signum() >= 0) { "Cumulative meter value must not be negative" }
    }
}
