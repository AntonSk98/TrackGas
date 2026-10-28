package com.trackgas.app

import com.trackgas.app.tracking.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ReadingInputTest {
    @Test fun parserConsumesCompleteTrimmedInputAndPreservesScale() {
        assertEquals(ParsedMeter.Required, parseMeter(" \t ", Locale.US))
        for (text in listOf("NaN", "Infinity", "-Infinity", "1e2", "100x", "1,000.2", "100.2.3", ".5", "1.", "+1", "1 000", "1\n2")) {
            assertEquals(text, ParsedMeter.Invalid, parseMeter(text, Locale.US))
        }
        assertEquals(ParsedMeter.Value("110.12500".toBigDecimal()), parseMeter(" \t110.12500\n", Locale.US))
        assertEquals(ParsedMeter.Value("-1.00".toBigDecimal()), parseMeter("-1.00", Locale.US))
        assertEquals(ParsedMeter.Value("110.12500".toBigDecimal()), parseMeter("110,12500", Locale.GERMANY))
        assertEquals(ParsedMeter.Value("110.12500".toBigDecimal()), parseMeter("110.12500", Locale.GERMANY))
        assertEquals(ParsedMeter.Invalid, parseMeter("110,12500", Locale.US))
        assertEquals(ParsedMeter.Value("110.12500".toBigDecimal()), parseMeter("١١٠٫١٢٥٠٠", Locale.forLanguageTag("ar-EG")))
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-EG"))) {
            val value = "110.12345678900".toBigDecimal()
            assertEquals(ParsedMeter.Value(value), parseMeter(numberText(value, locale, raw = true), locale))
        }
    }
}
