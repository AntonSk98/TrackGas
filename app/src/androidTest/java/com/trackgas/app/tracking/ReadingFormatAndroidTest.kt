package com.trackgas.app.tracking

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingFormatAndroidTest {
    @Test
    fun rawMeterTextPreservesScale() {
        assertEquals("100.0100", numberText(BigDecimal("100.0100"), Locale.US, raw = true))
    }
}
