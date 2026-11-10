package com.trackgas.app

import com.trackgas.app.tracking.ConsumptionChartMode
import com.trackgas.app.tracking.descriptionResource
import com.trackgas.app.tracking.inspectionResource
import com.trackgas.app.tracking.labelResource
import com.trackgas.app.tracking.titleResource
import com.trackgas.app.tracking.volumeResource
import java.io.File
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalizedStringResourcesTest {
    @Test
    fun chartModesUseDistinctLocalizedTitlesUnitsAndAccessibleDailyEstimates() {
        val english = readStrings(resourceFile("values/strings.xml"))
        val german = readStrings(resourceFile("values-de/strings.xml"))
        assertEquals("Total consumption", english.getValue("consumption_total").text)
        assertEquals("Gesamtverbrauch", german.getValue("consumption_total").text)
        assertEquals("Daily consumption", english.getValue("chart_daily_title").text)
        assertEquals("Tagesverbrauch", german.getValue("chart_daily_title").text)
        assertEquals("Consumption", english.getValue("chart_absolute_title").text)
        assertEquals("Verbrauch", german.getValue("chart_absolute_title").text)
        for (strings in listOf(english, german)) {
            for (key in listOf("chart_daily_title", "chart_absolute_title", "chart_mode_daily", "chart_mode_absolute")) {
                assertFalse(strings.getValue(key).text.contains("24"))
                assertFalse(strings.getValue(key).text.isBlank())
            }
            assertFalse(strings.getValue("chart_daily_unavailable").text.isBlank())
        }
        assertEquals("≈ 8.1 m³", String.format(Locale.US, english.getValue("chart_daily_estimate").text, "8.1"))
        assertEquals("≈ 8,1 m³", String.format(Locale.GERMANY, german.getValue("chart_daily_estimate").text, "8,1"))
        assertEquals("%1\$s m³/day", english.getValue("chart_daily_volume").text)
        assertEquals("%1\$s m³/Tag", german.getValue("chart_daily_volume").text)
        assertEquals("Estimated daily consumption: 8.1 cubic metres per day",
            String.format(Locale.US, english.getValue("chart_daily_estimate_description").text, "8.1"))
        assertEquals("Geschätzter Tagesverbrauch: 8,1 Kubikmeter pro Tag",
            String.format(Locale.GERMANY, german.getValue("chart_daily_estimate_description").text, "8,1"))
        assertEquals("≈ %1\$s m³ / 24 h", english.getValue("history_24h_estimate").text)
        assertEquals("≈ %1\$s m³ / 24 h", german.getValue("history_24h_estimate").text)

        assertEquals(R.string.chart_daily_title, ConsumptionChartMode.DAILY_ESTIMATE.titleResource)
        assertEquals(R.string.chart_mode_daily, ConsumptionChartMode.DAILY_ESTIMATE.labelResource)
        assertEquals(R.string.chart_daily_volume, ConsumptionChartMode.DAILY_ESTIMATE.volumeResource)
        assertEquals(R.string.chart_daily_estimate, ConsumptionChartMode.DAILY_ESTIMATE.inspectionResource)
        assertEquals(R.string.chart_daily_estimate_description, ConsumptionChartMode.DAILY_ESTIMATE.descriptionResource)
        assertEquals(R.string.chart_absolute_title, ConsumptionChartMode.ABSOLUTE.titleResource)
        assertEquals(R.string.chart_mode_absolute, ConsumptionChartMode.ABSOLUTE.labelResource)
        assertEquals(R.string.volume, ConsumptionChartMode.ABSOLUTE.volumeResource)
        assertEquals(R.string.volume, ConsumptionChartMode.ABSOLUTE.inspectionResource)
        assertEquals(R.string.volume, ConsumptionChartMode.ABSOLUTE.descriptionResource)
    }

    @Test
    fun englishAndGermanResourcesHaveMatchingKeysAndPlaceholders() {
        val english = readStrings(resourceFile("values/strings.xml"))
        val german = readStrings(resourceFile("values-de/strings.xml"))

        assertEquals(english.keys, german.keys)
        assertFalse(
            "German resources should use informal address",
            german.values.any { Regex("\\b(Sie|Ihnen|Ihr|Ihre|Ihrem|Ihren|Ihres)\\b").containsMatchIn(it.text) },
        )
        english.forEach { (key, value) ->
            val translated = german.getValue(key)
            assertEquals("translatable attribute for $key", value.translatable, translated.translatable)
            assertEquals(
                "format placeholders for $key",
                placeholders(value.text),
                placeholders(translated.text),
            )
        }
        assertEquals("English", english.getValue("language_english").text)
        assertEquals("Deutsch", german.getValue("language_german").text)
        assertEquals("false", english.getValue("language_english").translatable)
        assertEquals("false", english.getValue("language_german").translatable)
    }

    private fun resourceFile(relativePath: String): File {
        val paths = listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        )
        return paths.firstOrNull(File::isFile)
            ?: error("Could not find Android string resource: $relativePath")
    }

    private fun readStrings(file: File): Map<String, ResourceString> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        val strings = linkedMapOf<String, ResourceString>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            assertFalse("duplicate string resource: $name", strings.containsKey(name))
            val translatable = node.attributes.getNamedItem("translatable")?.nodeValue.orEmpty()
            strings[name] = ResourceString(node.textContent, translatable)
        }
        return strings
    }

    private fun placeholders(value: String): List<String> =
        Regex("%(?:\\d+\\$)?[sd]").findAll(value).map { it.value }.sorted().toList()

    private data class ResourceString(val text: String, val translatable: String)
}
