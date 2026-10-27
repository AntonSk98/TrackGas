package com.trackgas.app

import com.trackgas.app.tracking.PeriodType
import com.trackgas.app.tracking.bucketText
import com.trackgas.app.tracking.dateText
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFormatTest {
    @Test fun weeksUseLocalizedWeekdaysEvenAcrossYears() {
        val monday = LocalDate.parse("2026-12-28")
        val sunday = LocalDate.parse("2027-01-03")
        assertEquals("Mon", bucketText(monday, PeriodType.WEEK, Locale.US))
        assertEquals("Sun", bucketText(sunday, PeriodType.WEEK, Locale.US))
        assertEquals("Mo.", bucketText(monday, PeriodType.WEEK, Locale.GERMANY))
        assertFalse(bucketText(sunday, PeriodType.WEEK, Locale.GERMANY).contains("2027"))
    }

    @Test fun monthsUseOnlyLocalizedDayNumbers() {
        val date = LocalDate.parse("2026-10-26")
        assertEquals("26", bucketText(date, PeriodType.MONTH, Locale.US))
        assertEquals("26", bucketText(date, PeriodType.MONTH, Locale.GERMANY))
        assertEquals("\u0662\u0666", bucketText(date, PeriodType.MONTH, Locale.forLanguageTag("ar-EG-u-nu-arab")))
        assertEquals("1", bucketText(date.withDayOfMonth(1), PeriodType.MONTH, Locale.US))
        assertEquals("31", bucketText(date.withDayOfMonth(31), PeriodType.MONTH, Locale.US))
    }

    @Test fun yearsUseMonthNamesWithoutRepeatingTheYear() {
        val date = LocalDate.parse("2026-10-01")
        assertEquals("Oct", bucketText(date, PeriodType.YEAR, Locale.US))
        assertEquals("Okt", bucketText(date, PeriodType.YEAR, Locale.GERMANY))
    }

    @Test fun markersPadMonthlyDaysWithoutChangingWeekOrYearLabels() {
        val date = LocalDate.parse("2026-10-04")
        assertEquals("4", bucketText(date, PeriodType.MONTH, Locale.US))
        assertEquals("04", bucketText(date, PeriodType.MONTH, Locale.US, padDay = true))
        assertEquals("26", bucketText(date.withDayOfMonth(26), PeriodType.MONTH, Locale.GERMANY, padDay = true))
        assertEquals("Sun", bucketText(date, PeriodType.WEEK, Locale.US, padDay = true))
        assertEquals("Oct", bucketText(date, PeriodType.YEAR, Locale.US, padDay = true))
        assertEquals(
            "\u0660\u0664",
            bucketText(date, PeriodType.MONTH, Locale.forLanguageTag("ar-EG-u-nu-arab"), padDay = true),
        )
    }

    @Test fun historyAndFormDatesKeepTheirYearContext() {
        val date = LocalDate.parse("2026-10-26")
        assertEquals("Oct 26, 2026", dateText(date, Locale.US))
        assertTrue(dateText(date, Locale.GERMANY).contains("2026"))
    }
}
