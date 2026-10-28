package com.trackgas.app.tracking

import java.math.BigDecimal
import java.time.LocalDate

/**
 * An actual cumulative meter observation, not a calculated daily consumption value.
 * Construction enforces intrinsic identity/value invariants; [evaluateSave] handles
 * date eligibility and chronology against other saved readings.
 *
 * @property id Stable identity preserved when the observation's date or value is edited.
 * @property date Calendar date of the observation, with no time-of-day or timezone component.
 * @property cubicMetres Raw cumulative meter value; decimal precision is retained as entered.
 */
data class Reading(
    val id: String,
    val date: LocalDate,
    val cubicMetres: BigDecimal,
) {
    init {
        require(id.isNotBlank()) { "Reading identity must not be blank" }
        require(cubicMetres.signum() >= 0) { "Cumulative meter value must not be negative" }
    }
}
