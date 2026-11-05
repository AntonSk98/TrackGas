package com.trackgas.app

import com.trackgas.app.temperature.TemperatureAggregation
import com.trackgas.app.tracking.numberText
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers period means and daily-versus-monthly temperature bucket aggregation. */
class TemperatureAggregationTest {
    private val temperatures = mapOf(
        date("2026-10-01") to BigDecimal("4"),
        date("2026-10-02") to BigDecimal("6"),
        date("2026-10-03") to BigDecimal("8"),
    )

    @Test
    fun periodAndBucketMeansIgnoreDatesWithoutTemperature() {
        assertEquals(
            BigDecimal("6"),
            TemperatureAggregation.mean(date("2026-10-01")..date("2026-10-04"), temperatures),
        )
        assertNull(TemperatureAggregation.mean(date("2026-10-04")..date("2026-10-05"), temperatures))
        assertNull(TemperatureAggregation.mean(date("2026-10-04")..date("2026-10-05"), emptyMap()))
    }

    @Test
    fun displayedMeansUseLocalizedDecimals() {
        val mean = TemperatureAggregation.mean(date("2026-10-01")..date("2026-10-02"), temperatures)
        assertEquals("5", numberText(requireNotNull(mean), Locale.US))
        assertEquals("5", numberText(requireNotNull(mean), Locale.GERMANY))
        val fractionalMean = TemperatureAggregation.mean(
            date("2026-10-01")..date("2026-10-02"),
            mapOf(
                date("2026-10-01") to BigDecimal("4"),
                date("2026-10-02") to BigDecimal("7"),
            ),
        )
        assertEquals("5.5", numberText(requireNotNull(fractionalMean), Locale.US))
        assertEquals("5,5", numberText(requireNotNull(fractionalMean), Locale.GERMANY))
    }

    private fun date(value: String) = LocalDate.parse(value)
}
